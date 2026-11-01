package com.example.demo.ecommerce.api.dto;

import com.example.demo.ecommerce.api.OrderStatus;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderStatusUpdateRequest {

    @NotNull(message = "Trạng thái không được để trống")
    private OrderStatus status;
}
