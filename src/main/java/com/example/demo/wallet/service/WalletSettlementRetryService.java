package com.example.demo.wallet.service;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.demo.ecommerce.api.OrderLifecycleApi;
import lombok.RequiredArgsConstructor;
@Service @RequiredArgsConstructor
public class WalletSettlementRetryService {
    private final OrderLifecycleApi orders;
    @Transactional public void failed(Long id) { orders.recordSettlementFailure(id); }
}
