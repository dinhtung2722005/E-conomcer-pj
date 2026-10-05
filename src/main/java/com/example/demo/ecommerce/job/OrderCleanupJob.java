package com.example.demo.ecommerce.job;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.ecommerce.entity.Order;
import com.example.demo.ecommerce.entity.OrderStatus;
import com.example.demo.ecommerce.repository.OrderRepository;
import com.example.demo.ecommerce.service.OrderService;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class OrderCleanupJob {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderService orderService; 
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void releaseExpiredOrders() {
        LocalDateTime expireTime = LocalDateTime.now().minusMinutes(15);        
        List<Order> expiredOrders = orderRepository.findByStatusAndCreatedAtBefore(OrderStatus.PENDING, expireTime);
        if (!expiredOrders.isEmpty()) {
            log.info("Phát hiện {} đơn hàng quá hạn thanh toán. Bắt đầu hủy và hoàn kho...", expiredOrders.size());
            
            for (Order order : expiredOrders) {
                try {
                    orderService.cancelOrder(order.getId());
                    log.info("Đã tự động hủy đơn hàng ID: {} do quá hạn 15 phút.", order.getId());
                } catch (Exception e) {
                    log.error("Lỗi khi xử lý hủy đơn hàng ID {}: {}", order.getId(), e.getMessage());
                }
            }
        }
    }
}