package com.example.demo.wallet.service;

import java.math.BigDecimal;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.example.demo.wallet.entity.*;
import com.example.demo.wallet.repository.WalletJournalRepository;
import lombok.RequiredArgsConstructor;

/** Append-only, balanced postings. All callers hold the relevant wallet locks in the same transaction. */
@Service @RequiredArgsConstructor
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
        if (!journal.getEntries().isEmpty()) journals.saveAndFlush(journal);
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
    }
}
