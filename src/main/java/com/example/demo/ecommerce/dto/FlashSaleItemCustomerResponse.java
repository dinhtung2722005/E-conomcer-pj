package com.example.demo.ecommerce.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
@Setter
@Getter
@AllArgsConstructor
public class FlashSaleItemCustomerResponse{
       private Long productId;
       private String productName;
       private BigDecimal originalPrice;
       private BigDecimal flashSalePrice;
       private Integer soldQuantity;
       private Integer totalQuantity;  
}