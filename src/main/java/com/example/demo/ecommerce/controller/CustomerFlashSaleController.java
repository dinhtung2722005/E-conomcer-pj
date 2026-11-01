package com.example.demo.ecommerce.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.ecommerce.api.dto.FlashSaleCustomerResponse;
import com.example.demo.ecommerce.service.CustomerFlashSaleService;
import com.example.demo.shared.web.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/flash-sales")
@RequiredArgsConstructor
public class CustomerFlashSaleController {

    private final CustomerFlashSaleService customerFlashSaleService;

    /** Trả về {@code result = null} khi hiện không có chương trình nào đang chạy. */
    @GetMapping("/current")
    public ApiResponse<FlashSaleCustomerResponse> getCurrentFlashSale() {
        FlashSaleCustomerResponse response = customerFlashSaleService.getCurrentFlashSale();
        if (response == null) {
            return new ApiResponse<>(200, "Hiện không có chương trình Flash Sale nào", null);
        }
        return new ApiResponse<>(200, "Thành công", response);
    }
}
