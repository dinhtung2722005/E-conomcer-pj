package com.example.demo.wallet.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.example.demo.wallet.api.TransactionStatus;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TopUpResponse {
    private Long transactionId;
    private BigDecimal amount;
    private String currency;
    private TransactionStatus status;
    private TopUpInstructionsResponse instructions;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private LocalDateTime completedAt;
}
