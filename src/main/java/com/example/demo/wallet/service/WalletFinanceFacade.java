package com.example.demo.wallet.service;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import com.example.demo.wallet.api.*;
import com.example.demo.wallet.api.dto.*;
import com.example.demo.ecommerce.api.OrderSnapshot;
import com.example.demo.user.api.UserAccount;
import lombok.RequiredArgsConstructor;

@Service @RequiredArgsConstructor @Transactional(propagation=Propagation.MANDATORY)
public class WalletFinanceFacade implements WalletPaymentsApi,WalletReturnsApi {
    private final WalletPaymentService payments;
    private final WalletOrderFundsService cancellations;
    private final WalletSettlementService settlement;
    private final WalletReturnService returns;
    @Override @Transactional(propagation=Propagation.NOT_SUPPORTED)
    public PaymentPermit authorizePayment(Long id,PayOrderRequest request){return payments.authorizePayment(id,request);}
    @Override public void lockPayer(PaymentPermit permit){payments.lockPayer(permit);}
    @Override public Reservation reserve(OrderSnapshot order,PaymentPermit permit,String key){return payments.reserve(order,permit,key);}
    @Override public void refundCancellation(Long orderId,Long buyerId){cancellations.refundCancellation(orderId,buyerId);}
    @Override public BigDecimal settle(OrderSnapshot order){return settlement.settle(order);}
    @Override public RefundCaseResponse request(OrderSnapshot order,RefundCaseRequest request,boolean adminException){return returns.request(order,request,adminException);}
    @Override public RefundCaseResponse get(Long id){return returns.get(id);}
    @Override public Decision decide(OrderSnapshot order,UserAccount actor,RefundDecisionRequest request){return returns.decide(order,actor,request);}
}
