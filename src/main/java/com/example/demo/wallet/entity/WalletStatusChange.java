package com.example.demo.wallet.entity;
import com.example.demo.wallet.api.WalletStatus;

import java.time.LocalDateTime;

import org.hibernate.annotations.Check;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;

import com.example.demo.user.api.UserRole;

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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Append-only audit of actual state transitions. Repeated requests do not create another transition. */
@Entity
@Immutable
@Table(name = "wallet_status_changes", indexes = {
        @Index(name = "idx_wallet_status_change_history", columnList = "wallet_id, created_at")
})
@Check(name = "ck_wallet_status_change_transition", constraints = "previous_status <> new_status")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WalletStatusChange {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id", nullable = false, updatable = false)
    @NotNull
    private Wallet wallet;

    @Column(name="actor_user_id",nullable=false,updatable=false)
    @NotNull private Long actorId;

    @Column(name = "actor_role", nullable = false, length = 20, updatable = false)
    @Enumerated(EnumType.STRING)
    @NotNull
    private UserRole actorRole;

    @Column(name = "previous_status", nullable = false, length = 20, updatable = false)
    @Enumerated(EnumType.STRING)
    @NotNull
    private WalletStatus previousStatus;

    @Column(name = "new_status", nullable = false, length = 20, updatable = false)
    @Enumerated(EnumType.STRING)
    @NotNull
    private WalletStatus newStatus;

    @Column(nullable = false, length = 500, updatable = false)
    @NotBlank
    @Size(max = 500)
    private String reason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
