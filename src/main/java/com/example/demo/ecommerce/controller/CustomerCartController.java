package com.example.demo.ecommerce.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.ecommerce.api.dto.CartCustomerResponse;
import com.example.demo.ecommerce.service.CartService;
import com.example.demo.shared.web.ApiResponse;
import com.example.demo.user.api.UserAccountsApi;

import lombok.RequiredArgsConstructor;

/**
 * Giỏ hàng của khách đang đăng nhập.
 *
 * <p>Không còn tham số {@code userId}: người dùng được xác định từ JWT. Trước đây
 * {@code userId} nằm trong query string và {@code /api/carts/**} lại được
 * {@code permitAll()}, nên bất kỳ ai cũng đọc/ghi/xóa được giỏ hàng của người khác.
 */
@Tag(name = "Cart", description = "Giỏ hàng của tài khoản đang đăng nhập.")
@RestController
@RequestMapping("/api/carts")
@RequiredArgsConstructor
public class CustomerCartController {

    private final CartService cartService;
    private final UserAccountsApi currentUserService;

    @GetMapping
    @Operation(summary = "Xem giỏ hàng của tôi")
    public ApiResponse<CartCustomerResponse> getMyCart() {
        return new ApiResponse<>(200, "Thành công", cartService.getCart(currentUserService.getCurrentUserId()));
    }

    @PostMapping("/add")
    @Operation(summary = "Thêm sản phẩm vào giỏ")
    public ApiResponse<CartCustomerResponse> addToCart(@RequestParam Long productId,
                                                       @RequestParam(defaultValue = "1") Integer quantity) {
        Long userId = currentUserService.getCurrentUserId();
        cartService.addToCart(userId, productId, quantity);
        return new ApiResponse<>(200, "Đã thêm sản phẩm vào giỏ hàng", cartService.getCart(userId));
    }

    @PutMapping("/items/{itemId}")
    @Operation(summary = "Cập nhật số lượng trong giỏ")
    public ApiResponse<CartCustomerResponse> updateQuantity(@PathVariable Long itemId,
                                                            @RequestParam Integer quantity) {
        Long userId = currentUserService.getCurrentUserId();
        cartService.updateCartItemQuantity(userId, itemId, quantity);
        return new ApiResponse<>(200, "Đã cập nhật số lượng sản phẩm", cartService.getCart(userId));
    }

    @DeleteMapping("/items/{itemId}")
    @Operation(summary = "Xóa một mục trong giỏ")
    public ApiResponse<CartCustomerResponse> removeItem(@PathVariable Long itemId) {
        Long userId = currentUserService.getCurrentUserId();
        cartService.removeCartItem(userId, itemId);
        return new ApiResponse<>(200, "Đã xóa sản phẩm khỏi giỏ hàng", cartService.getCart(userId));
    }

    @DeleteMapping("/clear")
    @Operation(summary = "Xóa toàn bộ giỏ hàng")
    public ApiResponse<CartCustomerResponse> clearCart() {
        Long userId = currentUserService.getCurrentUserId();
        cartService.clearCart(userId);
        return new ApiResponse<>(200, "Đã làm sạch giỏ hàng", cartService.getCart(userId));
    }
}
