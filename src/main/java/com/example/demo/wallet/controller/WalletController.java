package com.example.demo.wallet.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.shared.web.ApiResponse;
import com.example.demo.shared.web.PageResponse;
import com.example.demo.wallet.api.dto.WalletFreezeRequest;
import com.example.demo.wallet.api.dto.WalletHistoryQuery;
import com.example.demo.wallet.api.dto.WalletHoldQuery;
import com.example.demo.wallet.api.dto.WalletHoldResponse;
import com.example.demo.wallet.api.dto.WalletResponse;
import com.example.demo.wallet.api.dto.WalletTransactionResponse;
import com.example.demo.wallet.api.dto.ChangePinRequest;
import com.example.demo.wallet.api.dto.SetupPinRequest;
import com.example.demo.wallet.api.dto.WalletSecurityResponse;
import com.example.demo.wallet.service.WalletPinService;
import com.example.demo.wallet.service.WalletService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Every customer operation resolves the owner through WalletService and the authenticated principal. */
@Tag(name = "Wallet - Account & Security", description = "Số dư CASH/BONUS, lịch sử và bảo mật ví của tài khoản đang đăng nhập.")
@RestController
@RequestMapping("/api/wallet")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class WalletController {
    private final WalletService walletService;
    private final WalletPinService pinService;

    @GetMapping("/security")
    @Operation(summary = "Xem trạng thái bảo mật ví")
    public ApiResponse<WalletSecurityResponse> getSecurity() {
        return new ApiResponse<>(200, "Thành công", pinService.getMySecurity());
    }

    @PostMapping("/pin")
    @Operation(summary = "Thiết lập mã PIN thanh toán 6 số")
    public ApiResponse<WalletSecurityResponse> setupPin(
            @Valid @RequestBody SetupPinRequest request) {
        return new ApiResponse<>(200, "Thiết lập PIN thành công", pinService.setupPin(request));
    }

    @PutMapping("/pin")
    @Operation(summary = "Đổi mã PIN thanh toán")
    public ApiResponse<WalletSecurityResponse> changePin(
            @Valid @RequestBody ChangePinRequest request) {
        return new ApiResponse<>(200, "Đổi PIN thành công", pinService.changePin(request));
    }

    /** Idempotent initialization returns HTTP 200 for both new and existing wallets. */
    @PostMapping
    @Operation(summary = "Khởi tạo ví của tôi", description = "Thao tác idempotent; luôn HTTP 200 cho ví mới hoặc ví đã tồn tại.")
    public ApiResponse<WalletResponse> initializeMyWallet() {
        return new ApiResponse<>(200, "Khởi tạo ví thành công", walletService.initializeMyWallet());
    }

    @GetMapping
    @Operation(summary = "Xem số dư và trạng thái ví")
    public ApiResponse<WalletResponse> getMyWallet() {
        return new ApiResponse<>(200, "Thành công", walletService.getMyWallet());
    }

    @GetMapping("/transactions")
    @Operation(summary = "Tra cứu lịch sử giao dịch")
    public ApiResponse<PageResponse<WalletTransactionResponse>> getHistory(@Valid @ModelAttribute WalletHistoryQuery query) {
        return new ApiResponse<>(200, "Thành công", walletService.getHistory(query));
    }

    @GetMapping("/transactions/{id}")
    @Operation(summary = "Chi tiết giao dịch của tôi")
    public ApiResponse<WalletTransactionResponse> getTransaction(@PathVariable Long id) {
        return new ApiResponse<>(200, "Thành công", walletService.getTransaction(id));
    }

    @GetMapping("/holds")
    @Operation(summary = "Danh sách khoản tiền tạm giữ")
    public ApiResponse<PageResponse<WalletHoldResponse>> getHolds(@Valid @ModelAttribute WalletHoldQuery query) {
        return new ApiResponse<>(200, "Thành công", walletService.getHolds(query));
    }

    @PostMapping("/freeze")
    @Operation(summary = "Khóa chi tiêu ví khẩn cấp", description = "Ví vẫn có thể nhận tiền, nhưng không thể thanh toán hoặc rút; mở khóa do quản trị xử lý.")
    public ApiResponse<WalletResponse> freezeMyWallet(@Valid @RequestBody WalletFreezeRequest request) {
        return new ApiResponse<>(200, "Đã khóa ví", walletService.freezeMyWallet(request));
    }
}
