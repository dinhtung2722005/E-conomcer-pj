package com.example.demo.wallet.service;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.demo.ecommerce.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
@Service @RequiredArgsConstructor
public class WalletSettlementRetryService {
    private final OrderRepository orders;
    @Transactional public void failed(Long id) {
        var order=orders.findByIdForUpdate(id).orElseThrow();
        if(order.getSettledAt()!=null) return;
        order.setSettlementAttempts(Math.min(1000000,order.getSettlementAttempts()+1));
        order.setSettlementLastError("SETTLEMENT_REQUIRES_REVIEW");
        order.setSettlementNextAttemptAt(LocalDateTime.now().plusSeconds(Math.min(3600,30L<<Math.min(7,order.getSettlementAttempts()))));
    }
}
