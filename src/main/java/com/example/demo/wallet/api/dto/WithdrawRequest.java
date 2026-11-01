package com.example.demo.wallet.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

/** The funding service permits withdrawals from available CASH only. */
@Getter
@Setter
public class WithdrawRequest {
    @NotNull(message = "Số tiền rút không được để trống")
    @DecimalMin(value = "0", inclusive = false, message = "Số tiền rút phải lớn hơn 0")
    @Digits(integer = 15, fraction = 4, message = "Số tiền tối đa 15 chữ số nguyên và 4 chữ số thập phân")
    @Schema(description = "Số VND nguyên dương rút từ CASH khả dụng; hạn mức lấy từ cấu hình máy chủ", example = "100000", minimum = "1", multipleOf = 1)
    private BigDecimal amount;

    @NotNull(message = "Phải chọn tài khoản ngân hàng nhận tiền")
    @Positive(message = "Mã tài khoản ngân hàng phải lớn hơn 0")
    @Schema(description = "ID tài khoản ngân hàng nhận tiền của chính chủ đã xác minh", example = "1")
    private Long linkedBankAccountId;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @NotBlank(message = "Mã PIN không được để trống")
    @Pattern(regexp = "[0-9]{6}", message = "Mã PIN phải gồm đúng 6 chữ số")
    @Schema(description = "Mã PIN ví gồm 6 chữ số", format = "password", accessMode = Schema.AccessMode.WRITE_ONLY)
    private String pin;
}
