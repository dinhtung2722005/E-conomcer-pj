package com.example.demo.wallet.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/** Order ID comes from the path; amount and owner are resolved on the server. */
@Getter
@Setter
public class PayOrderRequest {
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @NotBlank(message = "Mã PIN không được để trống")
    @Pattern(regexp = "[0-9]{6}", message = "Mã PIN phải gồm đúng 6 chữ số")
    private String pin;

    private boolean useBonus = false;
}
