package com.example.demo.wallet.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.example.demo.shared.web.PageResponse;
import com.example.demo.shared.web.PageQuery;
import com.example.demo.shared.web.ApiResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import com.example.demo.user.api.dto.*;
import com.example.demo.shared.web.*;
import com.example.demo.wallet.api.dto.WalletReserveFundingRequest;
import com.example.demo.wallet.service.WalletReserveService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
@Tag(name = "Wallet - Reserve & Debts", description = "Quỹ hoàn tiền do ADMIN quản lý; người dùng chỉ xem và trả công nợ của mình.")
@RestController @RequiredArgsConstructor
public class WalletReserveController {
    private final WalletReserveService service;
    @Operation(summary = "Xem công nợ ví của tôi")
    @PreAuthorize("isAuthenticated()") @GetMapping("/api/wallet/debts") public ApiResponse<PageResponse<WalletReserveService.Debt>> ownDebts(@Valid @ModelAttribute PageQuery page){return new ApiResponse<>(200,"Thành công",service.ownDebts(page));}
    @Operation(summary = "Xem số dư quỹ hoàn tiền (ADMIN)")
    @PreAuthorize("hasRole('ADMIN')") @GetMapping("/api/admin/wallet-ops/refund-reserve") public ApiResponse<WalletReserveService.Reserve> balance(@RequestParam(defaultValue="VND") String currency){return new ApiResponse<>(200,"Thành công",service.balance(currency));}
    @Operation(summary = "Cấp vốn cho quỹ hoàn tiền (ADMIN)", description = "Trích CASH khả dụng từ ví quản trị; yêu cầu PIN và Idempotency-Key.")
    @PreAuthorize("hasRole('ADMIN')") @PostMapping("/api/admin/wallet-ops/refund-reserve/fund") public ApiResponse<Long> fund(@Valid @RequestBody WalletReserveFundingRequest request,@RequestHeader("Idempotency-Key") String key){return new ApiResponse<>(200,"Đã cấp quỹ từ số dư CASH",service.fund(request,key));}
    @Operation(summary = "Danh sách công nợ ví (ADMIN)")
    @PreAuthorize("hasRole('ADMIN')") @GetMapping("/api/admin/wallet-ops/debts") public ApiResponse<PageResponse<WalletReserveService.Debt>> debts(@Valid @ModelAttribute PageQuery page){return new ApiResponse<>(200,"Thành công",service.list(page));}
    @Operation(summary = "Trả công nợ của tôi", description = "Dùng CASH khả dụng; yêu cầu PIN và Idempotency-Key.")
    @PreAuthorize("isAuthenticated()") @PostMapping("/api/wallet/debts/repay") public ApiResponse<Long> repay(@Valid @RequestBody WalletReserveFundingRequest request,@RequestHeader("Idempotency-Key") String key){return new ApiResponse<>(200,"Đã trả công nợ từ CASH",service.repay(request,key));}
}
