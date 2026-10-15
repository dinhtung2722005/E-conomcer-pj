package com.example.demo.ecommerce.job;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.demo.ecommerce.entity.Order;
import com.example.demo.ecommerce.entity.OrderStatus;
import com.example.demo.ecommerce.repository.OrderRepository;
import com.example.demo.ecommerce.service.OrderService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Hủy các đơn hàng PENDING quá hạn thanh toán và hoàn hàng về kho.
 *
 * <p>Cố ý <b>không</b> đặt {@code @Transactional} trên method này. Mỗi lời gọi
 * {@link OrderService#cancelExpiredOrder} là một transaction riêng, nên một đơn lỗi không
 * làm rollback cả lô. Bản cũ bọc cả vòng lặp trong một transaction duy nhất: chỉ cần
 * một đơn lỗi lúc commit là toàn bộ lô bị hủy bỏ, trong khi log vẫn báo thành công
 * từng đơn.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderCleanupJob {

    private final OrderRepository orderRepository;
    private final OrderService orderService;

    @Value("${app.order.pending-timeout-minutes:15}")
    private long pendingTimeoutMinutes;

    @Scheduled(initialDelayString = "${app.order.cleanup-initial-delay-ms:60000}",
            fixedDelayString = "${app.order.cleanup-interval-ms:60000}")
    public void releaseExpiredOrders() {
        LocalDateTime expireTime = LocalDateTime.now().minusMinutes(pendingTimeoutMinutes);
        List<Order> expiredOrders = orderRepository.findByStatusAndCreatedAtBefore(OrderStatus.PENDING, expireTime);

        if (expiredOrders.isEmpty()) {
            return;
        }

        log.info("Phát hiện {} đơn hàng quá hạn thanh toán, bắt đầu hủy và hoàn kho", expiredOrders.size());

        int cancelled = 0;
        for (Order order : expiredOrders) {
            try {
                if (orderService.cancelExpiredOrder(order.getId(), expireTime)) {
                    cancelled++;
                    log.info("Đã tự động hủy đơn hàng ID {} do quá hạn {} phút", order.getId(), pendingTimeoutMinutes);
                }
            } catch (Exception e) {
                log.error("Lỗi khi hủy đơn hàng ID {}: {}", order.getId(), e.getMessage());
            }
        }

        log.info("Hoàn tất dọn đơn quá hạn: {}/{} đơn được hủy", cancelled, expiredOrders.size());
    }
}
