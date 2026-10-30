package com.example.demo.wallet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Used for both freezing and administrator unfreezing, so every change has a reason. */
@Getter
@Setter
public class WalletFreezeRequest {
    @NotBlank(message = "Lý do không được để trống")
    @Size(max = 500, message = "Lý do tối đa 500 ký tự")
    private String reason;
}
