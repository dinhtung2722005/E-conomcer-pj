package com.example.demo.ecommerce.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.ecommerce.api.dto.OrderResponse;
import com.example.demo.ecommerce.api.dto.OrderStatusUpdateRequest;
import com.example.demo.ecommerce.service.OrderService;
import com.example.demo.shared.web.ApiResponse;
import com.example.demo.shared.web.PageQuery;
import com.example.demo.shared.web.PageResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/orders")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final com.example.demo.ecommerce.api.OrderWorkflowUseCase workflow;

    @GetMapping
    public ApiResponse<PageResponse<OrderResponse>> getAllOrders(@Valid @ModelAttribute PageQuery pagination) {
        return new ApiResponse<>(200, "Thành công", orderService.getAllOrders(pagination));
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderResponse> getOrderById(@PathVariable Long id) {
        return new ApiResponse<>(200, "Thành công", orderService.getOrderById(id));
    }

    /**
     * Chuyển trạng thái đơn hàng.
     *
     * <p>Chỉ chấp nhận các bước chuyển hợp lệ. Khi sang {@code PAID}, hàng đang giữ chỗ
     * được trừ hẳn khỏi kho; khi sang {@code CANCELLED}, hàng được hoàn và lượt voucher
     * được trả lại.
     */
    @PutMapping("/{id}/status")
    public ApiResponse<OrderResponse> updateOrderStatus(@PathVariable Long id,
                                                        @Valid @RequestBody OrderStatusUpdateRequest request) {
        return new ApiResponse<>(200, "Cập nhật trạng thái thành công",
                workflow.updateOrderStatus(id, request.getStatus()));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<OrderResponse> cancelOrder(@PathVariable Long id) {
        return new ApiResponse<>(200, "Đã hủy đơn và hoàn trả kho thành công",
                workflow.cancelOrder(id));
    }
}
