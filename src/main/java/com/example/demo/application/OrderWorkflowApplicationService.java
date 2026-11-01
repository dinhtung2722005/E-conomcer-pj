package com.example.demo.application;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.demo.ecommerce.api.*;
import com.example.demo.ecommerce.api.dto.OrderResponse;
import com.example.demo.wallet.api.WalletPaymentsApi;
import com.example.demo.shared.error.*;
import lombok.RequiredArgsConstructor;

@Service @RequiredArgsConstructor @Transactional
public class OrderWorkflowApplicationService implements OrderWorkflowUseCase {
    private final OrderLifecycleApi orders;
    private final WalletPaymentsApi wallets;
    @Override public OrderResponse cancelOrder(Long id){return cancel(orders.lock(id,null));}
    @Override public OrderResponse cancelOrderByUser(Long id,Long userId){return cancel(orders.lock(id,userId));}
    @Override public boolean cancelExpiredOrder(Long id,LocalDateTime cutoff){
        var order=orders.lock(id,null);
        if(order.getStatus()!=OrderStatus.PENDING||!order.getCreatedAt().isBefore(cutoff))return false;
        cancel(order);return true;
    }
    @Override public OrderResponse updateOrderStatus(Long id,OrderStatus status){
        var order=orders.lock(id,null);
        if(order.getStatus()==status)return orders.response(id);
        if(status==OrderStatus.CANCELLED)return cancel(order);
        return orders.updateNonFinancialStatus(id,status);
    }
    @Override public OrderResponse confirmReceipt(Long id){return orders.confirmReceipt(id);}
    private OrderResponse cancel(OrderSnapshot order){
        if(order.getStatus()==OrderStatus.CANCELLED)return orders.response(order.getId());
        if(order.getStatus()!=OrderStatus.PENDING&&order.getStatus()!=OrderStatus.PAID)throw new AppException(ErrorCode.ORDER_STATUS_INVALID);
        wallets.refundCancellation(order.getId(),order.getUserId());
        return orders.cancelPrepared(order.getId());
    }
}
