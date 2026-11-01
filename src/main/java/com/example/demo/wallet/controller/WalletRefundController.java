package com.example.demo.wallet.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.example.demo.wallet.api.dto.RefundDecisionRequest;
import com.example.demo.wallet.api.dto.RefundCaseResponse;
import com.example.demo.wallet.api.dto.RefundCaseRequest;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.example.demo.shared.web.ApiResponse;
import com.example.demo.wallet.api.dto.*;
import com.example.demo.wallet.service.WalletRefundService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Wallet - Refunds", description = "Hồ sơ hoàn hàng/hoàn tiền. Khách chỉ thao tác trên đơn của mình; duyệt và hoàn ngoại lệ yêu cầu ADMIN.")
@RestController @RequiredArgsConstructor
public class WalletRefundController {
    private final WalletRefundService refunds;

    @PreAuthorize("hasRole('ADMIN')") @PostMapping("/api/admin/orders/{id}/refund")
    @Operation(summary = "Tạo hồ sơ hoàn tiền ngoại lệ (ADMIN)")
    public ApiResponse<RefundCaseResponse> requestAdmin(@PathVariable Long id,@Valid @RequestBody RefundCaseRequest request){
        return new ApiResponse<>(200,"Đã tạo hồ sơ hoàn ngoại lệ để kiểm tra và duyệt",refunds.requestAdminRefund(id,request));
    }

    @PreAuthorize("isAuthenticated()") @PostMapping("/api/orders/{id}/refund")
    @Operation(summary = "Yêu cầu hoàn hàng/hoàn tiền", description = "Chỉ chủ đơn; hỗ trợ hoàn từng phần theo mặt hàng. Chính sách hiện tại cho phép yêu cầu trong 7 ngày sau nhận hàng.")
    public ApiResponse<RefundCaseResponse> request(@PathVariable Long id, @Valid @RequestBody RefundCaseRequest request) {
        return new ApiResponse<>(200, "Đã tiếp nhận yêu cầu hoàn hàng", refunds.requestRefund(id, request));
    }
    @PreAuthorize("isAuthenticated()") @GetMapping("/api/orders/{id}/refund")
    @Operation(summary = "Xem hồ sơ hoàn tiền của đơn hàng")
    public ApiResponse<RefundCaseResponse> get(@PathVariable Long id) {
        return new ApiResponse<>(200, "Thành công", refunds.getMyRefund(id));
    }
    @PreAuthorize("hasRole('ADMIN')") @PostMapping("/api/admin/orders/{id}/refund/decision")
    @Operation(summary = "Duyệt hoặc từ chối hoàn tiền (ADMIN)", description = "Kiểm tra hàng trả và số lượng trước khi hoàn; tiền hoàn về ví theo nguồn CASH/BONUS gốc.")
    public ApiResponse<RefundCaseResponse> decide(@PathVariable Long id, @Valid @RequestBody RefundDecisionRequest request) {
        return new ApiResponse<>(200, "Đã xử lý yêu cầu hoàn hàng", refunds.decide(id, request));
    }
}
