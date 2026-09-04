package com.example.demo.ecommerce.controller;

import com.example.demo.ecommerce.dto.ProductRequest;
import com.example.demo.ecommerce.dto.ProductResponse;
import com.example.demo.ecommerce.service.ProductService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.example.demo.user.dto_request.ApiResponse;
@RestController
@RequestMapping("/api/admin/products")
@PreAuthorize("hasRole('ADMIN')")
public class ProductController {
    private final ProductService productService;
    public ProductController(ProductService productService){
        this.productService = productService;
    }
    @PostMapping
    public ApiResponse<ProductResponse> createProduct(@RequestBody ProductRequest request) {
        ProductResponse product = productService.createProduct(request);
        ApiResponse<ProductResponse> response = new ApiResponse<>();
        response.setCode(200);
        response.setMessage("Tao product thanh cong");
        response.setResult(product);
        return response;
    }

    
}
