package com.example.demo.ecommerce.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.ecommerce.dto.InventoryRequest;
import com.example.demo.ecommerce.dto.InventoryResponse;
import com.example.demo.ecommerce.service.InventoryService;
import com.example.demo.user.dto_request.ApiResponse;

@RestController
@RequestMapping("/api/admin/inventories")
@PreAuthorize("hasRole('ADMIN')")
public class InventoryController {

    @Autowired
    private InventoryService inventoryService;

    @PostMapping("/add-stock")
    public ApiResponse<InventoryResponse> addStock(@RequestBody InventoryRequest request) {
        return new ApiResponse<>(200, "Nhập kho thành công", inventoryService.addStock(request));
    }

    @GetMapping
    public ApiResponse<List<InventoryResponse>> getAllInventories() {
        return new ApiResponse<>(200, "Thành công", inventoryService.getAllInventories());
    }

    @GetMapping("/product/{productId}")
    public ApiResponse<InventoryResponse> getInventoryByProduct(@PathVariable Long productId) {
        return new ApiResponse<>(200, "Thành công", inventoryService.getInventoryByProductId(productId));
    }
}