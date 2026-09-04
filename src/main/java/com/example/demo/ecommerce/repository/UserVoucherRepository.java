package com.example.demo.ecommerce.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.demo.ecommerce.entity.UserVoucher;

@Repository
public interface UserVoucherRepository extends JpaRepository<UserVoucher, Long> {
    

    boolean existsByUserIdAndVoucherId(Long userId, Long voucherId);
    
    List<UserVoucher> findByUserIdAndIsUsedFalse(Long userId);
}