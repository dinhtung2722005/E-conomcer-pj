package com.example.demo.ecommerce.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.ecommerce.dto.InventoryRequest;
import com.example.demo.ecommerce.dto.InventoryResponse;
import com.example.demo.ecommerce.service.InventoryService;
import com.example.demo.user.dto_request.ApiResponse;
import com.example.demo.user.dto_request.PageQuery;
import com.example.demo.user.dto_request.PageResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/inventories")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @PostMapping("/add-stock")
    public ApiResponse<InventoryResponse> addStock(@Valid @RequestBody InventoryRequest request) {
        return new ApiResponse<>(200, "Nhập kho thành công", inventoryService.addStock(request));
    }

    @GetMapping
    public ApiResponse<PageResponse<InventoryResponse>> getAllInventories(@Valid @ModelAttribute PageQuery pagination) {
        return new ApiResponse<>(200, "Thành công", inventoryService.getAllInventories(pagination));
    }

    @GetMapping("/product/{productId}")
    public ApiResponse<InventoryResponse> getInventoryByProduct(@PathVariable Long productId) {
        return new ApiResponse<>(200, "Thành công", inventoryService.getInventoryByProductId(productId));
    }
}
