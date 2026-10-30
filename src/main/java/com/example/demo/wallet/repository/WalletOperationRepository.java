package com.example.demo.wallet.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import com.example.demo.wallet.entity.WalletOperation;

public interface WalletOperationRepository extends JpaRepository<WalletOperation, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<WalletOperation> findByWalletIdAndRequestKey(Long walletId, String requestKey);
}
