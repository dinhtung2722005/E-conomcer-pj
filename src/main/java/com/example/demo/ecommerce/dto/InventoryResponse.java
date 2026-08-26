package com.example.demo.ecommerce.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class InventoryResponse{
        private Long id;
        private Long productId;
        private String productName;
        private Integer availableQuantity; 
        private Integer lockedQuantity;
        private Long version;
}