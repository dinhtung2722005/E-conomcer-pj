package com.example.demo.wallet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class RefundCaseRequest {
    @NotBlank @Size(max = 500) private String reason;
}
