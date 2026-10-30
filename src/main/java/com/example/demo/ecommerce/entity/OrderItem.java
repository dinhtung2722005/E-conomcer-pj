package com.example.demo.ecommerce.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "order_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItem {
    @Column(name = "seller_id", updatable = false)
    private Long sellerId;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", insertable = false, updatable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    @Setter(lombok.AccessLevel.NONE)
    private com.example.demo.user.entity.User seller;
    @Column(name = "settlement_amount", nullable = false, precision = 19, scale = 4, updatable = false)
    @org.hibernate.annotations.ColumnDefault("0") @Builder.Default
    private BigDecimal settlementAmount = BigDecimal.ZERO;
    @Column(name = "platform_fee", nullable = false, precision = 19, scale = 4, updatable = false)
    @org.hibernate.annotations.ColumnDefault("0") @Builder.Default
    private BigDecimal platformFee = BigDecimal.ZERO;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /**
     * Tên sản phẩm tại thời điểm mua — ảnh chụp (snapshot) để hóa đơn cũ
     * vẫn hiển thị đúng nếu sản phẩm sau này bị đổi tên.
     */
    @Column(name = "product_name", length = 255)
    private String productName;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "price_at_purchase", nullable = false, precision = 19, scale = 4)
    private BigDecimal priceAtPurchase;
}
