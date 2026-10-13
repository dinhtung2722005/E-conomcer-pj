package com.example.demo.ecommerce.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.ecommerce.dto.OrderRequest;
import com.example.demo.ecommerce.dto.OrderResponse;
import com.example.demo.ecommerce.service.CheckoutService;
import com.example.demo.ecommerce.service.OrderService;
import com.example.demo.user.dto_request.ApiResponse;
import com.example.demo.user.service.CurrentUserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Đơn hàng của khách đang đăng nhập.
 *
 * <p>Bổ sung các endpoint "đơn của tôi" — trước đây {@link OrderService} đã có sẵn
 * logic nhưng không controller nào gọi tới, nên khách không thể xem lại đơn của mình.
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class CustomerOrderController {

    private final CheckoutService checkoutService;
    private final OrderService orderService;
    private final CurrentUserService currentUserService;

    /** Đặt hàng từ giỏ hiện tại. Trả về đơn vừa tạo (trước đây kết quả bị bỏ đi). */
    @PostMapping("/checkout")
    public ApiResponse<OrderResponse> checkout(@Valid @RequestBody OrderRequest request) {
        OrderResponse order = checkoutService.placeOrder(currentUserService.getCurrentUserId(), request);
        return new ApiResponse<>(201, "Đặt hàng thành công", order);
    }

    @GetMapping
    public ApiResponse<List<OrderResponse>> getMyOrders() {
        return new ApiResponse<>(200, "Thành công",
                orderService.getUserOrders(currentUserService.getCurrentUserId()));
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderResponse> getMyOrderDetail(@PathVariable Long id) {
        return new ApiResponse<>(200, "Thành công",
                orderService.getOrderDetail(id, currentUserService.getCurrentUserId()));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<OrderResponse> cancelMyOrder(@PathVariable Long id) {
        return new ApiResponse<>(200, "Đã hủy đơn hàng",
                orderService.cancelOrderByUser(id, currentUserService.getCurrentUserId()));
    }
}
