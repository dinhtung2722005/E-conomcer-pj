package com.example.demo.wallet.repository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import com.example.demo.wallet.entity.WalletPinRecovery;
public interface WalletPinRecoveryRepository extends JpaRepository<WalletPinRecovery,String> {
    long countByWalletIdAndCreatedAtAfter(Long walletId,LocalDateTime since);
    Optional<WalletPinRecovery> findFirstByWalletIdOrderByCreatedAtDesc(Long walletId);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select r from WalletPinRecovery r where r.id=:id and r.wallet.id=:walletId")
    Optional<WalletPinRecovery> lock(String id,Long walletId);
    @Modifying @Query("update WalletPinRecovery r set r.consumedAt=:now where r.wallet.id=:walletId and r.consumedAt is null")
    int invalidate(Long walletId,LocalDateTime now);
}
