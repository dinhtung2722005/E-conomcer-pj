package com.example.demo.ecommerce.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.ecommerce.dto.OrderResponse;
import com.example.demo.ecommerce.entity.Order;
import com.example.demo.ecommerce.entity.OrderItem;
import com.example.demo.ecommerce.entity.OrderStatus;
import com.example.demo.ecommerce.mapper.OrderMapper;
import com.example.demo.ecommerce.repository.OrderRepository;
import com.example.demo.user.exception.AppException;
import com.example.demo.user.exception.ErrorCode;

@Service
public class OrderService {

    @Autowired
    private OrderRepository orderRepository;
    
    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private InventoryService inventoryService; 

    public List<OrderResponse> getAllOrders() {
        return orderRepository.findAll()
                .stream()
                .map(orderMapper::toResponse)
                .toList();
    }

    public OrderResponse getOrderById(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.Order_Not_Found));
        return orderMapper.toResponse(order);
    }

    @Transactional
    public OrderResponse updateOrderStatus(Long id, OrderStatus newStatus) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.Order_Not_Found));

        order.setStatus(newStatus);
        
        return orderMapper.toResponse(orderRepository.save(order));
    }

    @Transactional
    public OrderResponse cancelOrder(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.Order_Not_Found));

        if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.COMPLETED) {
            throw new RuntimeException("Không thể hủy đơn hàng ở trạng thái này!");
        }

        order.setStatus(OrderStatus.CANCELLED);
        Order savedOrder = orderRepository.save(order);
        for (OrderItem item : order.getItems()) {
            inventoryService.releaseInventory(
                item.getProduct().getId(), 
                item.getQuantity()
            );
        }

        return orderMapper.toResponse(savedOrder);
    }
    public List<OrderResponse> getUserOrders(Long userId) {
        // Lưu ý: Bạn cần vào OrderRepository thêm dòng: List<Order> findByUserId(Long userId);
        return orderRepository.findByUserId(userId)
                .stream()
                .map(orderMapper::toResponse)
                .toList();
    }

    // Xem chi tiết đơn hàng (Bắt buộc kiểm tra quyền sở hữu)
    public OrderResponse getOrderDetail(Long orderId, Long userId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.Order_Not_Found));

        if (!order.getUserId().equals(userId)) {
            throw new RuntimeException("Bạn không có quyền truy cập đơn hàng này!");
        }

        return orderMapper.toResponse(order);
    }

    // Khách hàng tự hủy đơn
    @Transactional
    public OrderResponse cancelOrderByUser(Long orderId, Long userId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.Order_Not_Found));

        if (!order.getUserId().equals(userId)) {
            throw new RuntimeException("Bạn không có quyền hủy đơn hàng này!");
        }

        // Tận dụng lại chính hàm cancelOrder gốc của bạn để xử lý logic hoàn kho
        return this.cancelOrder(orderId);
    }
}