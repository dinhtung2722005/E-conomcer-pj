package com.example.demo.wallet.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

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
    @Schema(description = "Mã PIN ví gồm 6 chữ số", format = "password", accessMode = Schema.AccessMode.WRITE_ONLY)
    private String pin;

    @Schema(description = "Cho phép sử dụng số dư BONUS theo chính sách thanh toán", defaultValue = "false")
    private boolean useBonus = false;
}
