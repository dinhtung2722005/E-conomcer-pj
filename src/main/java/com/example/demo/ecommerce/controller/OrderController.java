package com.example.demo.ecommerce.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.ecommerce.dto.OrderResponse;
import com.example.demo.ecommerce.dto.OrderStatusUpdateRequest;
import com.example.demo.ecommerce.service.OrderService;
import com.example.demo.user.dto_request.ApiResponse;

@RestController
@RequestMapping("/api/admin/orders")
@PreAuthorize("hasRole('ADMIN')")
public class OrderController {

    @Autowired
    private OrderService orderService;

    @GetMapping
    public ApiResponse<List<OrderResponse>> getAllOrders() {
        return new ApiResponse<>(200, "Thành công", orderService.getAllOrders());
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderResponse> getOrderById(@PathVariable Long id) {
        return new ApiResponse<>(200, "Thành công", orderService.getOrderById(id));
    }

    @PutMapping("/{id}/status")
    public ApiResponse<OrderResponse> updateOrderStatus(
            @PathVariable Long id, 
            @RequestBody OrderStatusUpdateRequest request) {
        return new ApiResponse<>(200, "Cập nhật trạng thái thành công", 
                orderService.updateOrderStatus(id, request.getStatus()));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<OrderResponse> cancelOrder(@PathVariable Long id) {
        return new ApiResponse<>(200, "Đã hủy đơn và hoàn trả kho thành công", 
                orderService.cancelOrder(id));
    }
}