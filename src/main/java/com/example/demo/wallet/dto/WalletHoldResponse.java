package com.example.demo.wallet.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.example.demo.wallet.entity.WalletBalanceType;
import com.example.demo.wallet.entity.WalletHoldStatus;
import com.example.demo.wallet.entity.WalletHoldType;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WalletHoldResponse {
    private Long id;
    private Long orderId;
    private String referenceId;
    private WalletHoldType type;
    private WalletBalanceType balanceType;
    private WalletHoldStatus status;
    private BigDecimal amount;
    private BigDecimal capturedAmount;
    private BigDecimal releasedAmount;
    private BigDecimal remainingAmount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
