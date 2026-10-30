package com.example.demo.wallet.entity;

import java.math.BigDecimal;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.Immutable;
import jakarta.persistence.*;
import lombok.*;

@Entity @Immutable
@Table(name = "wallet_journal_entries", indexes = @Index(name = "idx_journal_account", columnList = "account,id"))
@Check(name = "ck_journal_nonzero", constraints = "amount <> 0")
@Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class WalletJournalEntry {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "journal_id", nullable = false, updatable = false) private WalletJournal journal;
    @Column(nullable = false, length = 100, updatable = false) private String account;
    /** Signed liability movement; the sum of each journal is zero. */
    @jakarta.validation.constraints.NotNull
    @jakarta.validation.constraints.Digits(integer = 15, fraction = 4)
    @Column(nullable = false, precision = 19, scale = 4, updatable = false) private BigDecimal amount;
}
