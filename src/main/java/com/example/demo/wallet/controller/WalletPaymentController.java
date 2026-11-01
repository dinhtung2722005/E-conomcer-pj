package com.example.demo.wallet.controller;
import com.example.demo.ecommerce.api.dto.OrderResponse;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.example.demo.shared.web.ApiResponse;
import com.example.demo.wallet.api.dto.PayOrderRequest;
import com.example.demo.wallet.api.dto.PaymentResponse;
import com.example.demo.wallet.api.OrderPaymentUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController @RequestMapping("/api/orders")
@PreAuthorize("isAuthenticated()") @RequiredArgsConstructor
public class WalletPaymentController {
    private final OrderPaymentUseCase payments;
    private final com.example.demo.ecommerce.api.OrderLifecycleApi orders;

    @PostMapping("/{id}/confirm-receipt")
    public ApiResponse<com.example.demo.ecommerce.api.dto.OrderResponse> confirmReceipt(@PathVariable Long id) {
        return new ApiResponse<>(200, "Đã xác nhận nhận hàng", orders.confirmReceipt(id));
    }
    @PostMapping("/{id}/pay")
    public ApiResponse<PaymentResponse> pay(@PathVariable Long id, @Valid @RequestBody PayOrderRequest request,
            @RequestHeader("Idempotency-Key") String key) {
        return new ApiResponse<>(200, "Thanh toán thành công", payments.payOrder(id, request, key));
    }
}
