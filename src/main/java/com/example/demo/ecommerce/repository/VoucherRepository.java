package com.example.demo.ecommerce.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.demo.ecommerce.entity.Voucher;

public interface VoucherRepository extends JpaRepository<Voucher, Long> {
    Optional<Voucher> findByCode(String code);
    boolean existsByCode(String code);
    @Query("SELECT v FROM Voucher v WHERE v.startDate <= :now AND v.endDate >= :now AND v.usedQuantity < v.totalQuantity")
    List<Voucher> findAvailableVouchers(@Param("now") LocalDateTime now);
}
