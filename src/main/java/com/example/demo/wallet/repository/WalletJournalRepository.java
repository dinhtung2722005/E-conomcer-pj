package com.example.demo.wallet.repository;

import java.math.BigDecimal;
import org.springframework.data.jpa.repository.*;
import com.example.demo.wallet.entity.WalletJournal;

public interface WalletJournalRepository extends JpaRepository<WalletJournal, Long> {
    @Query("select coalesce(sum(e.amount), 0) from WalletJournalEntry e where e.account = :account and e.journal.currency = :currency")
    BigDecimal accountBalance(String account, String currency);
    @Query("select count(j) from WalletJournal j where (select coalesce(sum(e.amount), 0) from WalletJournalEntry e where e.journal = j) <> 0")
    long countUnbalancedJournals();
}
