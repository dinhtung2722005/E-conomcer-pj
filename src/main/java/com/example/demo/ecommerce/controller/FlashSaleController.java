package com.example.demo.ecommerce.controller;

import com.example.demo.user.dto_request.ApiResponse;
import com.example.demo.ecommerce.dto.FlashSaleRequest;
import com.example.demo.ecommerce.dto.FlashSaleResponse;
import com.example.demo.ecommerce.service.FlashSaleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/flash-sales")
@PreAuthorize("hasRole('ADMIN')")
public class FlashSaleController {

    @Autowired
    private FlashSaleService flashSaleService;

    @PostMapping
    public ApiResponse<FlashSaleResponse> createFlashSale(@RequestBody FlashSaleRequest request) {
        return new ApiResponse<>(200, "Tạo Flash Sale thành công", flashSaleService.createFlashSale(request));
    }

    @GetMapping
    public ApiResponse<List<FlashSaleResponse>> getAllFlashSales() {
        return new ApiResponse<>(200, "Thành công", flashSaleService.getAllFlashSales());
    }

    @GetMapping("/{id}")
    public ApiResponse<FlashSaleResponse> getFlashSaleById(@PathVariable Long id) {
        return new ApiResponse<>(200, "Thành công", flashSaleService.getFlashSaleById(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<FlashSaleResponse> updateFlashSale(
            @PathVariable Long id, 
            @RequestBody FlashSaleRequest request) {
        return new ApiResponse<>(200, "Cập nhật thành công", flashSaleService.updateFlashSale(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<String> deleteFlashSale(@PathVariable Long id) {
        flashSaleService.deleteFlashSale(id);
        return new ApiResponse<>(200, "Đã xóa chương trình Flash Sale", null);
    }
}