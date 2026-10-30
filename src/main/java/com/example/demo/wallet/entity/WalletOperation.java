package com.example.demo.wallet.entity;

import jakarta.persistence.*;
import lombok.*;

/** One client request, including payments split across CASH and BONUS. Contains no PIN. */
@Entity
@Table(name = "wallet_operations", uniqueConstraints = @UniqueConstraint(
        name = "uk_wallet_operation_key", columnNames = {"wallet_id", "request_key"}))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class WalletOperation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wallet_id", nullable = false, updatable = false)
    private Wallet wallet;
    @Column(name = "request_key", nullable = false, length = 80, updatable = false)
    private String requestKey;
    @Column(nullable = false, length = 64, updatable = false)
    private String fingerprint;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private TransactionType type;
    @Column(nullable = false, length = 100, updatable = false)
    private String reference;
}
