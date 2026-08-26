package com.example.demo.ecommerce.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FlashSaleItemRequest{
        private Long productId;
        private BigDecimal promotionalPrice;
        private Integer totalQuantity;
}