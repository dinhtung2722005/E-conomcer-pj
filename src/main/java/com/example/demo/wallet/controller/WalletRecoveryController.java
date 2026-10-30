package com.example.demo.wallet.controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import com.example.demo.user.dto_request.ApiResponse;
import com.example.demo.wallet.dto.WalletRecoveryDtos.*;
import com.example.demo.wallet.service.WalletRecoveryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
@RestController @RequestMapping("/api/wallet") @PreAuthorize("isAuthenticated()") @RequiredArgsConstructor
public class WalletRecoveryController {
    private final WalletRecoveryService service;
    @PostMapping("/pin/recovery") public ApiResponse<Start> start(){return new ApiResponse<>(200,"Đã gửi mã đến email khôi phục",service.requestReset());}
    @PostMapping("/pin/recovery/confirm") public ApiResponse<Void> confirm(@Valid @RequestBody Confirm request){service.resetPin(request);return new ApiResponse<>(200,"Đã khôi phục PIN; vui lòng đăng nhập lại",null);}
    @PostMapping("/security/recovery-email") public ApiResponse<Start> enroll(@Valid @RequestBody Enroll request){return new ApiResponse<>(200,"Đã gửi mã xác minh email",service.requestRecoveryEmail(request));}
    @PostMapping("/security/recovery-email/confirm") public ApiResponse<Void> confirmEmail(@Valid @RequestBody EmailConfirm request){service.confirmRecoveryEmail(request);return new ApiResponse<>(200,"Đã xác minh email khôi phục",null);}
}
