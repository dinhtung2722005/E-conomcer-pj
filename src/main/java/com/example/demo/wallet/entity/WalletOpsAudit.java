package com.example.demo.wallet.entity;
import java.time.LocalDateTime;
import org.hibernate.annotations.*;
import jakarta.persistence.*;
import jakarta.persistence.Table;
import lombok.*;
@Entity @Immutable @Table(name="wallet_ops_audit") @Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class WalletOpsAudit {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false,updatable=false) private Long actorId;
    @Column(nullable=false,length=50,updatable=false) private String action;
    @Column(nullable=false,length=100,updatable=false) private String reference;
    @Column(nullable=false,length=500,updatable=false) private String reason;
    @CreationTimestamp @Column(updatable=false) private LocalDateTime createdAt;
}
