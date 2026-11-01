package com.example.demo.wallet.api.dto;
import com.example.demo.wallet.api.AlertStatus;
import jakarta.validation.constraints.*;
import lombok.*;
@Getter @Setter
public class WalletAlertReviewRequest {
    @NotNull private AlertStatus status;
    @NotBlank @Size(max=500) private String reason;
}
