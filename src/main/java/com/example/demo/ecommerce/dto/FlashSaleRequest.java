package com.example.demo.ecommerce.dto;

import java.time.LocalDateTime;

import com.example.demo.ecommerce.entity.FlashSaleStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class FlashSaleRequest{
        private String name;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private FlashSaleStatus status; 
}