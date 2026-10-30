package com.example.demo.wallet.controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import com.example.demo.user.dto_request.*;
import com.example.demo.wallet.service.WalletNotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
@RestController @RequestMapping("/api/wallet/notifications") @PreAuthorize("isAuthenticated()") @RequiredArgsConstructor
public class WalletNotificationController {
    private final WalletNotificationService service;
    @GetMapping public ApiResponse<PageResponse<WalletNotificationService.Response>> list(@Valid @ModelAttribute PageQuery page){return new ApiResponse<>(200,"Thành công",service.list(page));}
    @PostMapping("/{id}/read") public ApiResponse<WalletNotificationService.Response> read(@PathVariable Long id){return new ApiResponse<>(200,"Thành công",service.markRead(id));}
}
