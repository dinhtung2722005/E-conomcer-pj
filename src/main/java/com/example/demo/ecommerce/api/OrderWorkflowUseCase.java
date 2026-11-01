package com.example.demo.ecommerce.api;
import java.time.LocalDateTime;
import com.example.demo.ecommerce.api.dto.OrderResponse;
public interface OrderWorkflowUseCase {
    OrderResponse cancelOrder(Long id);
    OrderResponse cancelOrderByUser(Long id,Long userId);
    boolean cancelExpiredOrder(Long id,LocalDateTime cutoff);
    OrderResponse updateOrderStatus(Long id,OrderStatus status);
    OrderResponse confirmReceipt(Long id);
}
