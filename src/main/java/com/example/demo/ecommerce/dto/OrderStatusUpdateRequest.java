package com.example.demo.ecommerce.dto;

import com.example.demo.ecommerce.entity.OrderStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class OrderStatusUpdateRequest{
        private OrderStatus status;
}
