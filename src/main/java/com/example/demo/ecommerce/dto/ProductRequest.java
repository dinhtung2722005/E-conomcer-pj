package com.example.demo.ecommerce.dto;

import java.math.BigDecimal;
import java.util.Set;

import com.example.demo.ecommerce.entity.ProductStatus;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class ProductRequest {

    @NotBlank(message = "Tên sản phẩm không được để trống")
    @Size(max = 255, message = "Tên sản phẩm tối đa 255 ký tự")
    private String name;

    @Size(max = 5000, message = "Mô tả tối đa 5000 ký tự")
    private String description;

    @NotNull(message = "Giá sản phẩm không được để trống")
    @DecimalMin(value = "0", message = "Giá sản phẩm không được âm")
    @Digits(integer = 15, fraction = 4, message = "Giá sản phẩm không hợp lệ")
    private BigDecimal price;

    @NotNull(message = "Trạng thái sản phẩm không được để trống")
    private ProductStatus status;

    @Size(max = 50, message = "Một sản phẩm tối đa 50 danh mục")
    private Set<Long> categoryIds;
}
