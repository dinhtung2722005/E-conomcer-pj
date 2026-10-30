package com.example.demo.wallet.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import com.example.demo.wallet.entity.WalletRefundCase;

public interface WalletRefundCaseRepository extends JpaRepository<WalletRefundCase, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<WalletRefundCase> findByOrderId(Long orderId);
}
