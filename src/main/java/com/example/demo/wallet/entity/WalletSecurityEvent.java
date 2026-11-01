package com.example.demo.wallet.entity;
import com.example.demo.wallet.api.SecurityOutcome;
import com.example.demo.wallet.api.SecurityAction;

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


    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "actor_id", updatable = false) private Long actorId;
    @Column(name = "wallet_id", updatable = false) private Long walletId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40, updatable = false) private SecurityAction action;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20, updatable = false) private SecurityOutcome outcome;
    @Column(nullable = false, length = 80, updatable = false) private String code;
    @Column(length = 100, updatable = false) private String reference;
    @Column(name = "request_id", length = 80, updatable = false) private String requestId;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
}
