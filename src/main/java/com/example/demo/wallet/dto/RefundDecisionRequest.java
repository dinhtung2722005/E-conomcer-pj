package com.example.demo.wallet.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class RefundDecisionRequest {
    @Positive private Long caseId;
    @NotNull private Boolean approved;
    @NotBlank @Size(max = 500) private String reason;
}
