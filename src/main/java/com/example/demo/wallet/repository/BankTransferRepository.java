package com.example.demo.wallet.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import com.example.demo.wallet.entity.BankTransfer;
import jakarta.persistence.LockModeType;

public interface BankTransferRepository extends JpaRepository<BankTransfer, Long>, JpaSpecificationExecutor<BankTransfer> {
    long countByFinishedFalse();
    @Query("select t.id from BankTransfer t where t.finished=false and t.createdAt<:cutoff and t.id>:after order by t.id")
    java.util.List<Long> overdueIds(LocalDateTime cutoff,Long after,Pageable page);
    interface Route {
        Long getWalletId();
        Long getTransactionId();
        Long getHoldId();
        Long getUserId();
    }

    @Query("select t.transaction.wallet.id as walletId, t.transaction.id as transactionId, "
            + "h.id as holdId, t.transaction.wallet.userId as userId from BankTransfer t left join t.transaction.hold h where t.id = :id")
    Optional<Route> findRoute(Long id);
    @Query("select t.id from BankTransfer t where t.finished = false and t.nextAttemptAt <= :now "
            + "and (t.leaseUntil is null or t.leaseUntil <= :now) and t.id > :afterId order by t.id")
    List<Long> findDueIds(LocalDateTime now, Long afterId, Pageable page);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from BankTransfer t where t.id = :id")
    Optional<BankTransfer> findByIdForUpdate(Long id);

    Optional<BankTransfer> findByTransactionOperationId(Long operationId);
    Optional<BankTransfer> findByTransactionId(Long transactionId);
}
