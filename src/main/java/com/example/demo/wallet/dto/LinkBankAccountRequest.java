package com.example.demo.wallet.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class LinkBankAccountRequest {
    /** One-time proof from the selected partner's account ownership verification flow. */
    @NotBlank @Size(max = 2048)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String verificationToken;
}
