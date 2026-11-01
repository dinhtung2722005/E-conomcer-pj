package com.example.demo.wallet.api.dto;
import com.example.demo.wallet.api.RefundStatus;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class RefundCaseResponse {
    private Long id;
    private Long orderId;
    private java.math.BigDecimal amount;
    private java.util.List<Line> items;
    public record Line(Long orderItemId,int quantity,java.math.BigDecimal amount) {}
    private RefundStatus status;
    private String reason;
    private String decisionReason;
    private LocalDateTime createdAt;
    private LocalDateTime reviewedAt;
}
