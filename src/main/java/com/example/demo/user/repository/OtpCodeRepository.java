package com.example.demo.user.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.demo.user.entity.OtpCode;

@Repository
public interface OtpCodeRepository extends JpaRepository<OtpCode, Long> {
    
    Optional<OtpCode> findTopByEmailAndIsUsedFalseOrderByExpirationTimeDesc(String email);
}