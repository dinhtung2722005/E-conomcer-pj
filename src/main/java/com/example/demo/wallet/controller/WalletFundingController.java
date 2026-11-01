package com.example.demo.wallet.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.example.demo.wallet.api.dto.WithdrawResponse;
import com.example.demo.wallet.api.dto.WithdrawRequest;
import com.example.demo.wallet.api.dto.TopUpResponse;
import com.example.demo.wallet.api.dto.TopUpRequest;
import com.example.demo.wallet.api.dto.LinkBankAccountRequest;
import com.example.demo.wallet.api.dto.BankAccountSummaryResponse;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.example.demo.shared.web.ApiResponse;
import com.example.demo.wallet.api.dto.*;
import com.example.demo.wallet.service.WalletFundingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Wallet - Funding", description = "Nạp/rút qua tài khoản ngân hàng đã xác minh của chính chủ. Cần cấu hình đối tác để xử lý tiền thật.")
@RestController @RequestMapping("/api/wallet")
@PreAuthorize("isAuthenticated()") @RequiredArgsConstructor
public class WalletFundingController {
    private final WalletFundingService funding;

    @GetMapping("/top-ups/{id}")
    @Operation(summary = "Tra cứu trạng thái yêu cầu nạp")
    public ApiResponse<TopUpResponse> getTopUp(@PathVariable Long id) {
        return new ApiResponse<>(200, "Thành công", funding.getTopUp(id));
    }

    @GetMapping("/withdrawals/{id}")
    @Operation(summary = "Tra cứu trạng thái yêu cầu rút")
    public ApiResponse<WithdrawResponse> getWithdrawal(@PathVariable Long id) {
        return new ApiResponse<>(200, "Thành công", funding.getWithdrawal(id));
    }

    @GetMapping("/bank-accounts")
    @Operation(summary = "Danh sách tài khoản ngân hàng đã liên kết")
    public ApiResponse<List<BankAccountSummaryResponse>> accounts() {
        return new ApiResponse<>(200, "Thành công", funding.getMyBankAccounts());
    }

    @PostMapping("/bank-accounts")
    @Operation(summary = "Liên kết tài khoản ngân hàng chính chủ", description = "Thông tin tài khoản được xác minh qua đối tác; không coi yêu cầu liên kết là xác minh thành công.")
    public ApiResponse<BankAccountSummaryResponse> link(@Valid @RequestBody LinkBankAccountRequest request) {
        return new ApiResponse<>(200, "Liên kết tài khoản thành công", funding.linkAccount(request));
    }

    @PostMapping("/top-ups")
    @Operation(summary = "Tạo yêu cầu nạp tiền", description = "Số tiền là VND nguyên dương. Tiền chỉ được ghi có sau xác nhận đối tác; CASH có thể được dùng tự động để thu hồi công nợ. Áp dụng hạn mức cấu hình.")
    public ApiResponse<TopUpResponse> topUp(@Valid @RequestBody TopUpRequest request,
            @RequestHeader("Idempotency-Key") String key) {
        return new ApiResponse<>(200, "Đã tiếp nhận yêu cầu nạp tiền", funding.topUp(request, key));
    }

    @PostMapping("/withdrawals")
    @Operation(summary = "Tạo yêu cầu rút tiền", description = "Yêu cầu mã PIN. Chỉ rút CASH khả dụng về tài khoản chính chủ đã xác minh; khóa tiền trong khi chờ đối tác. VND nguyên dương, áp dụng hạn mức và kiểm tra công nợ.")
    public ApiResponse<WithdrawResponse> withdraw(@Valid @RequestBody WithdrawRequest request,
            @RequestHeader("Idempotency-Key") String key) {
        return new ApiResponse<>(200, "Đã tiếp nhận yêu cầu rút tiền", funding.withdraw(request, key));
    }
}
