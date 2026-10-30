package com.example.demo.wallet.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.demo.wallet.entity.WalletBalanceType;
import com.example.demo.wallet.entity.WalletHold;
import com.example.demo.wallet.entity.WalletHoldStatus;
import com.example.demo.wallet.entity.WalletHoldType;

import jakarta.persistence.LockModeType;

public interface WalletHoldRepository extends JpaRepository<WalletHold, Long> {
    Optional<WalletHold> findByIdAndWalletId(Long id, Long walletId);

    Optional<WalletHold> findByWalletIdAndTypeAndReferenceIdAndBalanceType(Long walletId, WalletHoldType type,
            String referenceId, WalletBalanceType balanceType);

    List<WalletHold> findByWalletIdAndOrderIdOrderByIdAsc(Long walletId, Long orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT h FROM WalletHold h WHERE h.id = :id AND h.wallet.id = :walletId")
    Optional<WalletHold> findByIdAndWalletIdForUpdate(@Param("id") Long id, @Param("walletId") Long walletId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT h FROM WalletHold h WHERE h.wallet.id = :walletId AND h.type = :type "
            + "AND h.referenceId = :referenceId AND h.balanceType = :balanceType")
    Optional<WalletHold> findByReferenceForUpdate(@Param("walletId") Long walletId,
            @Param("type") WalletHoldType type, @Param("referenceId") String referenceId,
            @Param("balanceType") WalletBalanceType balanceType);

    /** Lock both CASH/BONUS holds in a consistent order before canceling or settling an order. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT h FROM WalletHold h WHERE h.wallet.id = :walletId AND h.order.id = :orderId ORDER BY h.id")
    List<WalletHold> findByWalletIdAndOrderIdForUpdate(@Param("walletId") Long walletId, @Param("orderId") Long orderId);

    Page<WalletHold> findByWalletIdAndStatusInOrderByCreatedAtDescIdDesc(Long walletId,
            Collection<WalletHoldStatus> statuses, Pageable pageable);
}
