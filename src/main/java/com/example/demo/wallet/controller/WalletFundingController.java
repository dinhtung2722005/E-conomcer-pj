package com.example.demo.wallet.controller;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.example.demo.user.dto_request.ApiResponse;
import com.example.demo.wallet.dto.*;
import com.example.demo.wallet.service.WalletFundingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController @RequestMapping("/api/wallet")
@PreAuthorize("isAuthenticated()") @RequiredArgsConstructor
public class WalletFundingController {
    private final WalletFundingService funding;

    @GetMapping("/top-ups/{id}")
    public ApiResponse<TopUpResponse> getTopUp(@PathVariable Long id) {
        return new ApiResponse<>(200, "Thành công", funding.getTopUp(id));
    }

    @GetMapping("/withdrawals/{id}")
    public ApiResponse<WithdrawResponse> getWithdrawal(@PathVariable Long id) {
        return new ApiResponse<>(200, "Thành công", funding.getWithdrawal(id));
    }

    @GetMapping("/bank-accounts")
    public ApiResponse<List<BankAccountSummaryResponse>> accounts() {
        return new ApiResponse<>(200, "Thành công", funding.getMyBankAccounts());
    }

    @PostMapping("/bank-accounts")
    public ApiResponse<BankAccountSummaryResponse> link(@Valid @RequestBody LinkBankAccountRequest request) {
        return new ApiResponse<>(200, "Liên kết tài khoản thành công", funding.linkAccount(request));
    }

    @PostMapping("/top-ups")
    public ApiResponse<TopUpResponse> topUp(@Valid @RequestBody TopUpRequest request,
            @RequestHeader("Idempotency-Key") String key) {
        return new ApiResponse<>(200, "Đã tiếp nhận yêu cầu nạp tiền", funding.topUp(request, key));
    }

    @PostMapping("/withdrawals")
    public ApiResponse<WithdrawResponse> withdraw(@Valid @RequestBody WithdrawRequest request,
            @RequestHeader("Idempotency-Key") String key) {
        return new ApiResponse<>(200, "Đã tiếp nhận yêu cầu rút tiền", funding.withdraw(request, key));
    }
}
