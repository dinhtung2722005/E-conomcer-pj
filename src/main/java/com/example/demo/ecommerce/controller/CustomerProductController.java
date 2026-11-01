package com.example.demo.ecommerce.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.ecommerce.api.dto.ProductCustomerResponse;
import com.example.demo.ecommerce.service.CustomerProductService;
import com.example.demo.shared.web.ApiResponse;
import com.example.demo.shared.web.PageResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class CustomerProductController {

    private final CustomerProductService customerProductService;

    @GetMapping
    public ApiResponse<PageResponse<ProductCustomerResponse>> getAllProducts(
            @RequestParam(value = "page", defaultValue = "0", required = false) int page,
            @RequestParam(value = "size", defaultValue = "10", required = false) int size,
            @RequestParam(value = "sortBy", defaultValue = "id", required = false) String sortBy,
            @RequestParam(value = "sortDir", defaultValue = "desc", required = false) String sortDir,
            @RequestParam(value = "categoryId", required = false) Long categoryId) {
        return new ApiResponse<>(200, "Thành công",
                customerProductService.getAllActiveProducts(page, size, sortBy, sortDir, categoryId));
    }

    @GetMapping("/{id}")
    public ApiResponse<ProductCustomerResponse> getProductDetail(@PathVariable Long id) {
        return new ApiResponse<>(200, "Thành công", customerProductService.getProductDetail(id));
    }
}
