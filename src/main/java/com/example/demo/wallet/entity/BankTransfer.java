package com.example.demo.wallet.entity;

import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;
import jakarta.persistence.*;
import lombok.*;

/** Durable outbox and reconciliation state. A lease is committed before contacting the bank. */
@Entity
@Table(name = "wallet_bank_transfers", indexes = @Index(name = "idx_bank_transfer_due", columnList = "finished,next_attempt_at,id"))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class BankTransfer {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false, unique = true, updatable = false)
    private WalletTransaction transaction;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bank_account_id", nullable = false, updatable = false)
    private LinkedBankAccount bankAccount;
    @Column(nullable = false, length = 100, unique = true, updatable = false)
    private String merchantReference;
    @Column(nullable = false)
    private boolean finished;
    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;
    private LocalDateTime leaseUntil;
    /** Set durably before the first external dispatch; absence plus attempts=0 permits safe cancellation. */
    private LocalDateTime dispatchStartedAt;
    @Column(nullable = false) @org.hibernate.annotations.ColumnDefault("0")
    private boolean suspended;
    @Column(length = 36)
    private String leaseToken;
    @Column(nullable = false)
    private int attempts;
    /** Non-sensitive diagnostic code only; bank exception text is never persisted or returned. */
    @Column(length = 50)
    private String lastError;
    @Column(columnDefinition = "text")
    private String instructionsJson;
    private LocalDateTime expiresAt;
    @Version private Long version;
    @CreationTimestamp @Column(updatable = false)
    private LocalDateTime createdAt;
}
