package com.example.demo.ecommerce.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.ecommerce.api.dto.FlashSaleCustomerResponse;
import com.example.demo.ecommerce.service.CustomerFlashSaleService;
import com.example.demo.shared.web.ApiResponse;

import lombok.RequiredArgsConstructor;

@Tag(name = "Catalog - Flash Sales", description = "Flash sale công khai; không yêu cầu đăng nhập.")
@RestController
@RequestMapping("/api/flash-sales")
@RequiredArgsConstructor
public class CustomerFlashSaleController {

    private final CustomerFlashSaleService customerFlashSaleService;

    /** Trả về {@code result = null} khi hiện không có chương trình nào đang chạy. */
    @GetMapping("/current")
    @Operation(summary = "Xem flash sale hiện tại")
    public ApiResponse<FlashSaleCustomerResponse> getCurrentFlashSale() {
        FlashSaleCustomerResponse response = customerFlashSaleService.getCurrentFlashSale();
        if (response == null) {
            return new ApiResponse<>(200, "Hiện không có chương trình Flash Sale nào", null);
        }
        return new ApiResponse<>(200, "Thành công", response);
    }
}
