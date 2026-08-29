package com.example.demo.ecommerce.dto;

import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
@Getter
@Setter
@AllArgsConstructor

public class FlashSaleCustomerResponse{
        private Long id;
        private String name; 
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private List<FlashSaleItemCustomerResponse> items;
}