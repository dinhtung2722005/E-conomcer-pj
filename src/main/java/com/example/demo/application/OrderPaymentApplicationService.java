package com.example.demo.application;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.annotation.Validated;
import com.example.demo.user.api.UserAccountsApi;
import com.example.demo.ecommerce.api.OrderLifecycleApi;
import com.example.demo.wallet.api.*;
import com.example.demo.wallet.api.dto.*;
import lombok.RequiredArgsConstructor;

@Service @Validated @RequiredArgsConstructor @Transactional(propagation=Propagation.NOT_SUPPORTED)
public class OrderPaymentApplicationService implements OrderPaymentUseCase {
    private final UserAccountsApi users;
    private final OrderLifecycleApi orders;
    private final WalletPaymentsApi wallets;
    private final PlatformTransactionManager manager;
    @Override public PaymentResponse payOrder(Long id,PayOrderRequest request,String key){
        var permit=wallets.authorizePayment(id,request);
        return new TransactionTemplate(manager).execute(tx->{
            wallets.lockPayer(permit);
            var order=orders.lock(id,users.getCurrentUserId());
            var reservation=wallets.reserve(order,permit,key);
            if(!reservation.replay())orders.completePayment(id);
            return reservation.response();
        });
    }
}
