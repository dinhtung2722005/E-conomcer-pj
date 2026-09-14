package com.example.demo.ecommerce.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.ecommerce.dto.CartCustomerResponse;
import com.example.demo.ecommerce.service.CartService;
import com.example.demo.user.dto_request.ApiResponse;

@RestController
@RequestMapping("/api/carts")
public class CustomerCartController {

    @Autowired
    private CartService cartService;
    @GetMapping
    public ApiResponse<CartCustomerResponse> getMyCart(@RequestParam Long userId) {
        return new ApiResponse<>(200, "Thành công", cartService.getCart(userId));
    }

    @PostMapping("/add")
    public ApiResponse<String> addToCart(
            @RequestParam Long userId,
            @RequestParam Long productId,
            @RequestParam(defaultValue = "1") Integer quantity) {
        
        cartService.addToCart(userId, productId, quantity);
        return new ApiResponse<>(200, "Đã thêm sản phẩm vào giỏ hàng", null);
    }
    @PutMapping("/items/{itemId}")
    public ApiResponse<String> updateQuantity(
            @RequestParam Long userId,
            @PathVariable Long itemId,
            @RequestParam Integer quantity) {
        
        cartService.updateCartItemQuantity(userId, itemId, quantity);
        return new ApiResponse<>(200, "Đã cập nhật số lượng sản phẩm", null);
    }


    @DeleteMapping("/items/{itemId}")
    public ApiResponse<String> removeItem(
            @RequestParam Long userId,
            @PathVariable Long itemId) {
        
        cartService.removeCartItem(userId, itemId);
        return new ApiResponse<>(200, "Đã xóa sản phẩm khỏi giỏ hàng", null);
    }

    @DeleteMapping("/clear")
    public ApiResponse<String> clearCart(@RequestParam Long userId) {
        cartService.clearCart(userId);
        return new ApiResponse<>(200, "Đã làm sạch giỏ hàng", null);
    }
}