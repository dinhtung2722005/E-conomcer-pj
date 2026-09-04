package com.example.demo.ecommerce.controller;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.ecommerce.dto.VoucherCustomerResponse;
import com.example.demo.ecommerce.service.CustomerVoucherService;
import com.example.demo.user.dto_request.ApiResponse;

@RestController
@RequestMapping("/api/vouchers")
public class CustomerVoucherController {

    @Autowired
    private CustomerVoucherService customerVoucherService;

    @GetMapping
    public ApiResponse<List<VoucherCustomerResponse>> getAvailableVouchers() {
        return new ApiResponse<>(200, "Thành công", customerVoucherService.getAvailableVouchers());
    }

    @GetMapping("/validate")
    public ApiResponse<VoucherCustomerResponse> validateVoucher(
            @RequestParam String code,
            @RequestParam BigDecimal orderTotal) {
        return new ApiResponse<>(200, "Mã giảm giá hợp lệ", 
                customerVoucherService.validateVoucher(code, orderTotal));
    }

    
    @PostMapping("/save")
    public ApiResponse<String> saveVoucher(
            @RequestParam Long userId, 
            @RequestParam String code) {
        customerVoucherService.saveVoucherToWallet(userId, code);
        return new ApiResponse<>(200, "Đã lưu mã giảm giá vào ví thành công", null);
    } 
    @GetMapping("/my-vouchers")
    public ApiResponse<List<VoucherCustomerResponse>> getMyVouchers(
            @RequestParam Long userId) {
        return new ApiResponse<>(200, "Thành công", 
                customerVoucherService.getMyVouchers(userId));
    }
}