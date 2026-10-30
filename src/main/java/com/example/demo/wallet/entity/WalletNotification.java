package com.example.demo.wallet.entity;
import java.time.LocalDateTime;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import lombok.*;
@Entity @Table(name="wallet_notifications",indexes=@Index(name="idx_wallet_notification",columnList="wallet_id,id"))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class WalletNotification {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="wallet_id",nullable=false,updatable=false) private Wallet wallet;
    @Column(nullable=false,unique=true,length=150,updatable=false) private String eventKey;
    @Column(nullable=false,length=100,updatable=false) private String title;
    @Column(nullable=false,length=500,updatable=false) private String message;
    private Long transactionId;
    @CreationTimestamp @Column(updatable=false) private LocalDateTime createdAt;
    private LocalDateTime readAt;
    @Version private Long version;
}
