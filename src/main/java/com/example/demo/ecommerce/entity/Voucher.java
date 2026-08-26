package com.example.demo.ecommerce.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "vouchers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Voucher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Mã nhập vào (VD: FREESHIP, SALE50)
    @Column(nullable = false, unique = true, length = 50)
    private String code; 

    @Column(name = "discount_type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private DiscountType discountType;

    // Giá trị giảm (VD: 50 nếu là %, hoặc 50000 nếu là FIXED)
    @Column(name = "discount_value", nullable = false, precision = 19, scale = 4)
    private BigDecimal discountValue;

    // Mức giảm tối đa (Dành cho loại giảm theo % - VD: Giảm 50% nhưng tối đa 30k)
    @Column(name = "max_discount_amount", precision = 19, scale = 4)
    private BigDecimal maxDiscountAmount;

    // Đơn hàng tối thiểu mới được áp dụng
    @Column(name = "min_order_value", nullable = false, precision = 19, scale = 4)
    private BigDecimal minOrderValue;

    @Column(name = "total_quantity", nullable = false)
    private Integer totalQuantity;

    @Column(name = "used_quantity", nullable = false)
    @Builder.Default
    private Integer usedQuantity = 0;

    @Column(name = "start_date", nullable = false)
    private LocalDateTime startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDateTime endDate;

    // Khóa lạc quan để xử lý tranh chấp khi nhiều người cùng dùng voucher cuối cùng
    @Version
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
