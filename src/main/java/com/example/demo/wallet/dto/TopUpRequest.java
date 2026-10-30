package com.example.demo.wallet.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

/** Ownership and verification of the linked account must be checked by the funding service. */
@Getter
@Setter
public class TopUpRequest {
    @NotNull(message = "Số tiền nạp không được để trống")
    @DecimalMin(value = "0", inclusive = false, message = "Số tiền nạp phải lớn hơn 0")
    @Digits(integer = 15, fraction = 4, message = "Số tiền tối đa 15 chữ số nguyên và 4 chữ số thập phân")
    private BigDecimal amount;

    @NotNull(message = "Phải chọn tài khoản ngân hàng")
    @Positive(message = "Mã tài khoản ngân hàng phải lớn hơn 0")
    private Long linkedBankAccountId;
}
