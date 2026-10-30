package com.example.demo.wallet.gateway;
import java.time.LocalDateTime;
public interface WalletRecoveryDelivery {
    void send(String recipient,String code,LocalDateTime expiresAt);
}
