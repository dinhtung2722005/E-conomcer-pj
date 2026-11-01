package com.example.demo.wallet.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.example.demo.wallet.api.TransactionStatus;
import com.example.demo.wallet.api.TransactionType;
import com.example.demo.wallet.api.WalletBalanceType;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WalletTransactionResponse {
    private Long id;
    private String referenceId;
    private TransactionType type;
    private WalletBalanceType balanceType;
    private BigDecimal amount;
    private TransactionStatus status;
    private Long originalTransactionId;
    private String failureReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;
}
