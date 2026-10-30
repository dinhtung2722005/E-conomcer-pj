package com.example.demo.wallet.controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import com.example.demo.user.dto_request.*;
import com.example.demo.wallet.dto.*;
import com.example.demo.wallet.entity.*;
import com.example.demo.wallet.service.WalletOpsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
@RestController @RequestMapping("/api/admin/wallet-ops") @PreAuthorize("hasRole('ADMIN')") @RequiredArgsConstructor
public class WalletOpsController {
    private final WalletOpsService service;
    private <T> ApiResponse<T> ok(T value) { return new ApiResponse<>(200,"Thành công",value); }
    @GetMapping("/summary") public ApiResponse<WalletOpsDtos.Summary> summary(){return ok(service.summary());}
    @GetMapping("/transfers") public ApiResponse<PageResponse<WalletOpsDtos.Transfer>> transfers(@Valid @ModelAttribute PageQuery page,@RequestParam(required=false) TransactionStatus status,@RequestParam(defaultValue="false") boolean overdue){return ok(service.transfers(page,status,overdue));}
    @GetMapping("/holds") public ApiResponse<PageResponse<WalletOpsDtos.Held>> holds(@Valid @ModelAttribute PageQuery page){return ok(service.holds(page));}
    @GetMapping("/refunds") public ApiResponse<PageResponse<WalletOpsDtos.Refund>> refunds(@Valid @ModelAttribute PageQuery page,@RequestParam(required=false) WalletRefundCase.Status status){return ok(service.refunds(page,status));}
    @GetMapping("/settlements") public ApiResponse<PageResponse<WalletOpsDtos.Settlement>> settlements(@Valid @ModelAttribute PageQuery page){return ok(service.settlements(page));}
    @GetMapping("/alerts") public ApiResponse<PageResponse<WalletOpsDtos.Alert>> alerts(@Valid @ModelAttribute PageQuery page,@RequestParam(required=false) WalletAlert.Status status){return ok(service.alerts(page,status));}
    @GetMapping("/audit") public ApiResponse<PageResponse<WalletOpsDtos.Audit>> audit(@Valid @ModelAttribute PageQuery page){return ok(service.audit(page));}
    @PostMapping("/alerts/{id}/review") public ApiResponse<WalletOpsDtos.Alert> review(@PathVariable Long id,@Valid @RequestBody WalletAlertReviewRequest request){return ok(service.review(id,request));}
    @PostMapping("/transfers/{id}/retry") public ApiResponse<Void> retryTransfer(@PathVariable Long id,@Valid @RequestBody WalletFreezeRequest request){service.retryTransfer(id,request);return ok(null);}
    @PostMapping("/settlements/{id}/retry") public ApiResponse<Void> retrySettlement(@PathVariable Long id,@Valid @RequestBody WalletFreezeRequest request){service.retrySettlement(id,request);return ok(null);}
}
