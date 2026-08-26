package com.example.demo.ecommerce.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.example.demo.ecommerce.entity.OrderStatus;

import lombok.Getter;
import lombok.Setter;
@Getter
@Setter
public class OrderResponse{
        private Long id;
        private Long userId;
        private BigDecimal totalAmount;
        private OrderStatus status;
        private List<OrderItemResponse> items;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
}