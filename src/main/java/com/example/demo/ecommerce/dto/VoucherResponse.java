package com.example.demo.ecommerce.dto;

import com.example.demo.ecommerce.entity.DiscountType;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
@Setter
@Getter
public class VoucherResponse{
        private Long id;
        private String code;
        private DiscountType discountType;
        private BigDecimal discountValue;
        private BigDecimal maxDiscountAmount;
        private BigDecimal minOrderValue;
        private Integer totalQuantity;
        private Integer usedQuantity;
        private LocalDateTime startDate;
        private LocalDateTime endDate;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
}