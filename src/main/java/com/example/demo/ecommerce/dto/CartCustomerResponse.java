package com.example.demo.ecommerce.dto;

import java.math.BigDecimal;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
@Getter 
@Setter 
@AllArgsConstructor 
public class CartCustomerResponse {
    private Long cartId;
    private List<CartItemCustomerResponse> items;
    BigDecimal totalPrice;
}

