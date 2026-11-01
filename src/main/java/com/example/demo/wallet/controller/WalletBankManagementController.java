package com.example.demo.wallet.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import com.example.demo.shared.web.ApiResponse;
import com.example.demo.wallet.api.dto.WalletBankActionRequest;
import com.example.demo.wallet.service.WalletBankManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
@Tag(name = "Wallet - Bank Accounts", description = "Quản lý tài khoản ngân hàng và yêu cầu rút của chính người dùng.")
@RestController @RequestMapping("/api/wallet") @PreAuthorize("isAuthenticated()") @RequiredArgsConstructor
public class WalletBankManagementController {
    private final WalletBankManagementService service;
    @Operation(summary = "Thu hồi liên kết ngân hàng", description = "Yêu cầu mã PIN. Liên kết bị thu hồi không được dùng cho giao dịch mới.")
    @PostMapping("/bank-accounts/{id}/revoke") public ApiResponse<Void> revoke(@PathVariable Long id,@Valid @RequestBody WalletBankActionRequest request){service.revoke(id,request);return new ApiResponse<>(200,"Đã thu hồi liên kết ngân hàng",null);}
    @Operation(summary = "Hủy yêu cầu rút chưa gửi ngân hàng", description = "Yêu cầu mã PIN; chỉ hủy khi chưa gửi sang đối tác. Giải phóng khoản tạm giữ tương ứng.")
    @PostMapping("/withdrawals/{id}/cancel") public ApiResponse<Void> cancel(@PathVariable Long id,@Valid @RequestBody WalletBankActionRequest request){service.cancelWithdrawal(id,request);return new ApiResponse<>(200,"Đã hủy rút tiền chưa gửi",null);}
}
