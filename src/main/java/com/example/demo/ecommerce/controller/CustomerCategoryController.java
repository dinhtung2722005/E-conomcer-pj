package com.example.demo.ecommerce.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.ecommerce.api.dto.CategoryCustomerResponse;
import com.example.demo.ecommerce.service.CustomerCategoryService;
import com.example.demo.shared.web.ApiResponse;

import lombok.RequiredArgsConstructor;

@Tag(name = "Catalog - Categories", description = "Danh mục công khai; không yêu cầu đăng nhập.")
@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CustomerCategoryController {

    private final CustomerCategoryService customerCategoryService;

    @GetMapping
    @Operation(summary = "Danh sách danh mục công khai")
    public ApiResponse<List<CategoryCustomerResponse>> getAllCategories() {
        return new ApiResponse<>(200, "Thành công", customerCategoryService.getAllCategories());
    }
}
