package com.example.demo.ecommerce.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.example.demo.ecommerce.entity.DiscountType;

import lombok.Getter;
import lombok.Setter;
@Getter
@Setter
public class VoucherRequest{
        private String code;
        private DiscountType discountType;
        private BigDecimal discountValue;
        private BigDecimal maxDiscountAmount; 
        private BigDecimal minOrderValue;
        private Integer totalQuantity;
        private LocalDateTime startDate;
        private LocalDateTime endDate;
}