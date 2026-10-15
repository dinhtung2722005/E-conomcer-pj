package com.example.demo.ecommerce.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.ecommerce.dto.FlashSaleRequest;
import com.example.demo.ecommerce.dto.FlashSaleResponse;
import com.example.demo.ecommerce.service.FlashSaleService;
import com.example.demo.user.dto_request.ApiResponse;
import com.example.demo.user.dto_request.PageQuery;
import com.example.demo.user.dto_request.PageResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/flash-sales")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class FlashSaleController {

    private final FlashSaleService flashSaleService;

    /** Trạng thái được suy ra từ khung thời gian, không lấy từ request. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<FlashSaleResponse> createFlashSale(@Valid @RequestBody FlashSaleRequest request) {
        return new ApiResponse<>(201, "Tạo Flash Sale thành công",
                flashSaleService.createFlashSale(request));
    }

    @GetMapping
    public ApiResponse<PageResponse<FlashSaleResponse>> getAllFlashSales(@Valid @ModelAttribute PageQuery pagination) {
        return new ApiResponse<>(200, "Thành công", flashSaleService.getAllFlashSales(pagination));
    }

    @GetMapping("/active")
    public ApiResponse<PageResponse<FlashSaleResponse>> getActiveFlashSales(@Valid @ModelAttribute PageQuery pagination) {
        return new ApiResponse<>(200, "Thành công", flashSaleService.getActiveFlashSales(pagination));
    }

    @GetMapping("/{id}")
    public ApiResponse<FlashSaleResponse> getFlashSaleById(@PathVariable Long id) {
        return new ApiResponse<>(200, "Thành công", flashSaleService.getFlashSaleById(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<FlashSaleResponse> updateFlashSale(@PathVariable Long id,
                                                          @Valid @RequestBody FlashSaleRequest request) {
        return new ApiResponse<>(200, "Cập nhật thành công", flashSaleService.updateFlashSale(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteFlashSale(@PathVariable Long id) {
        flashSaleService.deleteFlashSale(id);
        return new ApiResponse<>(200, "Đã xóa chương trình Flash Sale", null);
    }
}
