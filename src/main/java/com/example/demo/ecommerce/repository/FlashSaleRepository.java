package com.example.demo.ecommerce.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.demo.ecommerce.entity.FlashSale;
import com.example.demo.ecommerce.entity.FlashSaleStatus;

public interface FlashSaleRepository extends JpaRepository<FlashSale, Long> {
Optional<FlashSale> findFirstByStatusAndStartTimeBeforeAndEndTimeAfter(
            FlashSaleStatus status,
            LocalDateTime currentTimeForStart,
            LocalDateTime currentTimeForEnd
    );
    List<FlashSale> findByStatus(String status);
}
