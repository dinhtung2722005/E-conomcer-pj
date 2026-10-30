package com.example.demo.wallet.entity;

import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;
import jakarta.persistence.*;
import lombok.*;

/** Append-only safe metadata. Never store attempted PINs, OTPs, email addresses or tokens. */
@Entity @Immutable @Getter @Builder @NoArgsConstructor @AllArgsConstructor
@Table(name = "wallet_security_events", indexes = {
        @Index(name = "idx_security_actor_time", columnList = "actor_id,created_at"),
        @Index(name = "idx_security_wallet_time", columnList = "wallet_id,created_at"),
        @Index(name = "idx_security_request", columnList = "request_id")})
public class WalletSecurityEvent {
    public enum Action { PIN_SETUP, PIN_CHANGE, PIN_VERIFY, PIN_LOCKED, PIN_RESET_REQUEST,
        PIN_RESET, RECOVERY_EMAIL_REQUEST, RECOVERY_EMAIL_CHANGE, RECOVERY_DELIVERY,
        AUTHENTICATION_FAILED, ACCESS_DENIED }
    public enum Outcome { SUCCESS, REJECTED }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "actor_id", updatable = false) private Long actorId;
    @Column(name = "wallet_id", updatable = false) private Long walletId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40, updatable = false) private Action action;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20, updatable = false) private Outcome outcome;
    @Column(nullable = false, length = 80, updatable = false) private String code;
    @Column(length = 100, updatable = false) private String reference;
    @Column(name = "request_id", length = 80, updatable = false) private String requestId;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
}
