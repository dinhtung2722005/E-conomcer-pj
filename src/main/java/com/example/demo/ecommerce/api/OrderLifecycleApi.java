package com.example.demo.ecommerce.api;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import com.example.demo.ecommerce.api.dto.OrderResponse;
import com.example.demo.shared.web.*;

public interface OrderLifecycleApi {
    record ReturnedQuantity(Long orderItemId,int quantity){}
    record SettlementView(Long orderId,BigDecimal amount,LocalDateTime availableAt,LocalDateTime settledAt,
            LocalDateTime nextAttemptAt,int attempts,String lastError,boolean slaBreached){}
    /** Joins the caller transaction and locks before returning any data. ownerId null is an internal/admin lookup. */
    OrderSnapshot lock(Long orderId,Long ownerId);
    void completePayment(Long orderId);
    void completeReturn(Long orderId,List<ReturnedQuantity> lines,BigDecimal amount,boolean fullyReturned);
    void completeSettlement(Long orderId,BigDecimal due);
    OrderResponse response(Long orderId);
    OrderResponse confirmReceipt(Long orderId);
    OrderResponse cancelPrepared(Long orderId);
    OrderResponse updateNonFinancialStatus(Long orderId,OrderStatus status);
    List<Long> findSettlementDueIds(LocalDateTime now,Long after,Pageable page);
    PageResponse<SettlementView> unsettled(PageQuery page);
    void scheduleSettlementRetry(Long orderId);
    void recordSettlementFailure(Long orderId);
}
