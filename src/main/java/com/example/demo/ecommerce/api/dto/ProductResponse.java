package com.example.demo.ecommerce.api.dto;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

import com.example.demo.ecommerce.api.ProductStatus;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductResponse{
        private Long sellerId;
        private Long id;
        private String name;
        private String description;
        private BigDecimal price;
        private ProductStatus status;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private Set<String> categoryNames; 

}
