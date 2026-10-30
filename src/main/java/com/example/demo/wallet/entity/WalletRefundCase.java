package com.example.demo.wallet.entity;

import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;
import com.example.demo.ecommerce.entity.Order;
import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name = "wallet_refund_cases")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class WalletRefundCase {
    public enum Status { REQUESTED, APPROVED, REJECTED }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, unique = true, updatable = false) private Order order;
    @Column(nullable = false, length = 500, updatable = false) private String reason;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    @Builder.Default private Status status = Status.REQUESTED;
    private Long reviewerId;
    @Column(length = 500) private String decisionReason;
    @CreationTimestamp @Column(updatable = false) private LocalDateTime createdAt;
    private LocalDateTime reviewedAt;
    @Version private Long version;
}
