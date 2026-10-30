package com.example.demo.wallet.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.example.demo.user.dto_request.ApiResponse;
import com.example.demo.wallet.dto.*;
import com.example.demo.wallet.service.WalletRefundService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController @RequiredArgsConstructor
public class WalletRefundController {
    private final WalletRefundService refunds;

    @PreAuthorize("isAuthenticated()") @PostMapping("/api/orders/{id}/refund")
    public ApiResponse<RefundCaseResponse> request(@PathVariable Long id, @Valid @RequestBody RefundCaseRequest request) {
        return new ApiResponse<>(200, "Đã tiếp nhận yêu cầu hoàn hàng", refunds.requestRefund(id, request));
    }
    @PreAuthorize("isAuthenticated()") @GetMapping("/api/orders/{id}/refund")
    public ApiResponse<RefundCaseResponse> get(@PathVariable Long id) {
        return new ApiResponse<>(200, "Thành công", refunds.getMyRefund(id));
    }
    @PreAuthorize("hasRole('ADMIN')") @PostMapping("/api/admin/orders/{id}/refund/decision")
    public ApiResponse<RefundCaseResponse> decide(@PathVariable Long id, @Valid @RequestBody RefundDecisionRequest request) {
        return new ApiResponse<>(200, "Đã xử lý yêu cầu hoàn hàng", refunds.decide(id, request));
    }
}
