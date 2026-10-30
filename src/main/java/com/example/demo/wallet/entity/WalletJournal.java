package com.example.demo.wallet.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;
import jakarta.persistence.*;
import lombok.*;

@Entity @Immutable
@Table(name = "wallet_journals")
@Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class WalletJournal {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 150, updatable = false) private String eventKey;
    @Column(nullable = false, length = 3, updatable = false) private String currency;
    @CreationTimestamp @Column(nullable = false, updatable = false) private LocalDateTime createdAt;
    @OneToMany(mappedBy = "journal", cascade = CascadeType.PERSIST)
    @Builder.Default private List<WalletJournalEntry> entries = new ArrayList<>();

    public void add(String account, java.math.BigDecimal amount) {
        entries.add(WalletJournalEntry.builder().journal(this).account(account).amount(amount).build());
    }
}
