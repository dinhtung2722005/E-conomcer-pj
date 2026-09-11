    package com.example.demo.ecommerce.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.ecommerce.dto.FlashSaleItemRequest;
import com.example.demo.ecommerce.dto.FlashSaleItemResponse;
import com.example.demo.ecommerce.service.FlashSaleItemService;
import com.example.demo.user.dto_request.ApiResponse;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class FlashSaleItemController {

    @Autowired
    private FlashSaleItemService flashSaleItemService;

    @PostMapping("/flash-sales/{flashSaleId}/items")
    public ApiResponse<FlashSaleItemResponse> addItem(
            @PathVariable Long flashSaleId, 
            @RequestBody FlashSaleItemRequest request) {
        return new ApiResponse<>(200, "Thêm sản phẩm vào Flash Sale thành công", flashSaleItemService.addItemToFlashSale(flashSaleId, request));
    }

    @GetMapping("/flash-sales/{flashSaleId}/items")
    public ApiResponse<List<FlashSaleItemResponse>> getItems(@PathVariable Long flashSaleId) {
        return new ApiResponse<>(200, "Thành công", flashSaleItemService.getItemsByFlashSaleId(flashSaleId));
    }

    @PutMapping("/flash-sale-items/{itemId}")
    public ApiResponse<FlashSaleItemResponse> updateItem(
            @PathVariable Long itemId, 
            @RequestBody FlashSaleItemRequest request) {
        return new ApiResponse<>(200, "Cập nhật thành công", flashSaleItemService.updateFlashSaleItem(itemId, request));
    }

    @DeleteMapping("/flash-sale-items/{itemId}")
    public ApiResponse<String> removeItem(@PathVariable Long itemId) {
        flashSaleItemService.removeFlashSaleItem(itemId);
        return new ApiResponse<>(200, "Đã loại bỏ sản phẩm khỏi chương trình Flash Sale", null);
    }
}