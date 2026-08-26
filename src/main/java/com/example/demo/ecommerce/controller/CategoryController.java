package com.example.demo.ecommerce.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.ecommerce.dto.CategoryRequest;
import com.example.demo.ecommerce.dto.CategoryResponse;
import com.example.demo.ecommerce.service.CategoryService;
import com.example.demo.user.dto_request.ApiResponse;

@RestController
@RequestMapping("/api/admin/categories")
@PreAuthorize("hasRole('ADMIN')")
public class CategoryController {

    @Autowired
    private CategoryService categoryService;

    @PostMapping
    public ApiResponse<CategoryResponse> createCategory(@RequestBody CategoryRequest request) {
        ApiResponse<CategoryResponse> response = new ApiResponse<>(200,null,categoryService.create(request));
        return response;
    }

    @GetMapping
    public ApiResponse<List<CategoryResponse>> getAllCategories() {
       ApiResponse<List<CategoryResponse>> response = new ApiResponse<>(200,null,categoryService.getAllCategories());
       return  response;
    }

    @GetMapping("/{id}")
    public ApiResponse<CategoryResponse> getCategoryById(@PathVariable Long id) {
        ApiResponse<CategoryResponse> response = new ApiResponse<>(200,null, categoryService.getCategoryById(id));
        return response;
    }

    @PutMapping("/{id}")
    public ApiResponse<CategoryResponse> updateCategory(
            @PathVariable Long id, 
            @RequestBody CategoryRequest request) {
           ApiResponse<CategoryResponse>response = new ApiResponse<>(200,null, categoryService.updateCategory(id, request));
           return response;
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteCategory(@PathVariable Long id) {
        categoryService.deleteCategory(id);
        return ResponseEntity.ok("Xóa danh mục thành công!");
    }
}