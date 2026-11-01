package com.example.demo.wallet.api.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.example.demo.ecommerce.api.OrderStatus;

import lombok.Getter;
import lombok.Setter;

/** Zero-total orders may complete without creating any positive-amount transaction or hold. */
@Getter
@Setter
public class PaymentResponse {
    private Long orderId;
    private BigDecimal totalAmount;
    private BigDecimal cashAmount;
    private BigDecimal bonusAmount;
    private String currency;
    private List<Long> transactionIds = new ArrayList<>();
    private List<Long> holdIds = new ArrayList<>();
    private OrderStatus orderStatus;
}
