package com.example.demo.ecommerce.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "orders", indexes = {
        @Index(name = "idx_orders_status_created_at", columnList = "status, created_at"),
        @Index(name = "idx_orders_user_id", columnList = "user_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {
    @Column(nullable = false, precision = 7, scale = 4, updatable = false)
    @org.hibernate.annotations.ColumnDefault("0") @Builder.Default
    private BigDecimal feePercent = BigDecimal.ZERO;
    @Column(nullable = false, precision = 7, scale = 4, updatable = false)
    @org.hibernate.annotations.ColumnDefault("0") @Builder.Default
    private BigDecimal cashbackPercent = BigDecimal.ZERO;
    @Column(nullable = false, updatable = false)
    @org.hibernate.annotations.ColumnDefault("604800") @Builder.Default
    private Long returnWindowSeconds = 604800L;
    private LocalDateTime completedAt;
    private LocalDateTime settlementAvailableAt;
    private LocalDateTime settledAt;
    private LocalDateTime settlementNextAttemptAt;
    @Column(nullable=false) @org.hibernate.annotations.ColumnDefault("0")
    private int settlementAttempts;
    @Column(length=80) private String settlementLastError;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalAmount;
    @Column(name = "receiver_name", nullable = false)
    private String receiverName;

    @Column(name = "receiver_phone", nullable = false)
    private String receiverPhone;
    @Column(name = "shipping_address", nullable = false)
    private String shippingAddress;

    @Column(name = "discount_amount", precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;
    @Column(name = "final_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal finalAmount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "voucher_id")
    private Voucher voucher;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }
}

