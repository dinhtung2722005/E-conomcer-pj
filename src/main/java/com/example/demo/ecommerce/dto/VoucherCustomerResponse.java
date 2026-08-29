package com.example.demo.ecommerce.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class VoucherCustomerResponse{
       private  Long id;
       private String code;
       private String description;
       private String discountType;    
       private BigDecimal discountValue; 
       private BigDecimal maxDiscount;
       private BigDecimal minOrderValue; 
       private LocalDateTime endDate;
}