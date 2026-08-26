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
public class FlashSaleItemResponse{
        private Long id;
        private Long flashSaleId;
        private Long productId;
        private String productName;         
        private BigDecimal promotionalPrice;
        private Integer totalQuantity;
        private Integer soldQuantity;
}