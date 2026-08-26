package com.example.demo.ecommerce.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.example.demo.ecommerce.dto.OrderItemResponse;
import com.example.demo.ecommerce.dto.OrderResponse;
import com.example.demo.ecommerce.entity.Order;
import com.example.demo.ecommerce.entity.OrderItem;

@Mapper(componentModel = "spring")
public interface OrderMapper {
    
    OrderResponse toResponse(Order order);

    @Mapping(source = "product.id", target = "productId")
    @Mapping(source = "product.name", target = "productName")
    @Mapping(source = "priceAtPurchase", target = "price")
    OrderItemResponse toOrderItemResponse(OrderItem orderItem);
}