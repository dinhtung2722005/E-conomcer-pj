package com.example.demo.wallet.dto;
import java.math.BigDecimal;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
public record WalletReserveFundingRequest(@NotNull @DecimalMin(value="0",inclusive=false) @Digits(integer=15,fraction=4) BigDecimal amount,
    @NotNull @Pattern(regexp="[0-9]{6}") @JsonProperty(access=JsonProperty.Access.WRITE_ONLY) String pin,
    @NotBlank @Size(max=500) String reason) {}
