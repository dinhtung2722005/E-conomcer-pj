package com.example.demo.ecommerce.dto;

import java.math.BigDecimal;
import java.util.Set;

import com.example.demo.ecommerce.entity.ProductStatus;

import lombok.Getter;
import lombok.Setter;
@Setter
@Getter
public class ProductRequest{
    private String name;
    private String description;
    private BigDecimal price;
    private ProductStatus status;
    private Set<Long> categoryIds;

}