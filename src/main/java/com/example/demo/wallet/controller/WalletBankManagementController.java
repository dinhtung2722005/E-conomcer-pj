package com.example.demo.wallet.controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import com.example.demo.user.dto_request.ApiResponse;
import com.example.demo.wallet.dto.WalletBankActionRequest;
import com.example.demo.wallet.service.WalletBankManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
@RestController @RequestMapping("/api/wallet") @PreAuthorize("isAuthenticated()") @RequiredArgsConstructor
public class WalletBankManagementController {
    private final WalletBankManagementService service;
    @PostMapping("/bank-accounts/{id}/revoke") public ApiResponse<Void> revoke(@PathVariable Long id,@Valid @RequestBody WalletBankActionRequest request){service.revoke(id,request);return new ApiResponse<>(200,"Đã thu hồi liên kết ngân hàng",null);}
    @PostMapping("/withdrawals/{id}/cancel") public ApiResponse<Void> cancel(@PathVariable Long id,@Valid @RequestBody WalletBankActionRequest request){service.cancelWithdrawal(id,request);return new ApiResponse<>(200,"Đã hủy rút tiền chưa gửi",null);}
}
