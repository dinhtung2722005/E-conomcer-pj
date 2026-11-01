package com.example.demo.wallet.api;
import com.example.demo.ecommerce.api.OrderSnapshot;
import com.example.demo.wallet.api.dto.*;
public interface WalletPaymentsApi {
    record Reservation(PaymentResponse response,boolean replay){}
    PaymentPermit authorizePayment(Long orderId,PayOrderRequest request);
    void lockPayer(PaymentPermit permit);
    Reservation reserve(OrderSnapshot order,PaymentPermit permit,String key);
    void refundCancellation(Long orderId,Long buyerId);
    /** Null means not eligible/already handled; otherwise the amount actually settled. */
    java.math.BigDecimal settle(OrderSnapshot order);
}
