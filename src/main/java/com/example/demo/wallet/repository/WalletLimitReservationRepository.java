package com.example.demo.wallet.repository;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import com.example.demo.wallet.api.TransactionType;
import com.example.demo.wallet.entity.WalletLimitReservation;

public interface WalletLimitReservationRepository extends JpaRepository<WalletLimitReservation,Long> {
    long countByWalletId(Long walletId);
    // Current read: an earlier order/route query may have opened a stale MySQL snapshot.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from WalletLimitReservation r where r.wallet.id=:walletId and r.type=:type and r.reservedAt>=:since order by r.id")
    List<WalletLimitReservation> window(Long walletId,TransactionType type,Instant since);
}
