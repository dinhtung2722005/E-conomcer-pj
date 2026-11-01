package com.example.demo.wallet.entity;
import com.example.demo.wallet.api.RefundStatus;

import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;
import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name = "wallet_refund_cases",uniqueConstraints=@UniqueConstraint(name="uk_refund_request",columnNames={"order_id","request_key"}))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
@org.hibernate.annotations.Check(constraints="amount >= 0 and fee_amount >= 0 and fee_amount <= amount and bonus_recovery_amount >= 0")
public class WalletRefundCase {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name="order_id",nullable=false,updatable=false) private Long orderId;
    @Column(name="request_key",nullable=false,length=80,updatable=false) private String requestKey;
    @Column(nullable=false,length=64,updatable=false) private String fingerprint;
    @Column(nullable=false,precision=19,scale=4,updatable=false) private java.math.BigDecimal amount;
    @Column(nullable=false,precision=19,scale=4,updatable=false) private java.math.BigDecimal feeAmount;
    @Column(nullable=false,precision=19,scale=4) @Builder.Default private java.math.BigDecimal bonusRecoveryAmount=java.math.BigDecimal.ZERO;
    @OneToMany(mappedBy="refundCase",cascade=CascadeType.ALL) @Builder.Default private java.util.List<WalletRefundLine> lines=new java.util.ArrayList<>();
    @Column(nullable = false, length = 500, updatable = false) private String reason;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    @Builder.Default private RefundStatus status = RefundStatus.REQUESTED;
    private Long reviewerId;
    @Column(length = 500) private String decisionReason;
    @CreationTimestamp @Column(updatable = false) private LocalDateTime createdAt;
    private LocalDateTime reviewedAt;
    @Version private Long version;
}
