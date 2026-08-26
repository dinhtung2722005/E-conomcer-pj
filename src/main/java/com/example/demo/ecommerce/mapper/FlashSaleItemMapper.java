package com.example.demo.ecommerce.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.example.demo.ecommerce.dto.FlashSaleItemResponse;
import com.example.demo.ecommerce.entity.FlashSaleItem;

@Mapper(componentModel = "spring")
public interface FlashSaleItemMapper {

    @Mapping(source = "flashSale.id", target = "flashSaleId")
    @Mapping(source = "product.id", target = "productId")
    @Mapping(source = "product.name", target = "productName")
    FlashSaleItemResponse toResponse(FlashSaleItem item);
}