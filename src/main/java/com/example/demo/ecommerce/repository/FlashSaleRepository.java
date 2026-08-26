package com.example.demo.ecommerce.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.demo.ecommerce.entity.FlashSale;

public interface FlashSaleRepository extends JpaRepository<FlashSale, Long> {
    
}
