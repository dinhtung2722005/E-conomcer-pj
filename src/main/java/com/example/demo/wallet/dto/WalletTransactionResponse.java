package com.example.demo.wallet.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.example.demo.wallet.entity.TransactionStatus;
import com.example.demo.wallet.entity.TransactionType;
import com.example.demo.wallet.entity.WalletBalanceType;

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
