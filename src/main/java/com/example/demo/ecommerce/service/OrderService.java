package com.example.demo.ecommerce.service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.ecommerce.api.dto.OrderResponse;
import com.example.demo.ecommerce.entity.Order;
import com.example.demo.ecommerce.entity.OrderItem;
import com.example.demo.ecommerce.api.OrderStatus;
import com.example.demo.ecommerce.entity.Voucher;
import com.example.demo.ecommerce.mapper.OrderMapper;
import com.example.demo.ecommerce.repository.OrderRepository;
import com.example.demo.ecommerce.repository.UserVoucherRepository;
import com.example.demo.ecommerce.repository.VoucherRepository;
import com.example.demo.shared.error.AppException;
import com.example.demo.shared.web.PageQuery;
import com.example.demo.shared.web.PageResponse;
import com.example.demo.shared.error.ErrorCode;

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
    private final com.example.demo.user.api.UserAccountsApi currentUser;

    @Transactional
    public OrderResponse confirmReceipt(Long id) {
        var user = currentUser.getCurrentUser();
        if (!user.isActive()) throw new AppException(ErrorCode.USER_LOCKED);
        Order order = orderRepository.findByIdAndUserIdForUpdate(id, user.getId())
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));
        if (order.getStatus() == OrderStatus.COMPLETED) return orderMapper.toResponse(order);
        if (order.getStatus() != OrderStatus.SHIPPED) throw new AppException(ErrorCode.ORDER_STATUS_INVALID);
        order.setStatus(OrderStatus.COMPLETED);
        order.setCompletedAt(LocalDateTime.now());
        order.setSettlementAvailableAt(order.getCompletedAt().plusSeconds(order.getReturnWindowSeconds()));
        return orderMapper.toResponse(orderRepository.save(order));
    }

    /** Called by the order API after the application coordinator approved the refund under the order lock. */
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    void applyApprovedReturn(Order order,java.util.List<com.example.demo.ecommerce.api.OrderLifecycleApi.ReturnedQuantity> lines,boolean fullyReturned) {
        var items=order.getItems().stream().collect(java.util.stream.Collectors.toMap(i->i.getId(),i->i));
        var seen=new java.util.HashSet<Long>();
        for(var line:lines){
            var item=items.get(line.orderItemId());
            if(item==null||!seen.add(line.orderItemId())||line.quantity()<=0||line.quantity()>item.getQuantity())throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        for(var line:lines.stream().sorted(Comparator.comparing(l->items.get(l.orderItemId()).getProduct().getId())).toList()) {
            var item=items.get(line.orderItemId());
            inventoryService.restock(item.getProduct().getId(),line.quantity());
        }
        if(fullyReturned){refundVoucher(order);order.setStatus(OrderStatus.REFUNDED);}
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> getAllOrders(PageQuery pagination) {
        return PageResponse.from(orderRepository.findAll(pagination.toPageable())
                .map(orderMapper::toResponse));
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long id) {
        return orderMapper.toResponse(findOrder(id));
    }

    @Transactional
    OrderResponse updateOrderStatus(Long id, OrderStatus newStatus) {
        Order order = findOrderForUpdate(id);
        OrderStatus current = order.getStatus();

        if (current == newStatus) {
            return orderMapper.toResponse(order);
        }
        if (!ALLOWED_TRANSITIONS.getOrDefault(current, Set.of()).contains(newStatus)) {
            throw new AppException(ErrorCode.ORDER_STATUS_INVALID,
                    "Không thể chuyển đơn hàng từ " + current + " sang " + newStatus);
        }

        if (newStatus == OrderStatus.PAID) {
            throw new AppException(ErrorCode.ORDER_STATUS_INVALID, "Thanh toán đơn hàng phải đi qua ví");
        } else if (newStatus == OrderStatus.CANCELLED) {
            return cancelLockedOrder(order);
        }

        order.setStatus(newStatus);
        if (newStatus == OrderStatus.COMPLETED) {
            order.setCompletedAt(LocalDateTime.now());
            order.setSettlementAvailableAt(order.getCompletedAt().plusSeconds(order.getReturnWindowSeconds()));
        }
        return orderMapper.toResponse(orderRepository.save(order));
    }

    @Transactional
    OrderResponse cancelOrder(Long id) {
        return cancelLockedOrder(findOrderForUpdate(id));
    }

    private OrderResponse cancelLockedOrder(Order order) {
        OrderStatus current = order.getStatus();

        // Gửi lại request hủy đơn không hoàn kho/voucher thêm lần nữa.
        if (current == OrderStatus.CANCELLED) {
            return orderMapper.toResponse(order);
        }

        // Chỉ hủy được đơn chưa giao. Đơn đã SHIPPED/COMPLETED/CANCELLED không thể hủy.
        if (current != OrderStatus.PENDING && current != OrderStatus.PAID) {
            throw new AppException(ErrorCode.ORDER_STATUS_INVALID,
                    "Không thể hủy đơn hàng ở trạng thái " + current);
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

    /**
     * Trả hàng về kho khi đơn bị hủy.
     *
     * <p>Chỉ đơn {@code PENDING} còn giữ hàng ở {@code lockedQuantity}; đơn đã qua
     * {@code PAID} (kể cả khi đang {@code SHIPPED}) đã trừ hẳn khỏi kho nên chỉ cộng
     * lại {@code availableQuantity}. Việc xác định theo "đã từng PAID hay chưa" là
     * bắt buộc — nếu chỉ so sánh với {@code PAID} thì hủy đơn ở trạng thái
     * {@code SHIPPED} sẽ gọi sai nhánh và thất bại.
     */
    private void restock(Order order) {
        boolean stockStillHeld = order.getStatus() == OrderStatus.PENDING;
        for (OrderItem item : sortedItems(order)) {
            if (stockStillHeld) {
                inventoryService.releaseInventory(item.getProduct().getId(), item.getQuantity());
            } else {
                inventoryService.restock(item.getProduct().getId(), item.getQuantity());
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

    private Order findOrderForUpdate(Long id) {
        return orderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));
    }

    private List<OrderItem> sortedItems(Order order) {
        return order.getItems().stream()
                .sorted(Comparator.comparing(item -> item.getProduct().getId()))
                .toList();
    }
}
