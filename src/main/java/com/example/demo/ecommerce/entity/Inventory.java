package com.example.demo.ecommerce.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "inventories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false, unique = true)
    private Product product;

    // Số lượng thực tế có thể bán
    @Column(name = "available_quantity", nullable = false)
    private Integer availableQuantity;

    // Số lượng đang bị tạm giữ (khi user đặt hàng nhưng chưa thanh toán xong)
    @Column(name = "locked_quantity", nullable = false)
    private Integer lockedQuantity;

    // Khóa lạc quan (Optimistic Locking) chống over-selling
    @Version
    private Long version;
}