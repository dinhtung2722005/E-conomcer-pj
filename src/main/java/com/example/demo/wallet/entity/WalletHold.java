package com.example.demo.wallet.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.Check;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.example.demo.ecommerce.entity.Order;
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
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One hold per source of funds. CASH and BONUS for one order use separate holds. */
@Entity
@Table(name = "wallet_holds", uniqueConstraints = {
        @UniqueConstraint(name = "uk_wallet_hold_reference",
                columnNames = {"wallet_id", "hold_type", "reference_id", "balance_type"}),
        @UniqueConstraint(name = "uk_wallet_hold_order", columnNames = {"wallet_id", "order_id", "balance_type"})
}, indexes = {
        @Index(name = "idx_wallet_hold_status", columnList = "wallet_id, status")
})
@Check(name = "ck_wallet_hold_amounts", constraints =
        "amount > 0 AND captured_amount >= 0 AND released_amount >= 0 AND captured_amount + released_amount <= amount")
@Check(name = "ck_wallet_hold_order", constraints =
        "(hold_type = 'ORDER_PAYMENT' AND order_id IS NOT NULL) OR (hold_type = 'WITHDRAWAL' AND order_id IS NULL)")
@Check(name = "ck_wallet_hold_withdraw_cash", constraints = "hold_type <> 'WITHDRAWAL' OR balance_type = 'CASH'")
@Check(name = "ck_wallet_hold_status", constraints =
        "(status = 'ACTIVE' AND captured_amount = 0 AND released_amount = 0) OR "
        + "(status = 'PARTIALLY_PROCESSED' AND captured_amount + released_amount > 0 AND captured_amount + released_amount < amount) OR "
        + "(status = 'CAPTURED' AND captured_amount = amount AND released_amount = 0) OR "
        + "(status = 'RELEASED' AND released_amount = amount AND captured_amount = 0) OR "
        + "(status = 'CLOSED' AND captured_amount > 0 AND released_amount > 0 AND captured_amount + released_amount = amount)")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WalletHold {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id", nullable = false, updatable = false)
    @NotNull
    private Wallet wallet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", updatable = false)
    private Order order;

    @Column(name = "reference_id", nullable = false, length = 100, updatable = false)
    @NotBlank
    @Size(max = 100)
    private String referenceId;

    @Column(name = "hold_type", nullable = false, length = 20, updatable = false)
    @Enumerated(EnumType.STRING)
    @NotNull
    private WalletHoldType type;

    @Column(name = "balance_type", nullable = false, length = 20, updatable = false)
    @Enumerated(EnumType.STRING)
    @NotNull
    @Builder.Default
    private WalletBalanceType balanceType = WalletBalanceType.CASH;

    @Column(nullable = false, precision = 19, scale = 4, updatable = false)
    @NotNull
    @DecimalMin(value = "0", inclusive = false)
    @Digits(integer = 15, fraction = 4)
    private BigDecimal amount;

    @Column(name = "captured_amount", nullable = false, precision = 19, scale = 4)
    @ColumnDefault("0")
    @NotNull
    @PositiveOrZero
    @Digits(integer = 15, fraction = 4)
    @Builder.Default
    @Setter(AccessLevel.NONE)
    private BigDecimal capturedAmount = BigDecimal.ZERO;

    @Column(name = "released_amount", nullable = false, precision = 19, scale = 4)
    @ColumnDefault("0")
    @NotNull
    @PositiveOrZero
    @Digits(integer = 15, fraction = 4)
    @Builder.Default
    @Setter(AccessLevel.NONE)
    private BigDecimal releasedAmount = BigDecimal.ZERO;

    @Column(nullable = false, length = 25)
    @Enumerated(EnumType.STRING)
    @NotNull
    @Builder.Default
    @Setter(AccessLevel.NONE)
    private WalletHoldStatus status = WalletHoldStatus.ACTIVE;

    @Version
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Transient
    public BigDecimal getRemainingAmount() {
        return amount.subtract(capturedAmount).subtract(releasedAmount);
    }

    /** Only changes this hold. Wallet balance and transaction must be updated in the same DB transaction. */
    public void capture(BigDecimal value) {
        checkMovement(value);
        capturedAmount = capturedAmount.add(value);
        refreshStatus();
    }

    /** Release returns funds to their original CASH/BONUS balance, never converts BONUS to CASH. */
    public void release(BigDecimal value) {
        checkMovement(value);
        releasedAmount = releasedAmount.add(value);
        refreshStatus();
    }

    private void checkMovement(BigDecimal value) {
        if (value == null || value.signum() <= 0 || Math.max(value.stripTrailingZeros().scale(), 0) > 4
                || value.compareTo(getRemainingAmount()) > 0) {
            throw new IllegalArgumentException("Hold movement must be positive, have at most 4 decimals and not exceed remaining funds");
        }
    }

    private void refreshStatus() {
        if (getRemainingAmount().signum() > 0) {
            status = WalletHoldStatus.PARTIALLY_PROCESSED;
        } else if (capturedAmount.compareTo(amount) == 0) {
            status = WalletHoldStatus.CAPTURED;
        } else if (releasedAmount.compareTo(amount) == 0) {
            status = WalletHoldStatus.RELEASED;
        } else {
            status = WalletHoldStatus.CLOSED;
        }
    }

    @AssertTrue(message = "Captured and released funds must agree with hold amount and status")
    @JsonIgnore
    public boolean isAllocationValid() {
        if (amount == null || capturedAmount == null || releasedAmount == null || status == null) {
            return true; // Field-level constraints report missing values.
        }
        if (amount.signum() <= 0 || capturedAmount.signum() < 0 || releasedAmount.signum() < 0) {
            return false;
        }
        int comparison = capturedAmount.add(releasedAmount).compareTo(amount);
        return switch (status) {
            case ACTIVE -> capturedAmount.signum() == 0 && releasedAmount.signum() == 0;
            case PARTIALLY_PROCESSED -> comparison < 0 && capturedAmount.add(releasedAmount).signum() > 0;
            case CAPTURED -> capturedAmount.compareTo(amount) == 0 && releasedAmount.signum() == 0;
            case RELEASED -> releasedAmount.compareTo(amount) == 0 && capturedAmount.signum() == 0;
            case CLOSED -> comparison == 0 && capturedAmount.signum() > 0 && releasedAmount.signum() > 0;
        };
    }

    @AssertTrue(message = "Order holds require an order; withdrawals require CASH and must not reference an order")
    @JsonIgnore
    public boolean isPurposeValid() {
        return type == null || switch (type) {
            case ORDER_PAYMENT -> order != null && wallet != null && wallet.getUserId() != null
                    && wallet.getUserId().equals(order.getUserId());
            case WITHDRAWAL -> order == null && balanceType == WalletBalanceType.CASH;
        };
    }
}
