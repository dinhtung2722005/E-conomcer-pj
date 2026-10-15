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

import com.example.demo.ecommerce.dto.FlashSaleItemRequest;
import com.example.demo.ecommerce.dto.FlashSaleItemResponse;
import com.example.demo.ecommerce.service.FlashSaleItemService;
import com.example.demo.user.dto_request.ApiResponse;
import com.example.demo.user.dto_request.PageQuery;
import com.example.demo.user.dto_request.PageResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class FlashSaleItemController {

    private final FlashSaleItemService flashSaleItemService;

    @PostMapping("/flash-sales/{flashSaleId}/items")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<FlashSaleItemResponse> addItem(@PathVariable Long flashSaleId,
                                                      @Valid @RequestBody FlashSaleItemRequest request) {
        return new ApiResponse<>(201, "Thêm sản phẩm vào Flash Sale thành công",
                flashSaleItemService.addItemToFlashSale(flashSaleId, request));
    }

    @GetMapping("/flash-sales/{flashSaleId}/items")
    public ApiResponse<PageResponse<FlashSaleItemResponse>> getItems(@PathVariable Long flashSaleId, @Valid @ModelAttribute PageQuery pagination) {
        return new ApiResponse<>(200, "Thành công", flashSaleItemService.getItemsByFlashSaleId(flashSaleId, pagination));
    }

    @PutMapping("/flash-sale-items/{itemId}")
    public ApiResponse<FlashSaleItemResponse> updateItem(@PathVariable Long itemId,
                                                         @Valid @RequestBody FlashSaleItemRequest request) {
        return new ApiResponse<>(200, "Cập nhật thành công",
                flashSaleItemService.updateFlashSaleItem(itemId, request));
    }

    @DeleteMapping("/flash-sale-items/{itemId}")
    public ApiResponse<Void> removeItem(@PathVariable Long itemId) {
        flashSaleItemService.removeFlashSaleItem(itemId);
        return new ApiResponse<>(200, "Đã loại bỏ sản phẩm khỏi chương trình Flash Sale", null);
    }
}
