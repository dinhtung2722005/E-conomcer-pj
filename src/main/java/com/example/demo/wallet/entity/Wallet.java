package com.example.demo.wallet.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.UpdateTimestamp;

import com.example.demo.user.entity.User;
import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "wallets")
@Check(name = "ck_wallet_nonnegative_balances", constraints =
        "balance >= 0 AND pending_balance >= 0 AND bonus_balance >= 0 AND pending_bonus_balance >= 0")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Wallet {
    /** Opening balances are journaled once under this wallet's write lock before the first ledger movement. */
    @Column(name = "ledger_opened", nullable = false)
    @ColumnDefault("0")
    @Builder.Default
    private boolean ledgerOpened = false;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true, updatable = false)
    @NotNull
    @Positive
    private Long userId;

    /** Read-only association creates a foreign key while retaining the existing userId API. */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    @JsonIgnore
    @Setter(AccessLevel.NONE)
    private User user;

    /** Available cash: may be spent or withdrawn. Does not include held cash. */
    @Column(nullable = false, precision = 19, scale = 4)
    @ColumnDefault("0")
    @NotNull
    @PositiveOrZero
    @Digits(integer = 15, fraction = 4)
    @Builder.Default
    private BigDecimal balance = BigDecimal.ZERO;

    /** Cash reserved by active holds; not available for another payment/withdrawal. */
    @Column(name = "pending_balance", nullable = false, precision = 19, scale = 4)
    @ColumnDefault("0")
    @NotNull
    @PositiveOrZero
    @Digits(integer = 15, fraction = 4)
    @Builder.Default
    private BigDecimal pendingBalance = BigDecimal.ZERO;

    /** Promotional credit may be spent, but must not be withdrawn. */
    @Column(name = "bonus_balance", nullable = false, precision = 19, scale = 4)
    @ColumnDefault("0")
    @NotNull
    @PositiveOrZero
    @Digits(integer = 15, fraction = 4)
    @Builder.Default
    private BigDecimal bonusBalance = BigDecimal.ZERO;

    @Column(name = "pending_bonus_balance", nullable = false, precision = 19, scale = 4)
    @ColumnDefault("0")
    @NotNull
    @PositiveOrZero
    @Digits(integer = 15, fraction = 4)
    @Builder.Default
    private BigDecimal pendingBonusBalance = BigDecimal.ZERO;

    @Column(nullable = false, length = 3, updatable = false)
    @ColumnDefault("'VND'")
    @NotNull
    @Pattern(regexp = "[A-Z]{3}")
    @Builder.Default
    private String currency = "VND";

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @ColumnDefault("'ACTIVE'")
    @NotNull
    @Builder.Default
    private WalletStatus status = WalletStatus.ACTIVE;

    @Version 
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
