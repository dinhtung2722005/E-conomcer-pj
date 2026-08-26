package com.example.demo.ecommerce.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class ProductCustomerResponse{
        private Long id;
        private String name;
        private String description;
        private BigDecimal originalPrice;
        private BigDecimal flashSalePrice;
        private String categoryName;
        private Integer availableQuantity;
}