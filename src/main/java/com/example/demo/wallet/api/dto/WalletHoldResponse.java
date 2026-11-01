package com.example.demo.wallet.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.example.demo.wallet.api.WalletBalanceType;
import com.example.demo.wallet.api.WalletHoldStatus;
import com.example.demo.wallet.api.WalletHoldType;

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
