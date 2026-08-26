package com.example.demo.ecommerce.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.example.demo.ecommerce.dto.InventoryResponse;
import com.example.demo.ecommerce.entity.Inventory;

@Mapper(componentModel = "spring")
public interface InventoryMapper {

    @Mapping(source = "product.id", target = "productId")
    @Mapping(source = "product.name", target = "productName")
    InventoryResponse toResponse(Inventory inventory);
}