package com.example.demo.application;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.demo.ecommerce.api.OrderLifecycleApi;
import com.example.demo.wallet.api.*;
import lombok.RequiredArgsConstructor;
@Service @RequiredArgsConstructor
public class SettlementApplicationService implements SettlementUseCase {
    private final OrderLifecycleApi orders;
    private final WalletPaymentsApi wallets;
    @Override @Transactional public boolean settle(Long id){
        var order=orders.lock(id,null);var amount=wallets.settle(order);
        if(amount==null)return false;
        orders.completeSettlement(id,amount);return true;
    }
}
