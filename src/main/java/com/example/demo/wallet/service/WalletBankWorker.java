package com.example.demo.wallet.service;

import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import com.example.demo.wallet.entity.*;
import com.example.demo.wallet.gateway.BankGateway;
import com.example.demo.wallet.repository.*;
import tools.jackson.databind.ObjectMapper;

/** Pull reconciliation uses keyset IDs. Unknown outcomes retain holds and retry with the same reference. */
@Service
@lombok.extern.slf4j.Slf4j
public class WalletBankWorker {
    private final BankTransferRepository transfers;
    private final WalletRepository wallets;
    private final WalletTransactionRepository transactions;
    private final WalletHoldRepository holds;
    private final ObjectProvider<BankGateway> gateways;
    private final ObjectMapper json;
    private final TransactionTemplate database;
    private final boolean enabled;
    private final WalletLedgerService ledger;

    public WalletBankWorker(BankTransferRepository transfers, WalletRepository wallets,
            WalletTransactionRepository transactions, WalletHoldRepository holds, ObjectProvider<BankGateway> gateways,
            ObjectMapper json, PlatformTransactionManager transactionManager,
            WalletLedgerService ledger,
            @Value("${wallet.bank.worker-enabled:true}") boolean enabled) {
        this.transfers = transfers;
        this.wallets = wallets;
        this.transactions = transactions;
        this.holds = holds;
        this.gateways = gateways;
        this.json = json;
        this.enabled = enabled;
        this.ledger = ledger;
        this.database = new TransactionTemplate(transactionManager);
        this.database.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Scheduled(fixedDelayString = "${wallet.bank.poll-delay-ms:30000}",
            initialDelayString = "${wallet.bank.initial-delay-ms:30000}")
    public void reconcile() {
        if (!enabled || gateways.getIfAvailable() == null) return;
        Long cursor = 0L;
        // Bound each sweep; IDs missed by a competing lease are revisited in the next sweep.
        for (int batch = 0; batch < 10; batch++) {
            var ids = transfers.findDueIds(LocalDateTime.now(), cursor, PageRequest.of(0, 100));
            if (ids.isEmpty()) return;
            for (Long id : ids) {
                try {
                    process(id);
                } catch (RuntimeException exception) {
                    // A DB outage cannot turn an unknown bank outcome into failure; lease expiry retries it.
                    log.warn("Bank transfer {} requires reconciliation ({})", id, exception.getClass().getSimpleName());
                }
                cursor = id;
            }
        }
    }

    /** Internal worker entry point; intentionally has no HTTP endpoint. */
    public void process(Long transferId) {
        BankGateway gateway = gateways.getIfAvailable();
        if (gateway == null) return;
        Claim claim = database.execute(status -> claim(transferId, gateway.provider()));
        if (claim == null) return;
        BankGateway.Result result;
        try {
            result = gateway.resolve(claim.command());
        } catch (RuntimeException exception) {
            database.executeWithoutResult(status -> recordUnknown(transferId, claim.token(), "GATEWAY_UNKNOWN"));
            return;
        }
        database.executeWithoutResult(status -> apply(transferId, claim, result));
    }

    private Claim claim(Long id, String provider) {
        BankTransfer transfer = transfers.findByIdForUpdate(id).orElse(null);
        LocalDateTime now = LocalDateTime.now();
        if (transfer == null || transfer.isFinished() || transfer.getNextAttemptAt().isAfter(now)
                || transfer.getLeaseUntil() != null && transfer.getLeaseUntil().isAfter(now)
                || !transfer.getBankAccount().getProvider().equals(provider)) return null;
        String token = UUID.randomUUID().toString();
        transfer.setLeaseToken(token);
        transfer.setLeaseUntil(now.plusMinutes(2));
        transfer.setAttempts(Math.min(transfer.getAttempts() + 1, 1000000));
        var transaction = transfer.getTransaction();
        return new Claim(token, new BankGateway.Command(transfer.getMerchantReference(), transaction.getType(),
                transaction.getAmount(), transaction.getWallet().getCurrency(), transfer.getBankAccount().getAccountToken()));
    }

    private void apply(Long id, Claim claim, BankGateway.Result result) {
        var route = transfers.findRoute(id).orElseThrow();
        Wallet wallet = wallets.findByIdForUpdate(route.getWalletId()).orElseThrow();
        WalletTransaction transaction = transactions.findByIdAndWalletIdForUpdate(route.getTransactionId(), wallet.getId()).orElseThrow();
        WalletHold hold = route.getHoldId() == null ? null
                : holds.findByIdAndWalletIdForUpdate(route.getHoldId(), wallet.getId()).orElseThrow();
        BankTransfer transfer = transfers.findByIdForUpdate(id).orElseThrow();
        if (transfer.isFinished() || !claim.token().equals(transfer.getLeaseToken())) return;
        transaction.setStatus(TransactionStatus.PROCESSING);
        if (!matches(claim.command(), result)) {
            recordUnknown(transfer, "INVALID_BANK_RESULT");
            transaction.setStatus(TransactionStatus.PROCESSING);
            return;
        }
        if (result.state() == BankGateway.State.PROCESSING) {
            transaction.setStatus(TransactionStatus.PROCESSING);
            if (result.instructions() != null && transaction.getType() == TransactionType.DEPOSIT) {
                transfer.setInstructionsJson(json.writeValueAsString(result.instructions()));
                transfer.setExpiresAt(result.expiresAt());
            }
            recordUnknown(transfer, null);
            return;
        }
        if (result.providerTransactionId() == null || result.providerTransactionId().isBlank()
                || result.providerTransactionId().length() > 150) {
            recordUnknown(transfer, "MISSING_BANK_REFERENCE");
            return;
        }
        // The global provider reference unique constraint also prevents crediting another request twice.
        transaction.setProvider(transfer.getBankAccount().getProvider());
        transaction.setProviderTransactionId(result.providerTransactionId());
        boolean success = result.state() == BankGateway.State.SUCCEEDED;
        ledger.open(wallet);
        if (transaction.getType() == TransactionType.DEPOSIT) {
            if (success) {
                wallet.setBalance(wallet.getBalance().add(transaction.getAmount()));
                ledger.move("bank:" + transaction.getId(), wallet.getCurrency(), "platform:BANK_CLEARING",
                        ledger.account(wallet, WalletBalanceType.CASH, false), transaction.getAmount());
            }
        } else {
            if (hold == null || hold.getRemainingAmount().compareTo(transaction.getAmount()) != 0) {
                throw new IllegalStateException("Withdrawal hold is inconsistent");
            }
            wallet.setPendingBalance(wallet.getPendingBalance().subtract(transaction.getAmount()));
            if (success) hold.capture(transaction.getAmount());
            else {
                hold.release(transaction.getAmount());
                wallet.setBalance(wallet.getBalance().add(transaction.getAmount()));
            }
            ledger.move("bank:" + transaction.getId(), wallet.getCurrency(), ledger.account(wallet, WalletBalanceType.CASH, true),
                    success ? "platform:BANK_CLEARING" : ledger.account(wallet, WalletBalanceType.CASH, false), transaction.getAmount());
        }
        transaction.setStatus(success ? TransactionStatus.SUCCESS : TransactionStatus.FAILED);
        transaction.setCompletedAt(LocalDateTime.now());
        transaction.setFailureReason(success ? null : "BANK_REJECTED");
        transfer.setFinished(true);
        transfer.setLastError(null);
        transfer.setLeaseToken(null);
        transfer.setLeaseUntil(null);
        transactions.flush();
    }

    private boolean matches(BankGateway.Command command, BankGateway.Result result) {
        return result != null && result.state() != null
                && command.merchantReference().equals(result.merchantReference())
                && result.amount() != null && command.amount().compareTo(result.amount()) == 0
                && command.currency().equals(result.currency()) && command.accountToken().equals(result.accountToken());
    }

    private void recordUnknown(Long id, String token, String error) {
        var route = transfers.findRoute(id).orElseThrow();
        wallets.findByIdForUpdate(route.getWalletId()).orElseThrow();
        var transaction = transactions.findByIdAndWalletIdForUpdate(route.getTransactionId(), route.getWalletId()).orElseThrow();
        var transfer = transfers.findByIdForUpdate(id).orElseThrow();
        if (!transfer.isFinished() && token.equals(transfer.getLeaseToken())) {
            transaction.setStatus(TransactionStatus.PROCESSING);
            recordUnknown(transfer, error);
        }
    }

    private void recordUnknown(BankTransfer transfer, String error) {
        transfer.setLastError(error);
        long seconds = Math.min(3600, 10L << Math.min(transfer.getAttempts(), 8));
        transfer.setNextAttemptAt(LocalDateTime.now().plusSeconds(seconds));
        transfer.setLeaseToken(null);
        transfer.setLeaseUntil(null);
    }

    private record Claim(String token, BankGateway.Command command) {}
}
