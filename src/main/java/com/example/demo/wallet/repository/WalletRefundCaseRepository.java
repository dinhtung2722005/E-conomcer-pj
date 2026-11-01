package com.example.demo.wallet.repository;
import com.example.demo.wallet.api.RefundStatus;

import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import com.example.demo.wallet.entity.WalletRefundCase;

public interface WalletRefundCaseRepository extends JpaRepository<WalletRefundCase, Long>, JpaSpecificationExecutor<WalletRefundCase> {
    long countByStatus(RefundStatus status);
    @Query("select r.id from WalletRefundCase r where r.reviewedAt is null and r.createdAt<:cutoff and r.id>:after order by r.id")
    java.util.List<Long> overdueIds(java.time.LocalDateTime cutoff,Long after,org.springframework.data.domain.Pageable page);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<WalletRefundCase> findFirstByOrderIdOrderByIdDesc(Long orderId);
    @Lock(LockModeType.PESSIMISTIC_WRITE) Optional<WalletRefundCase> findByOrderIdAndRequestKey(Long orderId,String requestKey);
    @Lock(LockModeType.PESSIMISTIC_WRITE) Optional<WalletRefundCase> findByIdAndOrderId(Long id,Long orderId);
    long countByOrderId(Long orderId);
}
