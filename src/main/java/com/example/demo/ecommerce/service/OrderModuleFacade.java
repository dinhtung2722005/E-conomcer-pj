package com.example.demo.ecommerce.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.data.domain.Pageable;
import com.example.demo.ecommerce.api.*;
import com.example.demo.ecommerce.api.dto.OrderResponse;
import com.example.demo.ecommerce.entity.Order;
import com.example.demo.ecommerce.repository.OrderRepository;
import com.example.demo.shared.error.*;
import com.example.demo.shared.web.*;
import lombok.RequiredArgsConstructor;

@Service @RequiredArgsConstructor
public class OrderModuleFacade implements OrderLifecycleApi,OrderOwnershipApi {
    private final OrderRepository orders;
    private final OrderService service;
    private final InventoryService inventory;
    private final jakarta.persistence.EntityManager entities;
    @Override @Transactional(propagation=Propagation.SUPPORTS,readOnly=true)
    public java.util.Optional<Long> ownerId(Long id){
        // find() returns the already managed/locked Order in financial transactions. Unlike a JPQL
        // query it does not trigger AUTO flush while Hibernate is validating a WalletHold insertion.
        return java.util.Optional.ofNullable(entities.find(Order.class,id)).map(Order::getUserId);
    }
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public OrderSnapshot lock(Long id,Long ownerId){
        Order order=(ownerId==null?orders.findByIdForUpdate(id):orders.findByIdAndUserIdForUpdate(id,ownerId))
                .orElseThrow(()->new AppException(ErrorCode.ORDER_NOT_FOUND));
        return snapshot(order);
    }
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public void completePayment(Long id){
        var order=locked(id);
        if(order.getStatus()!=OrderStatus.PENDING)throw new AppException(ErrorCode.ORDER_STATUS_INVALID);
        for(var item:order.getItems().stream().sorted(java.util.Comparator.comparing(i->i.getProduct().getId())).toList())
            inventory.confirmSale(item.getProduct().getId(),item.getQuantity());
        order.setStatus(OrderStatus.PAID);orders.saveAndFlush(order);
    }
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public void completeReturn(Long id,List<ReturnedQuantity> lines,BigDecimal amount,boolean fullyReturned){
        var order=locked(id);
        if(order.getStatus()!=OrderStatus.COMPLETED||amount.signum()<0||order.getRefundedAmount().add(amount).compareTo(order.getFinalAmount())>0)
            throw new AppException(ErrorCode.ORDER_STATUS_INVALID);
        service.applyApprovedReturn(order,lines,fullyReturned);
        order.setRefundedAmount(order.getRefundedAmount().add(amount));orders.saveAndFlush(order);
    }
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public void completeSettlement(Long id,BigDecimal due){
        var order=locked(id);
        if(order.getStatus()!=OrderStatus.COMPLETED||order.getSettledAt()!=null||order.getFinalAmount().subtract(order.getRefundedAmount()).compareTo(due)!=0)
            throw new AppException(ErrorCode.ORDER_STATUS_INVALID);
        order.setSettledAmount(due);order.setSettledAt(LocalDateTime.now());
        order.setSettlementLastError(null);order.setSettlementNextAttemptAt(null);orders.saveAndFlush(order);
    }
    @Override @Transactional(readOnly=true) public OrderResponse response(Long id){return service.getOrderById(id);}
    @Override @Transactional public OrderResponse confirmReceipt(Long id){return service.confirmReceipt(id);}
    @Override @Transactional(propagation=Propagation.MANDATORY) public OrderResponse cancelPrepared(Long id){return service.cancelOrder(id);}
    @Override @Transactional(propagation=Propagation.MANDATORY) public OrderResponse updateNonFinancialStatus(Long id,OrderStatus status){
        if(status==OrderStatus.PAID||status==OrderStatus.CANCELLED)throw new AppException(ErrorCode.ORDER_STATUS_INVALID);
        return service.updateOrderStatus(id,status);
    }
    @Override @Transactional(readOnly=true)
    public List<Long> findSettlementDueIds(LocalDateTime now,Long after,Pageable page){return orders.findSettlementDueIds(now,after,page);}
    @Override @Transactional(readOnly=true)
    public PageResponse<SettlementView> unsettled(PageQuery page){
        return PageResponse.from(orders.findAll((r,q,b)->b.and(b.equal(r.get("status"),OrderStatus.COMPLETED),b.isNull(r.get("settledAt"))),page.toPageable())
                .map(o->new SettlementView(o.getId(),o.getFinalAmount().subtract(o.getRefundedAmount()),o.getSettlementAvailableAt(),o.getSettledAt(),o.getSettlementNextAttemptAt(),o.getSettlementAttempts(),o.getSettlementLastError(),o.getSettlementAvailableAt()!=null&&o.getSettlementAvailableAt().isBefore(LocalDateTime.now().minusDays(1)))));
    }
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public void scheduleSettlementRetry(Long id){var order=locked(id);if(order.getSettledAt()!=null)throw new AppException(ErrorCode.DATA_CONFLICT);order.setSettlementNextAttemptAt(LocalDateTime.now());}
    @Override @Transactional
    public void recordSettlementFailure(Long id){
        var order=locked(id);if(order.getSettledAt()!=null)return;
        order.setSettlementAttempts(Math.min(1000000,order.getSettlementAttempts()+1));
        order.setSettlementLastError("SETTLEMENT_REQUIRES_REVIEW");
        order.setSettlementNextAttemptAt(LocalDateTime.now().plusSeconds(Math.min(3600,30L<<Math.min(7,order.getSettlementAttempts()))));
    }
    private Order locked(Long id){return orders.findByIdForUpdate(id).orElseThrow(()->new AppException(ErrorCode.ORDER_NOT_FOUND));}
    private OrderSnapshot snapshot(Order o){
        return new OrderSnapshot(o.getId(),o.getUserId(),o.getStatus(),o.getFinalAmount(),o.getRefundedAmount(),o.getSettledAmount(),o.getCashbackPercent(),o.getCreatedAt(),o.getSettlementAvailableAt(),o.getSettledAt(),
                o.getItems().stream().map(i->new OrderSnapshot.Item(i.getId(),i.getProduct().getId(),i.getSellerId(),i.getQuantity(),i.getSettlementAmount(),i.getPlatformFee())).toList());
    }
}
