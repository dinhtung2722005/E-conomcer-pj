package com.example.demo.wallet.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.demo.wallet.entity.WalletStatusChange;

/** Audit writes belong to the same transaction as the wallet state change. */
public interface WalletStatusChangeRepository extends JpaRepository<WalletStatusChange, Long> {
    Page<WalletStatusChange> findByWalletIdOrderByCreatedAtDescIdDesc(Long walletId, Pageable pageable);
}
