package com.example.demo.ecommerce.api.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FlashSaleItemRequest {

    @NotNull(message = "Sản phẩm không được để trống")
    private Long productId;

    @NotNull(message = "Giá khuyến mãi không được để trống")
    @DecimalMin(value = "0", inclusive = false, message = "Giá khuyến mãi phải lớn hơn 0")
    @Digits(integer = 15, fraction = 4, message = "Giá khuyến mãi không hợp lệ")
    private BigDecimal promotionalPrice;

    @NotNull(message = "Số lượng không được để trống")
    @Positive(message = "Số lượng phải lớn hơn 0")
    private Integer totalQuantity;
}
