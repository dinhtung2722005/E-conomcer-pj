package com.example.demo.wallet.entity;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name="wallet_alerts") @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class WalletAlert {
    public enum Status { OPEN, ACKNOWLEDGED, RESOLVED }
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false,unique=true,length=150,updatable=false) private String alertKey;
    @Column(nullable=false,length=50,updatable=false) private String category;
    @Column(nullable=false,length=100,updatable=false) private String reference;
    @Column(nullable=false,length=255) private String message;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) @Builder.Default private Status status=Status.OPEN;
    @CreationTimestamp @Column(updatable=false) private LocalDateTime createdAt;
    private LocalDateTime lastSeenAt;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    @Column(length=500) private String reviewReason;
    @Version private Long version;
}
