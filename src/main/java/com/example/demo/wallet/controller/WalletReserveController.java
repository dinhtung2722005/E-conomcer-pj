package com.example.demo.wallet.controller;
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
@RestController @RequiredArgsConstructor
public class WalletReserveController {
    private final WalletReserveService service;
    @PreAuthorize("isAuthenticated()") @GetMapping("/api/wallet/debts") public ApiResponse<PageResponse<WalletReserveService.Debt>> ownDebts(@Valid @ModelAttribute PageQuery page){return new ApiResponse<>(200,"Thành công",service.ownDebts(page));}
    @PreAuthorize("hasRole('ADMIN')") @GetMapping("/api/admin/wallet-ops/refund-reserve") public ApiResponse<WalletReserveService.Reserve> balance(@RequestParam(defaultValue="VND") String currency){return new ApiResponse<>(200,"Thành công",service.balance(currency));}
    @PreAuthorize("hasRole('ADMIN')") @PostMapping("/api/admin/wallet-ops/refund-reserve/fund") public ApiResponse<Long> fund(@Valid @RequestBody WalletReserveFundingRequest request,@RequestHeader("Idempotency-Key") String key){return new ApiResponse<>(200,"Đã cấp quỹ từ số dư CASH",service.fund(request,key));}
    @PreAuthorize("hasRole('ADMIN')") @GetMapping("/api/admin/wallet-ops/debts") public ApiResponse<PageResponse<WalletReserveService.Debt>> debts(@Valid @ModelAttribute PageQuery page){return new ApiResponse<>(200,"Thành công",service.list(page));}
    @PreAuthorize("isAuthenticated()") @PostMapping("/api/wallet/debts/repay") public ApiResponse<Long> repay(@Valid @RequestBody WalletReserveFundingRequest request,@RequestHeader("Idempotency-Key") String key){return new ApiResponse<>(200,"Đã trả công nợ từ CASH",service.repay(request,key));}
}
