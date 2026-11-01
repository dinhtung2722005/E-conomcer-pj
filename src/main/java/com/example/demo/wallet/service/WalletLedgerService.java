package com.example.demo.wallet.service;
import com.example.demo.shared.logging.LogContext;
import com.example.demo.wallet.api.WalletBalanceType;

import java.math.BigDecimal;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.example.demo.wallet.entity.*;
import com.example.demo.wallet.repository.WalletJournalRepository;
import lombok.RequiredArgsConstructor;

/** Append-only, balanced postings. All callers hold the relevant wallet locks in the same transaction. */
@Service @RequiredArgsConstructor @lombok.extern.slf4j.Slf4j
public class WalletLedgerService {
    private final WalletJournalRepository journals;

    public String account(Wallet wallet, WalletBalanceType type, boolean held) {
        return "wallet:" + wallet.getId() + ":" + type + (held ? ":HELD" : ":AVAILABLE");
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void open(Wallet wallet) {
        if (wallet.isLedgerOpened()) return;
        WalletJournal journal = WalletJournal.builder().eventKey("opening:" + wallet.getId()).currency(wallet.getCurrency()).build();
        opening(journal, wallet, WalletBalanceType.CASH, false, wallet.getBalance());
        opening(journal, wallet, WalletBalanceType.CASH, true, wallet.getPendingBalance());
        opening(journal, wallet, WalletBalanceType.BONUS, false, wallet.getBonusBalance());
        opening(journal, wallet, WalletBalanceType.BONUS, true, wallet.getPendingBonusBalance());
        if (!journal.getEntries().isEmpty()) {
            journals.saveAndFlush(journal);
            logAfterCommit(journal);
        }
        wallet.setLedgerOpened(true);
    }

    private void opening(WalletJournal journal, Wallet wallet, WalletBalanceType type, boolean held, BigDecimal amount) {
        if (amount.signum() == 0) return;
        journal.add(account(wallet, type, held), amount);
        journal.add("platform:OPENING", amount.negate());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void move(String eventKey, String currency, String from, String to, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0 || from.equals(to)) throw new IllegalArgumentException("Invalid ledger movement");
        post(eventKey, currency, Map.of(from, amount.negate(), to, amount));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void post(String eventKey, String currency, Map<String, BigDecimal> entries) {
        if (entries == null || entries.values().stream().anyMatch(amount -> amount == null
                || amount.stripTrailingZeros().scale() > 4 || amount.abs().compareTo(new BigDecimal("1000000000000000")) >= 0)) {
            throw new IllegalArgumentException("Ledger entries must fit DECIMAL(19,4) without rounding");
        }
        if (entries.size() < 2 || entries.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add).signum() != 0) {
            throw new IllegalArgumentException("Unbalanced wallet journal");
        }
        WalletJournal journal = WalletJournal.builder().eventKey(eventKey).currency(currency).build();
        entries.forEach((account, amount) -> {
            if (amount.signum() != 0) journal.add(account, amount);
        });
        journals.saveAndFlush(journal);
        logAfterCommit(journal);
    }

    private void logAfterCommit(WalletJournal journal) {
        Map<String,String> context=new java.util.HashMap<>();
        var inherited=org.slf4j.MDC.getCopyOfContextMap();
        if(inherited!=null)context.putAll(inherited);
        context.put("journalId",journal.getId().toString());
        context.put("eventKey",journal.getEventKey());
        String[] parts=journal.getEventKey().split(":");
        if(parts.length==2&&parts[1].matches("[0-9]+")){
            if(java.util.Set.of("hold","refund","reserve","bank","withdraw-cancel").contains(parts[0]))context.put("transactionId",parts[1]);
            if(java.util.Set.of("settlement","cashback","bonus-debt").contains(parts[0]))context.put("orderId",parts[1]);
        }
        String walletIds=journal.getEntries().stream().map(WalletJournalEntry::getAccount)
                .filter(a->a.startsWith("wallet:")).map(a->a.split(":")[1]).distinct().sorted()
                .collect(java.util.stream.Collectors.joining(","));
        String currency=journal.getCurrency();
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
            new org.springframework.transaction.support.TransactionSynchronization(){
                @Override public void afterCommit(){
                    try(var scope=new com.example.demo.shared.logging.LogContext(context)){
                        log.atInfo().addKeyValue("event","WALLET_JOURNAL_COMMITTED")
                                .addKeyValue("walletIds",walletIds).addKeyValue("currency",currency)
                                .log("Wallet journal committed");
                    }
                }
            });
    }
}
