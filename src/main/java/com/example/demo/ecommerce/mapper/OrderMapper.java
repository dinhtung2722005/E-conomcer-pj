package com.example.demo.ecommerce.mapper;

import java.util.ArrayList;
import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ObjectFactory;

import com.example.demo.ecommerce.dto.OrderItemResponse;
import com.example.demo.ecommerce.dto.OrderResponse;
import com.example.demo.ecommerce.entity.Order;
import com.example.demo.ecommerce.entity.OrderItem;

@Mapper(componentModel = "spring")
public interface OrderMapper {

    @Mapping(source = "voucher.code", target = "voucherCode")
    OrderResponse toResponse(Order order);

    /**
     * {@code productName} ưu tiên lấy từ ảnh chụp lưu trên đơn ({@code orderItem.productName})
     * để hóa đơn cũ không đổi theo tên sản phẩm hiện tại. Các đơn tạo trước khi có cột này
     * sẽ rơi về tên sản phẩm hiện hành.
     */
    @Mapping(source = "product.id", target = "productId")
    @Mapping(target = "productName", expression = "java(resolveProductName(orderItem))")
    @Mapping(source = "priceAtPurchase", target = "price")
    OrderItemResponse toOrderItemResponse(OrderItem orderItem);

    @ObjectFactory
    default List<OrderItemResponse> createOrderItemResponses(List<OrderItem> items) {
        return new ArrayList<>(items.size());
    }

    default String resolveProductName(OrderItem orderItem) {
        if (orderItem.getProductName() != null) {
            return orderItem.getProductName();
        }
        return orderItem.getProduct() != null ? orderItem.getProduct().getName() : null;
    }
}
