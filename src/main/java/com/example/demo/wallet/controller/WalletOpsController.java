package com.example.demo.wallet.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.example.demo.wallet.api.AlertStatus;
import com.example.demo.wallet.api.RefundStatus;
import com.example.demo.wallet.api.TransactionStatus;
import com.example.demo.wallet.api.dto.WalletOpsDtos;
import com.example.demo.wallet.api.dto.WalletFreezeRequest;
import com.example.demo.wallet.api.dto.WalletAuditQuery;
import com.example.demo.wallet.api.dto.WalletAlertReviewRequest;
import com.example.demo.shared.web.PageResponse;
import com.example.demo.shared.web.PageQuery;
import com.example.demo.shared.web.ApiResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import com.example.demo.user.api.dto.*;
import com.example.demo.shared.web.*;
import com.example.demo.wallet.api.dto.*;
import com.example.demo.wallet.entity.*;
import com.example.demo.wallet.service.WalletOpsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
@Tag(name = "Admin - Wallet Operations", description = "Theo dõi và xử lý vận hành ví; yêu cầu quyền ADMIN.")
@RestController @RequestMapping("/api/admin/wallet-ops") @PreAuthorize("hasRole('ADMIN')") @RequiredArgsConstructor
public class WalletOpsController {
    private final WalletOpsService service;
    private <T> ApiResponse<T> ok(T value) { return new ApiResponse<>(200,"Thành công",value); }
    @Operation(summary = "Tổng quan vận hành ví")
    @GetMapping("/summary") public ApiResponse<WalletOpsDtos.Summary> summary(){return ok(service.summary());}
    @Operation(summary = "Danh sách giao dịch ngân hàng và bộ lọc")
    @GetMapping("/transfers") public ApiResponse<PageResponse<WalletOpsDtos.Transfer>> transfers(@Valid @ModelAttribute PageQuery page,@RequestParam(required=false) TransactionStatus status,@RequestParam(defaultValue="false") boolean overdue){return ok(service.transfers(page,status,overdue));}
    @Operation(summary = "Danh sách tiền đang tạm giữ")
    @GetMapping("/holds") public ApiResponse<PageResponse<WalletOpsDtos.Held>> holds(@Valid @ModelAttribute PageQuery page){return ok(service.holds(page));}
    @Operation(summary = "Danh sách hồ sơ hoàn tiền")
    @GetMapping("/refunds") public ApiResponse<PageResponse<WalletOpsDtos.Refund>> refunds(@Valid @ModelAttribute PageQuery page,@RequestParam(required=false) RefundStatus status){return ok(service.refunds(page,status));}
    @Operation(summary = "Danh sách quyết toán người bán")
    @GetMapping("/settlements") public ApiResponse<PageResponse<WalletOpsDtos.Settlement>> settlements(@Valid @ModelAttribute PageQuery page){return ok(service.settlements(page));}
    @Operation(summary = "Danh sách cảnh báo đối soát")
    @GetMapping("/alerts") public ApiResponse<PageResponse<WalletOpsDtos.Alert>> alerts(@Valid @ModelAttribute PageQuery page,@RequestParam(required=false) AlertStatus status){return ok(service.alerts(page,status));}
    @Operation(summary = "Lịch sử thao tác quản trị ví")
    @GetMapping("/audit") public ApiResponse<PageResponse<WalletOpsDtos.Audit>> audit(@Valid @ModelAttribute WalletAuditQuery query){return ok(service.audit(query));}
    @Operation(summary = "Lịch sử sự kiện bảo mật ví")
    @GetMapping("/security-events") public ApiResponse<PageResponse<WalletOpsDtos.SecurityEvent>> securityEvents(@Valid @ModelAttribute WalletAuditQuery query){return ok(service.securityEvents(query));}
    @Operation(summary = "Ghi nhận xử lý cảnh báo")
    @PostMapping("/alerts/{id}/review") public ApiResponse<WalletOpsDtos.Alert> review(@PathVariable Long id,@Valid @RequestBody WalletAlertReviewRequest request){return ok(service.review(id,request));}
    @Operation(summary = "Yêu cầu thử lại giao dịch ngân hàng")
    @PostMapping("/transfers/{id}/retry") public ApiResponse<Void> retryTransfer(@PathVariable Long id,@Valid @RequestBody WalletFreezeRequest request){service.retryTransfer(id,request);return ok(null);}
    @Operation(summary = "Yêu cầu thử lại quyết toán")
    @PostMapping("/settlements/{id}/retry") public ApiResponse<Void> retrySettlement(@PathVariable Long id,@Valid @RequestBody WalletFreezeRequest request){service.retrySettlement(id,request);return ok(null);}
}
