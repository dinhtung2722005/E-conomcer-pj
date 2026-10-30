package com.example.demo.wallet.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.example.demo.wallet.entity.TransactionStatus;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RefundResponse {
    private Long refundCaseId;
    private Long orderId;
    private BigDecimal totalAmount;
    private BigDecimal cashAmount;
    private BigDecimal bonusAmount;
    private String currency;
    private List<Long> transactionIds = new ArrayList<>();
    private TransactionStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}
