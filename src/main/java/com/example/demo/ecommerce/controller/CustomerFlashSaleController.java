package com.example.demo.ecommerce.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.ecommerce.dto.FlashSaleCustomerResponse;
import com.example.demo.ecommerce.service.CustomerFlashSaleService;
import com.example.demo.user.dto_request.ApiResponse;

@RestController
@RequestMapping("/api/flash-sales")
public class CustomerFlashSaleController {

    @Autowired
    private CustomerFlashSaleService customerFlashSaleService;

    @GetMapping("/current")
    public ApiResponse<FlashSaleCustomerResponse> getCurrentFlashSale() {
        FlashSaleCustomerResponse response = customerFlashSaleService.getCurrentFlashSale();
        if (response == null) {
            return new ApiResponse<>(200, "Hiện không có chương trình Flash Sale nào", null);
        }
        return new ApiResponse<>(200, "Thành công", response);
    }
}