package com.example.demo.ecommerce.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.ecommerce.dto.OrderRequest;
import com.example.demo.ecommerce.service.CheckoutService;
import com.example.demo.user.dto_request.ApiResponse;

@RestController
@RequestMapping("/api/orders")
public class CustomerOrderController {

    @Autowired
    private CheckoutService checkoutService;

    @PostMapping("/checkout")
    public ApiResponse<String> checkout(
            @RequestParam Long userId,
            @RequestBody OrderRequest request) {
        
        checkoutService.placeOrder(userId, request);
        return new ApiResponse<>(200, "Đặt hàng thành công!", null);
    }
}