package com.example.demo.wallet.service;

import java.util.List;
import java.util.UUID;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import com.example.demo.user.exception.*;
import com.example.demo.user.service.CurrentUserService;
import com.example.demo.wallet.dto.*;
import com.example.demo.wallet.entity.*;
import com.example.demo.wallet.gateway.BankGateway;
import com.example.demo.wallet.repository.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

@Service @Validated @RequiredArgsConstructor
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class WalletFundingService {
    private final WalletRepository wallets;
    private final WalletTransactionRepository transactions;
    private final WalletHoldRepository holds;
    private final LinkedBankAccountRepository accounts;
    private final BankTransferRepository transfers;
    private final WalletOperationService operations;
    private final WalletPinService pins;
    private final CurrentUserService currentUser;
    private final ObjectProvider<BankGateway> gateways;
    private final PlatformTransactionManager transactionManager;
    private final ObjectMapper json;
    private final WalletLedgerService ledger;
    private final WalletRecoveryDebtRepository debts;

    public BankAccountSummaryResponse linkAccount(@NotNull @Valid LinkBankAccountRequest request) {
        Long userId = activeUserId();
        BankGateway gateway = gateway();
        // Network ownership verification takes place outside the database transaction.
        BankGateway.VerifiedAccount verified = gateway.verifyOwnership(userId, request.getVerificationToken());
        validateVerifiedAccount(verified);
        return new TransactionTemplate(transactionManager).execute(status -> {
            Wallet wallet = lockWallet(userId);
            var existing = accounts.findByProviderAndAccountToken(gateway.provider(), verified.accountToken());
            if (existing.isPresent()) {
                if(existing.get().getRevokedAt()!=null)throw new AppException(ErrorCode.WALLET_BANK_ACCOUNT_REVOKED);
                if (!existing.get().getWallet().getId().equals(wallet.getId())) {
                    throw new AppException(ErrorCode.ACCESS_DENIED);
                }
                return bankSummary(existing.get());
            }
            return bankSummary(accounts.saveAndFlush(LinkedBankAccount.builder().wallet(wallet)
                    .provider(gateway.provider()).accountToken(verified.accountToken()).bankCode(verified.bankCode())
                    .bankName(verified.bankName()).maskedAccountNumber(verified.maskedAccountNumber())
                    .accountHolderName(verified.accountHolderName()).build()));
        });
    }

    public List<BankAccountSummaryResponse> getMyBankAccounts() {
        Long userId = activeUserId();
        return new TransactionTemplate(transactionManager).execute(status -> {
            Wallet wallet = wallets.findByUserId(userId)
                    .orElseThrow(() -> new AppException(ErrorCode.WALLET_NOT_FOUND));
            return accounts.findByWalletIdOrderByIdAsc(wallet.getId()).stream().map(this::bankSummary).toList();
        });
    }

    public TopUpResponse topUp(@NotNull @Valid TopUpRequest request, String key) {
        Long userId = activeUserId();
        BankGateway gateway = gateway();
        String fingerprint = fingerprint(TransactionType.DEPOSIT, request.getAmount(), request.getLinkedBankAccountId());
        return new TransactionTemplate(transactionManager).execute(status -> {
            Wallet wallet = lockWallet(userId);
            var existing = operations.find(wallet, key, fingerprint);
            BankTransfer transfer = existing.isPresent()
                    ? transfers.findByTransactionOperationId(existing.get().getId()).orElseThrow()
                    : createTransfer(wallet, request.getLinkedBankAccountId(), request.getAmount(), key, fingerprint,
                            TransactionType.DEPOSIT, gateway.provider());
            return topUpResponse(transfer);
        });
    }

    public WithdrawResponse withdraw(@NotNull @Valid WithdrawRequest request, String key) {
        Long userId = activeUserId();
        BankGateway gateway = gateway();
        String fingerprint = fingerprint(TransactionType.WITHDRAW, request.getAmount(), request.getLinkedBankAccountId());
        var authorization = pins.authorize(request.getPin(), fingerprint);
        return new TransactionTemplate(transactionManager).execute(status -> {
            pins.lockAuthorizingUser(authorization);
            Wallet wallet = lockWallet(userId);
            pins.requireAuthorization(authorization, wallet, fingerprint);
            pins.requireWithdrawalAllowed(wallet);
            var existing = operations.find(wallet, key, fingerprint);
            if (existing.isPresent()) {
                return withdrawResponse(transfers.findByTransactionOperationId(existing.get().getId()).orElseThrow());
            }
            if (wallet.getStatus() != WalletStatus.ACTIVE) throw new AppException(ErrorCode.WALLET_LOCKED);
            if(debts.total(wallet.getUserId(),WalletBalanceType.CASH,wallet.getCurrency()).signum()>0)throw new AppException(ErrorCode.WALLET_DEBT_OUTSTANDING);
            if (wallet.getBalance().compareTo(request.getAmount()) < 0) {
                throw new AppException(ErrorCode.WALLET_INSUFFICIENT_BALANCE);
            }
            return withdrawResponse(createTransfer(wallet, request.getLinkedBankAccountId(), request.getAmount(), key,
                    fingerprint, TransactionType.WITHDRAW, gateway.provider()));
        });
    }

    public TopUpResponse getTopUp(Long transactionId) {
        return new TransactionTemplate(transactionManager).execute(status ->
                topUpResponse(findMyTransfer(transactionId, TransactionType.DEPOSIT)));
    }

    public WithdrawResponse getWithdrawal(Long transactionId) {
        return new TransactionTemplate(transactionManager).execute(status ->
                withdrawResponse(findMyTransfer(transactionId, TransactionType.WITHDRAW)));
    }

    private BankTransfer findMyTransfer(Long transactionId, TransactionType type) {
        Long userId = activeUserId();
        return transfers.findByTransactionId(transactionId)
                .filter(value -> value.getTransaction().getWallet().getUserId().equals(userId)
                        && value.getTransaction().getType() == type)
                .orElseThrow(() -> new AppException(ErrorCode.WALLET_TRANSACTION_NOT_FOUND));
    }

    private BankTransfer createTransfer(Wallet wallet, Long accountId, BigDecimal amount, String key,
            String fingerprint, TransactionType type, String provider) {
        LinkedBankAccount account = accounts.findByIdAndWalletId(accountId, wallet.getId())
                .filter(value->value.getRevokedAt()==null)
                .filter(value -> value.getProvider().equals(provider))
                .orElseThrow(() -> new AppException(ErrorCode.WALLET_BANK_ACCOUNT_NOT_FOUND));
        String reference = UUID.randomUUID().toString();
        ledger.open(wallet);
        var operation = operations.create(wallet, key, fingerprint, type, reference);
        WalletHold hold = null;
        if (type == TransactionType.WITHDRAW) {
            hold = holds.saveAndFlush(WalletHold.builder().wallet(wallet).type(WalletHoldType.WITHDRAWAL)
                    .referenceId(reference).amount(amount).build());
            wallet.setBalance(wallet.getBalance().subtract(amount));
            wallet.setPendingBalance(wallet.getPendingBalance().add(amount));
        }
        var transaction = transactions.saveAndFlush(WalletTransaction.builder().wallet(wallet).operation(operation)
                .referenceId(reference).idempotencyKey("op:" + operation.getId() + ":CASH")
                .type(type).amount(amount).hold(hold)
                .destinationAccountReference(type == TransactionType.WITHDRAW ? account.getAccountToken() : null).build());
        if (type == TransactionType.WITHDRAW) {
            ledger.move("hold:" + transaction.getId(), wallet.getCurrency(),
                    ledger.account(wallet, WalletBalanceType.CASH, false), ledger.account(wallet, WalletBalanceType.CASH, true), amount);
        }
        return transfers.saveAndFlush(BankTransfer.builder().transaction(transaction).bankAccount(account)
                .merchantReference(reference).nextAttemptAt(LocalDateTime.now()).build());
    }

    private Long activeUserId() {
        var user = currentUser.getCurrentUser();
        if (!user.isActive()) throw new AppException(ErrorCode.USER_LOCKED);
        return user.getId();
    }

    private Wallet lockWallet(Long userId) {
        return wallets.findByUserIdForUpdate(userId).orElseThrow(() -> new AppException(ErrorCode.WALLET_NOT_FOUND));
    }

    private BankGateway gateway() {
        BankGateway gateway = gateways.getIfAvailable();
        if (gateway == null) throw new AppException(ErrorCode.WALLET_BANK_UNAVAILABLE);
        return gateway;
    }

    private String fingerprint(TransactionType type, BigDecimal amount, Long accountId) {
        return operations.fingerprint(type + "|" + amount.stripTrailingZeros().toPlainString() + "|" + accountId);
    }

    private void validateVerifiedAccount(BankGateway.VerifiedAccount value) {
        if (value == null || value.accountToken() == null || value.accountToken().isBlank()
                || value.accountToken().length() > 150 || value.bankCode() == null || value.bankCode().isBlank()
                || value.bankName() == null || value.bankName().isBlank() || value.accountHolderName() == null
                || value.accountHolderName().isBlank() || value.maskedAccountNumber() == null
                || !value.maskedAccountNumber().contains("*")) {
            throw new AppException(ErrorCode.WALLET_BANK_UNAVAILABLE);
        }
    }

    private BankAccountSummaryResponse bankSummary(LinkedBankAccount account) {
        var result = new BankAccountSummaryResponse();
        result.setActive(account.getRevokedAt()==null);
        result.setLinkedBankAccountId(account.getId());
        result.setBankCode(account.getBankCode());
        result.setBankName(account.getBankName());
        result.setMaskedAccountNumber(account.getMaskedAccountNumber());
        result.setAccountHolderName(account.getAccountHolderName());
        return result;
    }

    private TopUpResponse topUpResponse(BankTransfer transfer) {
        var transaction = transfer.getTransaction();
        var result = new TopUpResponse();
        result.setTransactionId(transaction.getId());
        result.setAmount(transaction.getAmount());
        result.setCurrency(transaction.getWallet().getCurrency());
        result.setStatus(transaction.getStatus());
        result.setCreatedAt(transaction.getCreatedAt());
        result.setCompletedAt(transaction.getCompletedAt());
        result.setExpiresAt(transfer.getExpiresAt());
        if (transfer.getInstructionsJson() != null) {
            result.setInstructions(json.readValue(transfer.getInstructionsJson(), TopUpInstructionsResponse.class));
        }
        return result;
    }

    private WithdrawResponse withdrawResponse(BankTransfer transfer) {
        var transaction = transfer.getTransaction();
        var result = new WithdrawResponse();
        result.setTransactionId(transaction.getId());
        result.setHoldId(transaction.getHold().getId());
        result.setAmount(transaction.getAmount());
        result.setCurrency(transaction.getWallet().getCurrency());
        result.setStatus(transaction.getStatus());
        result.setBankAccount(bankSummary(transfer.getBankAccount()));
        result.setCreatedAt(transaction.getCreatedAt());
        result.setCompletedAt(transaction.getCompletedAt());
        return result;
    }
}
