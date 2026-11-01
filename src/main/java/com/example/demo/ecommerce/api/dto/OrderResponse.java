package com.example.demo.ecommerce.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.example.demo.ecommerce.api.OrderStatus;

import lombok.Getter;
import lombok.Setter;

/**
 * Đơn hàng trả về cho client.
 *
 * <p>Bổ sung thông tin giao hàng và tiền giảm giá — trước đây các trường này được lưu
 * trong DB nhưng không hề trả ra, nên admin không thể dùng API để giao hàng.
 */
@Getter
@Setter
public class OrderResponse {

    private Long id;
    private Long userId;
    private OrderStatus status;

    private String receiverName;
    private String receiverPhone;
    private String shippingAddress;

    private BigDecimal totalAmount;
    private BigDecimal discountAmount;
    private BigDecimal finalAmount;
    private String voucherCode;

    private List<OrderItemResponse> items;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;
    private LocalDateTime settlementAvailableAt;
    private LocalDateTime settledAt;
}
