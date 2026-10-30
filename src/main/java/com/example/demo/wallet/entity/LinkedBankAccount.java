package com.example.demo.wallet.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

/** Only created from an ownership verification result supplied by the bank adapter. */
@Entity
@Table(name = "wallet_bank_accounts", uniqueConstraints = @UniqueConstraint(
        name = "uk_wallet_bank_token", columnNames = {"provider", "account_token"}))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class LinkedBankAccount {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wallet_id", nullable = false, updatable = false)
    @JsonIgnore
    private Wallet wallet;
    @Column(nullable = false, length = 50, updatable = false)
    private String provider;
    @Column(name = "account_token", nullable = false, length = 150, updatable = false)
    @JsonIgnore
    private String accountToken;
    @Column(nullable = false, length = 50, updatable = false)
    private String bankCode;
    @Column(nullable = false, length = 150, updatable = false)
    private String bankName;
    @Column(nullable = false, length = 50, updatable = false)
    private String maskedAccountNumber;
    @Column(nullable = false, length = 150, updatable = false)
    private String accountHolderName;
}
