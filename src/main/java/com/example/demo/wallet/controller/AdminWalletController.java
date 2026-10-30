package com.example.demo.wallet.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.user.dto_request.ApiResponse;
import com.example.demo.wallet.dto.WalletFreezeRequest;
import com.example.demo.wallet.dto.WalletResponse;
import com.example.demo.wallet.service.WalletService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/wallets")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminWalletController {
    private final WalletService walletService;
    private final com.example.demo.wallet.service.WalletReconciliationService reconciliation;

    @org.springframework.web.bind.annotation.GetMapping("/{id}/reconciliation")
    public ApiResponse<com.example.demo.wallet.dto.WalletReconciliationResponse> reconcile(@PathVariable Long id) {
        return new ApiResponse<>(200, "Thành công", reconciliation.inspectAsAdmin(id));
    }

    @PostMapping("/{id}/freeze")
    public ApiResponse<WalletResponse> freezeWallet(@PathVariable Long id, @Valid @RequestBody WalletFreezeRequest request) {
        return new ApiResponse<>(200, "Đã khóa ví", walletService.freezeWallet(id, request));
    }

    @PostMapping("/{id}/unfreeze")
    public ApiResponse<WalletResponse> unfreezeWallet(@PathVariable Long id, @Valid @RequestBody WalletFreezeRequest request) {
        return new ApiResponse<>(200, "Đã mở khóa ví", walletService.unfreezeWallet(id, request));
    }
}
