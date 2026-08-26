package com.example.demo.ecommerce.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class OrderItemResponse {
    private Long id; 
    private Long productId; 
    private String productName;
    private Integer quantity;
    private BigDecimal price;
}