package com.example.demo.ecommerce.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.ecommerce.api.dto.OrderRequest;
import com.example.demo.ecommerce.api.dto.OrderResponse;
import com.example.demo.ecommerce.service.CheckoutService;
import com.example.demo.ecommerce.service.OrderService;
import com.example.demo.shared.web.ApiResponse;
import com.example.demo.user.api.UserAccountsApi;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Đơn hàng của khách đang đăng nhập.
 *
 * <p>Bổ sung các endpoint "đơn của tôi" — trước đây {@link OrderService} đã có sẵn
 * logic nhưng không controller nào gọi tới, nên khách không thể xem lại đơn của mình.
 */
@Tag(name = "Orders", description = "Đơn hàng của tài khoản đang đăng nhập.")
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class CustomerOrderController {

    private final CheckoutService checkoutService;
    private final OrderService orderService;
    private final com.example.demo.ecommerce.api.OrderWorkflowUseCase workflow;
    private final UserAccountsApi currentUserService;

    /** Đặt hàng từ giỏ hiện tại. Trả về đơn vừa tạo (trước đây kết quả bị bỏ đi). */
    @PostMapping("/checkout")
    @Operation(summary = "Tạo đơn từ giỏ hàng", description = "Tạo đơn PENDING và giữ tồn kho; chưa thanh toán. Gọi POST /api/orders/{id}/pay để thanh toán bằng ví. Endpoint hiện trả HTTP 200, code trong response là 201.")
    public ApiResponse<OrderResponse> checkout(@Valid @RequestBody OrderRequest request) {
        OrderResponse order = checkoutService.placeOrder(currentUserService.getCurrentUserId(), request);
        return new ApiResponse<>(201, "Đặt hàng thành công", order);
    }

    @GetMapping
    @Operation(summary = "Danh sách đơn hàng của tôi")
    public ApiResponse<List<OrderResponse>> getMyOrders() {
        return new ApiResponse<>(200, "Thành công",
                orderService.getUserOrders(currentUserService.getCurrentUserId()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết đơn hàng của tôi")
    public ApiResponse<OrderResponse> getMyOrderDetail(@PathVariable Long id) {
        return new ApiResponse<>(200, "Thành công",
                orderService.getOrderDetail(id, currentUserService.getCurrentUserId()));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Hủy đơn hàng của tôi", description = "Kiểm tra điều kiện hủy, hoàn tồn kho và hoàn tiền đã thanh toán trong cùng luồng nghiệp vụ.")
    public ApiResponse<OrderResponse> cancelMyOrder(@PathVariable Long id) {
        return new ApiResponse<>(200, "Đã hủy đơn hàng",
                workflow.cancelOrderByUser(id, currentUserService.getCurrentUserId()));
    }
}
