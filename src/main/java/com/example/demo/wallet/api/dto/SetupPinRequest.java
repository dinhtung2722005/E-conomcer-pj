package com.example.demo.wallet.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SetupPinRequest {
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @NotBlank(message = "Mật khẩu hiện tại không được để trống")
    private String currentPassword;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @NotBlank(message = "Mã PIN mới không được để trống")
    @Pattern(regexp = "[0-9]{6}", message = "Mã PIN phải gồm đúng 6 chữ số")
    private String newPin;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @NotBlank(message = "Xác nhận mã PIN không được để trống")
    @Pattern(regexp = "[0-9]{6}", message = "Mã PIN xác nhận phải gồm đúng 6 chữ số")
    private String confirmPin;

    @AssertTrue(message = "Xác nhận mã PIN không khớp")
    @JsonIgnore
    public boolean isPinConfirmed() {
        return newPin == null || confirmPin == null || newPin.equals(confirmPin);
    }
}
