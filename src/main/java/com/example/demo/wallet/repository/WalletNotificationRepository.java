package com.example.demo.wallet.repository;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.*;
import com.example.demo.wallet.entity.WalletNotification;
public interface WalletNotificationRepository extends JpaRepository<WalletNotification,Long> {
    Page<WalletNotification> findByWalletIdOrderByIdDesc(Long walletId,Pageable page);
    Optional<WalletNotification> findByIdAndWalletId(Long id,Long walletId);
    long countByEventKey(String key);
}
