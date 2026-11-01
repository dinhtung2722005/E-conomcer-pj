package com.example.demo.wallet.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.example.demo.wallet.api.dto.WalletReconciliationResponse;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.shared.web.ApiResponse;
import com.example.demo.wallet.api.dto.WalletFreezeRequest;
import com.example.demo.wallet.api.dto.WalletResponse;
import com.example.demo.wallet.service.WalletService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Admin - Wallets", description = "Đối soát và khóa/mở khóa ví; yêu cầu quyền ADMIN.")
@RestController
@RequestMapping("/api/admin/wallets")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminWalletController {
    private final WalletService walletService;
    private final com.example.demo.wallet.service.WalletReconciliationService reconciliation;

    @org.springframework.web.bind.annotation.GetMapping("/{id}/reconciliation")
    @Operation(summary = "Kiểm tra đối soát ví")
    public ApiResponse<com.example.demo.wallet.api.dto.WalletReconciliationResponse> reconcile(@PathVariable Long id) {
        return new ApiResponse<>(200, "Thành công", reconciliation.inspectAsAdmin(id));
    }

    @PostMapping("/{id}/freeze")
    @Operation(summary = "Khóa ví theo quyết định quản trị")
    public ApiResponse<WalletResponse> freezeWallet(@PathVariable Long id, @Valid @RequestBody WalletFreezeRequest request) {
        return new ApiResponse<>(200, "Đã khóa ví", walletService.freezeWallet(id, request));
    }

    @PostMapping("/{id}/unfreeze")
    @Operation(summary = "Mở khóa ví theo quyết định quản trị")
    public ApiResponse<WalletResponse> unfreezeWallet(@PathVariable Long id, @Valid @RequestBody WalletFreezeRequest request) {
        return new ApiResponse<>(200, "Đã mở khóa ví", walletService.unfreezeWallet(id, request));
    }
}
