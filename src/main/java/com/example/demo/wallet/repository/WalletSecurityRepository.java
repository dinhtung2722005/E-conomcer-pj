package com.example.demo.wallet.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.demo.wallet.entity.WalletSecurity;

import jakarta.persistence.LockModeType;

/** The inherited findById(walletId) reads security data because its primary key is the wallet ID. */
public interface WalletSecurityRepository extends JpaRepository<WalletSecurity, Long> {
    /** Lock the existing wallet first when initializing security: an absent security row cannot be locked. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM WalletSecurity s WHERE s.id = :walletId")
    Optional<WalletSecurity> findByWalletIdForUpdate(@Param("walletId") Long walletId);
}
