package com.example.demo.wallet.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.UpdateTimestamp;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "wallet_transactions", uniqueConstraints = {
        @UniqueConstraint(name = "uk_wallet_transaction_idempotency", columnNames = {"wallet_id", "idempotency_key"}),
        @UniqueConstraint(name = "uk_wallet_transaction_provider", columnNames = {"provider", "provider_transaction_id"})
}, indexes = {
        @Index(name = "idx_wallet_transaction_history", columnList = "wallet_id, created_at"),
        @Index(name = "idx_wallet_transaction_reference", columnList = "reference_id, transaction_type"),
        @Index(name = "idx_wallet_transaction_pending", columnList = "status, created_at")
})
@Check(name = "ck_wallet_transaction_amount", constraints = "amount > 0")
@Check(name = "ck_wallet_transaction_refund", constraints =
        "transaction_type NOT IN ('REFUND', 'CASHBACK_REVERSAL') OR original_transaction_id IS NOT NULL")
@Check(name = "ck_wallet_transaction_provider_pair", constraints =
        "(provider IS NULL AND provider_transaction_id IS NULL) OR "
        + "(provider IS NOT NULL AND provider_transaction_id IS NOT NULL)")
@Check(name = "ck_wallet_transaction_cash_only", constraints =
        "transaction_type NOT IN ('DEPOSIT', 'WITHDRAW', 'SETTLEMENT') OR balance_type = 'CASH'")
@Check(name = "ck_wallet_transaction_bonus_only", constraints =
        "transaction_type NOT IN ('CASHBACK', 'CASHBACK_REVERSAL') OR balance_type = 'BONUS'")
@Check(name = "ck_wallet_transaction_hold", constraints =
        "transaction_type NOT IN ('PAYMENT', 'WITHDRAW') OR hold_id IS NOT NULL")
@Check(name = "ck_wallet_transaction_destination", constraints =
        "transaction_type <> 'WITHDRAW' OR destination_account_reference IS NOT NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WalletTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id", nullable = false, updatable = false)
    @NotNull
    private Wallet wallet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "operation_id", updatable = false)
    @JsonIgnore
    private WalletOperation operation;

    /** Business reference (order/top-up/withdrawal). May be shared by different operations. */
    @Column(name = "reference_id", nullable = false, length = 100, updatable = false)
    @NotBlank
    @Size(max = 100)
    private String referenceId;

    /** Stable for retries. A mixed CASH/BONUS payment uses one key per balance type. */
    @Column(name = "idempotency_key", nullable = false, length = 100, updatable = false)
    @NotBlank
    @Size(max = 100)
    private String idempotencyKey;

    @Column(name = "transaction_type", nullable = false, length = 20, updatable = false)
    @Enumerated(EnumType.STRING)
    @NotNull
    private TransactionType type;

    @Column(name = "balance_type", nullable = false, length = 20, updatable = false)
    @Enumerated(EnumType.STRING)
    @ColumnDefault("'CASH'")
    @NotNull
    @Builder.Default
    private WalletBalanceType balanceType = WalletBalanceType.CASH;

    @Column(nullable = false, precision = 19, scale = 4, updatable = false)
    @NotNull
    @DecimalMin(value = "0", inclusive = false)
    @Digits(integer = 15, fraction = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @ColumnDefault("'PENDING'")
    @NotNull
    @Builder.Default
    private TransactionStatus status = TransactionStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hold_id", updatable = false)
    @JsonIgnore
    private WalletHold hold;

    /** Refunds/reversals point to their original operation; never overwrite that operation. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_transaction_id", updatable = false)
    @JsonIgnore
    private WalletTransaction originalTransaction;

    @Column(length = 50)
    @Size(max = 50)
    private String provider;

    /** Identifier of this operation at the provider, e.g. refund ID rather than charge ID. */
    @Column(name = "provider_transaction_id", length = 150)
    @Size(max = 150)
    private String providerTransactionId;

    /** Token/reference to a verified bank destination; do not store raw card/PIN data. */
    @Column(name = "destination_account_reference", length = 150, updatable = false)
    @Size(max = 150)
    @JsonIgnore
    private String destinationAccountReference;

    @Column(name = "failure_reason", length = 255)
    @Size(max = 255)
    private String failureReason;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Version
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @AssertTrue(message = "Provider and provider transaction ID must be supplied together and must not be blank")
    @JsonIgnore
    public boolean isProviderReferenceValid() {
        return provider == null && providerTransactionId == null
                || provider != null && !provider.isBlank()
                && providerTransactionId != null && !providerTransactionId.isBlank();
    }

    @AssertTrue(message = "Deposits, withdrawals and seller settlements must use CASH")
    @JsonIgnore
    public boolean isCashOperationValid() {
        return (type != TransactionType.DEPOSIT && type != TransactionType.WITHDRAW
                && type != TransactionType.SETTLEMENT) || balanceType == WalletBalanceType.CASH;
    }

    @AssertTrue(message = "Cashback operations must use BONUS")
    @JsonIgnore
    public boolean isBonusOperationValid() {
        return (type != TransactionType.CASHBACK && type != TransactionType.CASHBACK_REVERSAL)
                || balanceType == WalletBalanceType.BONUS;
    }

    @AssertTrue(message = "Payment/withdrawal requires a hold; withdrawal also requires a bank destination reference")
    @JsonIgnore
    public boolean isHoldRequiredValid() {
        if (type == TransactionType.PAYMENT) {
            return hold != null && hold.getType() == WalletHoldType.ORDER_PAYMENT;
        }
        if (type == TransactionType.WITHDRAW) {
            return hold != null && hold.getType() == WalletHoldType.WITHDRAWAL
                    && destinationAccountReference != null && !destinationAccountReference.isBlank();
        }
        return true;
    }

    @AssertTrue(message = "Linked hold must belong to the same wallet, reference and source of funds")
    @JsonIgnore
    public boolean isHoldLinkValid() {
        return hold == null || sameWallet(wallet, hold.getWallet())
                && balanceType == hold.getBalanceType()
                && referenceId != null && referenceId.equals(hold.getReferenceId())
                && (amount == null || hold.getAmount() == null || amount.compareTo(hold.getAmount()) <= 0);
    }

    @AssertTrue(message = "Refund/reversal must match a successful original operation and not exceed its amount")
    @JsonIgnore
    public boolean isOriginalTransactionValid() {
        if (type != TransactionType.REFUND && type != TransactionType.CASHBACK_REVERSAL) {
            return true;
        }
        if (originalTransaction == null) {
            return false;
        }
        TransactionType expectedType = type == TransactionType.REFUND ? TransactionType.PAYMENT : TransactionType.CASHBACK;
        return originalTransaction != this && originalTransaction.getType() == expectedType
                && originalTransaction.getStatus() == TransactionStatus.SUCCESS
                && sameWallet(wallet, originalTransaction.getWallet())
                && balanceType == originalTransaction.getBalanceType()
                && referenceId != null && referenceId.equals(originalTransaction.getReferenceId())
                && (amount == null || originalTransaction.getAmount() == null
                    || amount.compareTo(originalTransaction.getAmount()) <= 0);
    }

    private static boolean sameWallet(Wallet first, Wallet second) {
        return first != null && second != null && (first == second
                || first.getId() != null && first.getId().equals(second.getId()));
    }
}
