package com.example.demo.wallet.dto;
import com.example.demo.wallet.entity.WalletAlert;
import jakarta.validation.constraints.*;
import lombok.*;
@Getter @Setter
public class WalletAlertReviewRequest {
    @NotNull private WalletAlert.Status status;
    @NotBlank @Size(max=500) private String reason;
}
