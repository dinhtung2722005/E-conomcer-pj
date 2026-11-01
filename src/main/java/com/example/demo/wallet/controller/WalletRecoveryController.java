package com.example.demo.wallet.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.example.demo.wallet.api.dto.WalletRecoveryDtos;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import com.example.demo.shared.web.ApiResponse;
import com.example.demo.wallet.api.dto.WalletRecoveryDtos.*;
import com.example.demo.wallet.service.WalletRecoveryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
@Tag(name = "Wallet - Recovery", description = "Khôi phục PIN qua email tài khoản đã xác minh. Mã xác minh không trả về qua API.")
@RestController @RequestMapping("/api/wallet") @PreAuthorize("isAuthenticated()") @RequiredArgsConstructor
public class WalletRecoveryController {
    private final WalletRecoveryService service;
    @Operation(summary = "Gửi mã khôi phục PIN qua email")
    @PostMapping("/pin/recovery") public ApiResponse<Start> start(){return new ApiResponse<>(200,"Đã gửi mã đến email khôi phục",service.requestReset());}
    @Operation(summary = "Xác nhận khôi phục PIN", description = "Mã xác minh có thời hạn và giới hạn số lần thử. Sau khi thành công phải đăng nhập lại.")
    @PostMapping("/pin/recovery/confirm") public ApiResponse<Void> confirm(@Valid @RequestBody Confirm request){service.resetPin(request);return new ApiResponse<>(200,"Đã khôi phục PIN; vui lòng đăng nhập lại",null);}
    @Operation(summary = "Gửi mã xác minh email khôi phục")
    @PostMapping("/security/recovery-email") public ApiResponse<Start> enroll(@Valid @RequestBody Enroll request){return new ApiResponse<>(200,"Đã gửi mã xác minh email",service.requestRecoveryEmail(request));}
    @Operation(summary = "Xác nhận email khôi phục")
    @PostMapping("/security/recovery-email/confirm") public ApiResponse<Void> confirmEmail(@Valid @RequestBody EmailConfirm request){service.confirmRecoveryEmail(request);return new ApiResponse<>(200,"Đã xác minh email khôi phục",null);}
}
