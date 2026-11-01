package com.example.demo.wallet.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.example.demo.wallet.api.TransactionStatus;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WithdrawResponse {
    private Long transactionId;
    private Long holdId;
    private BigDecimal amount;
    private String currency;
    private TransactionStatus status;
    private BankAccountSummaryResponse bankAccount;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}
