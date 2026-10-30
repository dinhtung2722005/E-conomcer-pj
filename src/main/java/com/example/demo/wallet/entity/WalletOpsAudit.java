package com.example.demo.wallet.entity;
import java.time.LocalDateTime;
import org.hibernate.annotations.*;
import jakarta.persistence.*;
import jakarta.persistence.Table;
import lombok.*;
@Entity @Immutable @Table(name="wallet_ops_audit",indexes={
    @Index(name="idx_ops_audit_wallet_time",columnList="wallet_id,created_at"),
    @Index(name="idx_ops_audit_actor_time",columnList="actor_id,created_at"),
    @Index(name="idx_ops_audit_request",columnList="request_id")}) @Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class WalletOpsAudit {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false,updatable=false) private Long actorId;
    @Column(name="wallet_id",updatable=false) private Long walletId;
    @Column(name="request_id",length=80,updatable=false) private String requestId;
    @Column(nullable=false,length=50,updatable=false) private String action;
    @Column(nullable=false,length=100,updatable=false) private String reference;
    @Column(nullable=false,length=500,updatable=false) private String reason;
    @CreationTimestamp @Column(updatable=false) private LocalDateTime createdAt;
    @PrePersist void correlate() { requestId=org.slf4j.MDC.get("requestId"); }
}
