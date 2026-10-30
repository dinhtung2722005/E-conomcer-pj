package com.example.demo.wallet.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.Check;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.UpdateTimestamp;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Separate from the wallet response. PIN verification/hashing and rate limits belong in the security service. */
@Entity
@Table(name = "wallet_security")
@Check(name = "ck_wallet_security_attempts", constraints = "failed_pin_attempts >= 0")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WalletSecurity {
    @Id
    @Column(name = "wallet_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "wallet_id")
    @NotNull
    @JsonIgnore
    private Wallet wallet;

    /** Encoded hash only. Null until PIN enrollment; no raw PIN or biometric data is stored. */
    @Column(name = "pin_hash", length = 255)
    @Size(min = 1, max = 255)
    @JsonIgnore
    private String pinHash;

    @Column(name = "failed_pin_attempts", nullable = false)
    @ColumnDefault("0")
    @PositiveOrZero
    private int failedPinAttempts;

    @Column(name = "pin_locked_until")
    private LocalDateTime pinLockedUntil;

    @Column(name = "pin_changed_at")
    private LocalDateTime pinChangedAt;

    @Version
    private Long version;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
