package com.example.demo.ecommerce.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.demo.ecommerce.entity.FlashSaleItem;
import com.example.demo.ecommerce.entity.FlashSaleStatus;

@Repository
public interface FlashSaleItemRepository extends JpaRepository<FlashSaleItem, Long> {
    
    List<FlashSaleItem> findByFlashSaleId(Long flashSaleId);

    boolean existsByFlashSaleIdAndProductId(Long flashSaleId, Long productId);
    @Query("SELECT fsi.promotionalPrice FROM FlashSaleItem fsi " + // ĐÃ SỬA: promotionalPrice
           "JOIN fsi.flashSale fs " +
           "WHERE fsi.product.id = :productId " +
           "AND fs.status = :status " +
           "AND :now BETWEEN fs.startTime AND fs.endTime")
    Optional<BigDecimal> findActiveFlashSalePrice(
            @Param("productId") Long productId,
            @Param("status") FlashSaleStatus status,
            @Param("now") LocalDateTime now
    );
}