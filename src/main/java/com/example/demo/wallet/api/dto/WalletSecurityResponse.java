package com.example.demo.wallet.api.dto;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WalletSecurityResponse {
    private boolean pinConfigured;
    private LocalDateTime pinLockedUntil;
    private LocalDateTime pinChangedAt;
}
