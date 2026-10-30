package com.example.demo.wallet.entity;
import java.time.LocalDateTime;
import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;
@Entity @Table(name="wallet_pin_recoveries",indexes=@Index(name="idx_pin_recovery_rate",columnList="wallet_id,created_at"))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class WalletPinRecovery {
    public enum Purpose { RESET_PIN, RECOVERY_EMAIL }
    @Id @Column(length=36) private String id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="wallet_id",nullable=false,updatable=false) @JsonIgnore private Wallet wallet;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30,updatable=false) private Purpose purpose;
    @JsonIgnore @Column(nullable=false,length=100,updatable=false) private String email;
    @JsonIgnore @Column(nullable=false,length=255,updatable=false) private String codeHash;
    @Column(nullable=false,updatable=false) private long tokenVersion;
    @Column(nullable=false,updatable=false) private Long securityVersion;
    @Column(nullable=false,updatable=false) private LocalDateTime createdAt;
    @Column(nullable=false,updatable=false) private LocalDateTime expiresAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime consumedAt;
    @Column(nullable=false) private int failedAttempts;
    @Version private Long version;
}
