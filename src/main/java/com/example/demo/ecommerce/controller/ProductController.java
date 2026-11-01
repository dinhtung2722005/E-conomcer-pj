package com.example.demo.ecommerce.controller;

import org.springframework.beans.factory.annotation.Autowired;
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

import com.example.demo.ecommerce.api.dto.ProductRequest;
import com.example.demo.ecommerce.api.dto.ProductResponse;
import com.example.demo.ecommerce.service.ProductService;
import com.example.demo.shared.web.ApiResponse;
import com.example.demo.shared.web.PageQuery;
import com.example.demo.shared.web.PageResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Quản trị sản phẩm. Trước đây chỉ có duy nhất endpoint tạo mới, còn
 * {@code ProductService} đã có sẵn các method đọc/sửa/xóa nhưng không được expose.
 */
@RestController
@RequestMapping("/api/admin/products")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class ProductController {
    @Autowired 
    private ProductService productService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProductResponse> createProduct(@Valid @RequestBody ProductRequest request) {
        return new ApiResponse<>(201, "Tạo sản phẩm thành công", productService.createProduct(request));
    }

    @GetMapping
    public ApiResponse<PageResponse<ProductResponse>> getAllProducts(@Valid @ModelAttribute PageQuery pagination) {
        return new ApiResponse<>(200, "Thành công", productService.getAllProducts(pagination));
    }

    @GetMapping("/{id}")
    public ApiResponse<ProductResponse> getProductById(@PathVariable Long id) {
        return new ApiResponse<>(200, "Thành công", productService.getProductById(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<ProductResponse> updateProduct(@PathVariable Long id,
                                                      @Valid @RequestBody ProductRequest request) {
        return new ApiResponse<>(200, "Cập nhật sản phẩm thành công", productService.updateProduct(id, request));
    }

    /** Sản phẩm đã phát sinh đơn hàng sẽ được chuyển sang INACTIVE thay vì xóa cứng. */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return new ApiResponse<>(200, "Đã ngừng kinh doanh sản phẩm", null);
    }
}
