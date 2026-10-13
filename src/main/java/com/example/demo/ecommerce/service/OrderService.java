package com.example.demo.ecommerce.service;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.ecommerce.dto.OrderResponse;
import com.example.demo.ecommerce.entity.Order;
import com.example.demo.ecommerce.entity.OrderItem;
import com.example.demo.ecommerce.entity.OrderStatus;
import com.example.demo.ecommerce.entity.UserVoucher;
import com.example.demo.ecommerce.entity.Voucher;
import com.example.demo.ecommerce.mapper.OrderMapper;
import com.example.demo.ecommerce.repository.OrderRepository;
import com.example.demo.ecommerce.repository.UserVoucherRepository;
import com.example.demo.ecommerce.repository.VoucherRepository;
import com.example.demo.user.exception.AppException;
import com.example.demo.user.exception.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    /**
     * Các bước chuyển trạng thái hợp lệ.
     *
     * <p>Trước đây admin có thể đặt một đơn về bất kỳ trạng thái nào, kể cả
     * {@code SHIPPED -> PENDING}, làm sai lệch dữ liệu kho và doanh thu.
     */
    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = Map.of(
            OrderStatus.PENDING, EnumSet.of(OrderStatus.PAID, OrderStatus.CANCELLED),
            OrderStatus.PAID, EnumSet.of(OrderStatus.SHIPPED, OrderStatus.CANCELLED),
            OrderStatus.SHIPPED, EnumSet.of(OrderStatus.COMPLETED),
            OrderStatus.COMPLETED, EnumSet.noneOf(OrderStatus.class),
            OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class));

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final InventoryService inventoryService;
    private final VoucherRepository voucherRepository;
    private final UserVoucherRepository userVoucherRepository;

    @Transactional(readOnly = true)
    public List<OrderResponse> getAllOrders() {
        return orderRepository.findAll().stream()
                .map(orderMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long id) {
        return orderMapper.toResponse(findOrder(id));
    }

    @Transactional
    public OrderResponse updateOrderStatus(Long id, OrderStatus newStatus) {
        Order order = findOrder(id);
        OrderStatus current = order.getStatus();

        if (current == newStatus) {
            return orderMapper.toResponse(order);
        }
        if (!ALLOWED_TRANSITIONS.getOrDefault(current, Set.of()).contains(newStatus)) {
            throw new AppException(ErrorCode.ORDER_STATUS_INVALID,
                    "Không thể chuyển đơn hàng từ " + current + " sang " + newStatus);
        }

        if (newStatus == OrderStatus.PAID) {
            // Hàng đã bán thật: trừ hẳn khỏi số đang giữ chỗ.
            for (OrderItem item : order.getItems()) {
                inventoryService.confirmSale(item.getProduct().getId(), item.getQuantity());
            }
        } else if (newStatus == OrderStatus.CANCELLED) {
            restock(order);
            refundVoucher(order);
        }

        order.setStatus(newStatus);
        return orderMapper.toResponse(orderRepository.save(order));
    }

    @Transactional
    public OrderResponse cancelOrder(Long id) {
        Order order = findOrder(id);

        if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.COMPLETED) {
            throw new AppException(ErrorCode.ORDER_STATUS_INVALID,
                    "Không thể hủy đơn hàng ở trạng thái " + order.getStatus());
        }

        restock(order);
        refundVoucher(order);

        order.setStatus(OrderStatus.CANCELLED);
        return orderMapper.toResponse(orderRepository.save(order));
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getUserOrders(Long userId) {
        return orderRepository.findByUserId(userId).stream()
                .map(orderMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderDetail(Long orderId, Long userId) {
        Order order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));
        return orderMapper.toResponse(order);
    }

    @Transactional
    public OrderResponse cancelOrderByUser(Long orderId, Long userId) {
        Order order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));
        return cancelOrder(order.getId());
    }

    /**
     * Trả hàng về kho. Đơn PENDING còn giữ chỗ nên trả từ {@code locked};
     * đơn PAID đã trừ hẳn nên cộng lại vào {@code available}.
     */
    private void restock(Order order) {
        boolean alreadyConsumed = order.getStatus() == OrderStatus.PAID;
        for (OrderItem item : order.getItems()) {
            if (alreadyConsumed) {
                inventoryService.restock(item.getProduct().getId(), item.getQuantity());
            } else {
                inventoryService.releaseInventory(item.getProduct().getId(), item.getQuantity());
            }
        }
    }

    /**
     * Hoàn lại lượt sử dụng voucher và mở khóa voucher trong ví của người dùng.
     * Bản cũ bỏ qua bước này nên một đơn bị hủy vẫn tiêu mất một lượt voucher.
     */
    private void refundVoucher(Order order) {
        Voucher voucher = order.getVoucher();
        if (voucher == null) {
            return;
        }

        voucherRepository.findByIdForUpdate(voucher.getId()).ifPresent(locked -> {
            if (locked.getUsedQuantity() > 0) {
                locked.setUsedQuantity(locked.getUsedQuantity() - 1);
                voucherRepository.save(locked);
            }
        });

        userVoucherRepository.findByUserIdAndVoucherId(order.getUserId(), voucher.getId())
                .ifPresent(userVoucher -> {
                    userVoucher.setIsUsed(false);
                    userVoucherRepository.save(userVoucher);
                });
    }

    private Order findOrder(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));
    }
}
