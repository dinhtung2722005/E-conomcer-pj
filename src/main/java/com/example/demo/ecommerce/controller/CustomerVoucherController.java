package com.example.demo.ecommerce.controller;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.ecommerce.api.dto.VoucherCustomerResponse;
import com.example.demo.ecommerce.service.CustomerVoucherService;
import com.example.demo.shared.web.ApiResponse;
import com.example.demo.user.api.UserAccountsApi;

import lombok.RequiredArgsConstructor;

/**
 * Voucher dành cho khách.
 *
 * <p>Các thao tác trên ví voucher lấy {@code userId} từ JWT — trước đây nhận từ
 * query string nên người dùng có thể lưu/xem voucher của bất kỳ tài khoản nào.
 */
@RestController
@RequestMapping("/api/vouchers")
@RequiredArgsConstructor
public class CustomerVoucherController {

    private final CustomerVoucherService customerVoucherService;
    private final UserAccountsApi currentUserService;

    /** Danh sách voucher công khai đang có hiệu lực. */
    @GetMapping
    public ApiResponse<List<VoucherCustomerResponse>> getAvailableVouchers() {
        return new ApiResponse<>(200, "Thành công", customerVoucherService.getAvailableVouchers());
    }

    @GetMapping("/validate")
    public ApiResponse<VoucherCustomerResponse> validateVoucher(@RequestParam String code,
                                                               @RequestParam BigDecimal orderTotal) {
        return new ApiResponse<>(200, "Mã giảm giá hợp lệ",
                customerVoucherService.validateVoucher(code, orderTotal));
    }

    @PostMapping("/save")
    public ApiResponse<Void> saveVoucher(@RequestParam String code) {
        customerVoucherService.saveVoucherToWallet(currentUserService.getCurrentUserId(), code);
        return new ApiResponse<>(200, "Đã lưu mã giảm giá vào ví thành công", null);
    }

    @GetMapping("/my-vouchers")
    public ApiResponse<List<VoucherCustomerResponse>> getMyVouchers() {
        return new ApiResponse<>(200, "Thành công",
                customerVoucherService.getMyVouchers(currentUserService.getCurrentUserId()));
    }
}
