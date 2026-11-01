package com.example.demo.ecommerce.api.dto;

import java.time.LocalDateTime;

import com.example.demo.ecommerce.api.FlashSaleStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class FlashSaleResponse{
        private Long id;
        private String name;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private FlashSaleStatus status;
        private LocalDateTime createdAt;
}