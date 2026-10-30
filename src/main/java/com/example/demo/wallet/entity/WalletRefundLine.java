package com.example.demo.wallet.entity;
import java.math.BigDecimal;
import jakarta.persistence.*;
import com.example.demo.ecommerce.entity.OrderItem;
import lombok.*;
@Entity @Table(name="wallet_refund_lines",uniqueConstraints=@UniqueConstraint(name="uk_refund_line",columnNames={"case_id","order_item_id"}))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
@org.hibernate.annotations.Check(constraints="quantity > 0 and amount >= 0 and fee_amount >= 0 and fee_amount <= amount")
public class WalletRefundLine {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="case_id",nullable=false,updatable=false) private WalletRefundCase refundCase;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="order_item_id",nullable=false,updatable=false) private OrderItem item;
    @Column(nullable=false,updatable=false) private int quantity;
    @Column(nullable=false,precision=19,scale=4,updatable=false) private BigDecimal amount;
    @Column(nullable=false,precision=19,scale=4,updatable=false) private BigDecimal feeAmount;
}
