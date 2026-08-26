package com.example.demo.ecommerce.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.demo.ecommerce.entity.Voucher;

public interface VoucherRepository extends JpaRepository<Voucher, Long> {

    boolean existsByCode(String code);
    
}
