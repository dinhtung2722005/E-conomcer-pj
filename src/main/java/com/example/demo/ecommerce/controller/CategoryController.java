package com.example.demo.ecommerce.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.ecommerce.dto.CategoryRequest;
import com.example.demo.ecommerce.dto.CategoryResponse;
import com.example.demo.ecommerce.service.CategoryService;
import com.example.demo.user.dto_request.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/categories")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CategoryResponse> createCategory(@Valid @RequestBody CategoryRequest request) {
        return new ApiResponse<>(201, "Tạo danh mục thành công", categoryService.create(request));
    }

    @GetMapping
    public ApiResponse<List<CategoryResponse>> getAllCategories() {
        return new ApiResponse<>(200, "Thành công", categoryService.getAllCategories());
    }

    @GetMapping("/{id}")
    public ApiResponse<CategoryResponse> getCategoryById(@PathVariable Long id) {
        return new ApiResponse<>(200, "Thành công", categoryService.getCategoryById(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<CategoryResponse> updateCategory(@PathVariable Long id,
                                                        @Valid @RequestBody CategoryRequest request) {
        return new ApiResponse<>(200, "Cập nhật danh mục thành công", categoryService.updateCategory(id, request));
    }

    /** Trả về cùng định dạng {@link ApiResponse} như mọi endpoint khác. */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteCategory(@PathVariable Long id) {
        categoryService.deleteCategory(id);
        return new ApiResponse<>(200, "Xóa danh mục thành công", null);
    }
}
