package com.example.demo.wallet.api.dto;
import com.example.demo.wallet.api.AlertStatus;
import com.example.demo.wallet.api.RefundStatus;
import com.example.demo.wallet.api.TransactionStatus;
import com.example.demo.wallet.api.TransactionType;
import com.example.demo.wallet.api.WalletHoldStatus;
import com.example.demo.wallet.api.WalletHoldType;
import com.example.demo.wallet.api.WalletBalanceType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
public final class WalletOpsDtos {
    private WalletOpsDtos() {}
    public record Summary(long unfinishedTransfers,long pendingRefunds,long openAlerts,long unbalancedJournals) {}
    public record Transfer(Long id,Long transactionId,Long walletId,TransactionType type,BigDecimal amount,String currency,
        TransactionStatus status,boolean suspended,boolean finished,int attempts,String lastError,LocalDateTime createdAt,
        LocalDateTime nextAttemptAt,LocalDateTime dispatchStartedAt,boolean slaBreached) {}
    public record Held(Long id,Long walletId,Long orderId,WalletHoldType type,WalletBalanceType source,BigDecimal remaining,
        WalletHoldStatus status,LocalDateTime createdAt,boolean slaBreached) {}
    public record Refund(Long id,Long orderId,RefundStatus status,String reason,LocalDateTime createdAt,
        LocalDateTime reviewedAt,boolean slaBreached) {}
    public record Settlement(Long orderId,BigDecimal amount,LocalDateTime availableAt,LocalDateTime settledAt,
        LocalDateTime nextAttemptAt,int attempts,String lastError,boolean slaBreached) {}
    public record Alert(Long id,String category,String reference,String message,AlertStatus status,
        LocalDateTime createdAt,LocalDateTime lastSeenAt,Long reviewedBy,String reviewReason) {}
    public record Audit(Long id,Long actorId,Long walletId,String action,String reference,String reason,String requestId,LocalDateTime createdAt) {}
    public record SecurityEvent(Long id,Long actorId,Long walletId,String action,String outcome,String code,
        String reference,String requestId,LocalDateTime createdAt) {}
}
