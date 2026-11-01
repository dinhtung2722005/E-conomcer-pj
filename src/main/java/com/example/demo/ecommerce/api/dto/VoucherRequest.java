package com.example.demo.ecommerce.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.example.demo.ecommerce.api.DiscountType;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VoucherRequest {

    @NotBlank(message = "Mã giảm giá không được để trống")
    @Size(max = 50, message = "Mã giảm giá tối đa 50 ký tự")
    private String code;

    @NotNull(message = "Loại giảm giá không được để trống")
    private DiscountType discountType;

    @NotNull(message = "Giá trị giảm không được để trống")
    @DecimalMin(value = "0", inclusive = false, message = "Giá trị giảm phải lớn hơn 0")
    @Digits(integer = 15, fraction = 4, message = "Giá trị giảm không hợp lệ")
    private BigDecimal discountValue;

    /** Chỉ có ý nghĩa với voucher loại PERCENTAGE; có thể để trống. */
    @DecimalMin(value = "0", message = "Mức giảm tối đa không được âm")
    @Digits(integer = 15, fraction = 4, message = "Mức giảm tối đa không hợp lệ")
    private BigDecimal maxDiscountAmount;

    @NotNull(message = "Giá trị đơn tối thiểu không được để trống")
    @DecimalMin(value = "0", message = "Giá trị đơn tối thiểu không được âm")
    @Digits(integer = 15, fraction = 4, message = "Giá trị đơn tối thiểu không hợp lệ")
    private BigDecimal minOrderValue;

    @NotNull(message = "Tổng số lượng không được để trống")
    @Positive(message = "Tổng số lượng phải lớn hơn 0")
    private Integer totalQuantity;

    @NotNull(message = "Ngày bắt đầu không được để trống")
    private LocalDateTime startDate;

    @NotNull(message = "Ngày kết thúc không được để trống")
    private LocalDateTime endDate;
}
