package com.example.demo.wallet.dto;

import java.time.LocalDateTime;
import com.example.demo.wallet.entity.WalletRefundCase;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class RefundCaseResponse {
    private Long id;
    private Long orderId;
    private WalletRefundCase.Status status;
    private String reason;
    private String decisionReason;
    private LocalDateTime createdAt;
    private LocalDateTime reviewedAt;
}
