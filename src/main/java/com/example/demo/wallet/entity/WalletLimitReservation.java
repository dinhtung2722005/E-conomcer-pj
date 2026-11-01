package com.example.demo.wallet.entity;

import java.math.BigDecimal;
import java.time.Instant;
import com.example.demo.wallet.api.TransactionType;
import jakarta.persistence.*;
import lombok.*;

/** A committed request consumes quota even if its bank result is pending, failed or later refunded. */
@Entity
@Table(name="wallet_limit_reservations", indexes=@Index(name="idx_wallet_limit_window",columnList="wallet_id,type,reserved_at"))
@org.hibernate.annotations.Immutable
@org.hibernate.annotations.Check(constraints="amount >= 0")
@Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class WalletLimitReservation {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false)
    @JoinColumn(name="wallet_id",nullable=false,updatable=false) private Wallet wallet;
    @OneToOne(fetch=FetchType.LAZY,optional=false)
    @JoinColumn(name="operation_id",nullable=false,unique=true,updatable=false) private WalletOperation operation;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20,updatable=false) private TransactionType type;
    @Column(nullable=false,precision=19,scale=4,updatable=false) private BigDecimal amount;
    @Column(name="reserved_at",nullable=false,updatable=false) private Instant reservedAt;
}
