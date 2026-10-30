package com.example.demo.wallet.dto;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
public record WalletBankActionRequest(
    @NotNull @Pattern(regexp="[0-9]{6}") @JsonProperty(access=JsonProperty.Access.WRITE_ONLY) String pin,
    @NotBlank @Size(max=500) String reason) {}
