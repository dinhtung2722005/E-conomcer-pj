package com.example.demo.wallet.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.example.demo.wallet.entity.WalletStatus;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WalletResponse {
    private Long id;
    private String currency;
    private BigDecimal balance;
    private BigDecimal pendingBalance;
    private BigDecimal bonusBalance;
    private BigDecimal pendingBonusBalance;
    private WalletStatus status;
    private boolean pinConfigured;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
