package com.example.demo.wallet.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.user.dto_request.ApiResponse;
import com.example.demo.user.dto_request.PageResponse;
import com.example.demo.wallet.dto.WalletFreezeRequest;
import com.example.demo.wallet.dto.WalletHistoryQuery;
import com.example.demo.wallet.dto.WalletHoldQuery;
import com.example.demo.wallet.dto.WalletHoldResponse;
import com.example.demo.wallet.dto.WalletResponse;
import com.example.demo.wallet.dto.WalletTransactionResponse;
import com.example.demo.wallet.dto.ChangePinRequest;
import com.example.demo.wallet.dto.SetupPinRequest;
import com.example.demo.wallet.dto.WalletSecurityResponse;
import com.example.demo.wallet.service.WalletPinService;
import com.example.demo.wallet.service.WalletService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Every customer operation resolves the owner through WalletService and the authenticated principal. */
@RestController
@RequestMapping("/api/wallet")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class WalletController {
    private final WalletService walletService;
    private final WalletPinService pinService;

    @GetMapping("/security")
    public ApiResponse<WalletSecurityResponse> getSecurity() {
        return new ApiResponse<>(200, "Thành công", pinService.getMySecurity());
    }

    @PostMapping("/pin")
    public ApiResponse<WalletSecurityResponse> setupPin(
            @Valid @RequestBody SetupPinRequest request) {
        return new ApiResponse<>(200, "Thiết lập PIN thành công", pinService.setupPin(request));
    }

    @PutMapping("/pin")
    public ApiResponse<WalletSecurityResponse> changePin(
            @Valid @RequestBody ChangePinRequest request) {
        return new ApiResponse<>(200, "Đổi PIN thành công", pinService.changePin(request));
    }

    /** Idempotent initialization returns HTTP 200 for both new and existing wallets. */
    @PostMapping
    public ApiResponse<WalletResponse> initializeMyWallet() {
        return new ApiResponse<>(200, "Khởi tạo ví thành công", walletService.initializeMyWallet());
    }

    @GetMapping
    public ApiResponse<WalletResponse> getMyWallet() {
        return new ApiResponse<>(200, "Thành công", walletService.getMyWallet());
    }

    @GetMapping("/transactions")
    public ApiResponse<PageResponse<WalletTransactionResponse>> getHistory(@Valid @ModelAttribute WalletHistoryQuery query) {
        return new ApiResponse<>(200, "Thành công", walletService.getHistory(query));
    }

    @GetMapping("/transactions/{id}")
    public ApiResponse<WalletTransactionResponse> getTransaction(@PathVariable Long id) {
        return new ApiResponse<>(200, "Thành công", walletService.getTransaction(id));
    }

    @GetMapping("/holds")
    public ApiResponse<PageResponse<WalletHoldResponse>> getHolds(@Valid @ModelAttribute WalletHoldQuery query) {
        return new ApiResponse<>(200, "Thành công", walletService.getHolds(query));
    }

    @PostMapping("/freeze")
    public ApiResponse<WalletResponse> freezeMyWallet(@Valid @RequestBody WalletFreezeRequest request) {
        return new ApiResponse<>(200, "Đã khóa ví", walletService.freezeMyWallet(request));
    }
}
