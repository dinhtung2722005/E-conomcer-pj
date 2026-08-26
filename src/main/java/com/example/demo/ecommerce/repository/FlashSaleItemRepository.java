package com.example.demo.ecommerce.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.demo.ecommerce.entity.FlashSaleItem;

@Repository
public interface FlashSaleItemRepository extends JpaRepository<FlashSaleItem, Long> {
    
    List<FlashSaleItem> findByFlashSaleId(Long flashSaleId);

    boolean existsByFlashSaleIdAndProductId(Long flashSaleId, Long productId);
}