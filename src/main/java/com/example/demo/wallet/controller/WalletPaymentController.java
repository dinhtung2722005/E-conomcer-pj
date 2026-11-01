package com.example.demo.wallet.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.example.demo.ecommerce.api.dto.OrderResponse;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.example.demo.shared.web.ApiResponse;
import com.example.demo.wallet.api.dto.PayOrderRequest;
import com.example.demo.wallet.api.dto.PaymentResponse;
import com.example.demo.wallet.api.OrderPaymentUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Wallet - Payments", description = "Thanh toán đơn hàng bằng ví của tài khoản đang đăng nhập.")
@RestController @RequestMapping("/api/orders")
@PreAuthorize("isAuthenticated()") @RequiredArgsConstructor
public class WalletPaymentController {
    private final OrderPaymentUseCase payments;
    private final com.example.demo.ecommerce.api.OrderLifecycleApi orders;

    @PostMapping("/{id}/confirm-receipt")
    @Operation(summary = "Xác nhận đã nhận hàng", description = "Ghi nhận mốc nhận hàng để tính thời hạn hoàn hàng và quyết toán người bán.")
    public ApiResponse<com.example.demo.ecommerce.api.dto.OrderResponse> confirmReceipt(@PathVariable Long id) {
        return new ApiResponse<>(200, "Đã xác nhận nhận hàng", orders.confirmReceipt(id));
    }
    @PostMapping("/{id}/pay")
    @Operation(summary = "Thanh toán đơn hàng bằng ví", description = "Yêu cầu mã PIN và Idempotency-Key. Số tiền, chủ đơn và người bán được tra từ đơn hàng trên máy chủ. CASH/BONUS theo useBonus; áp dụng hạn mức và kiểm tra công nợ. Tiền được tạm giữ trước quyết toán.")
    public ApiResponse<PaymentResponse> pay(@PathVariable Long id, @Valid @RequestBody PayOrderRequest request,
            @RequestHeader("Idempotency-Key") String key) {
        return new ApiResponse<>(200, "Thanh toán thành công", payments.payOrder(id, request, key));
    }
}
