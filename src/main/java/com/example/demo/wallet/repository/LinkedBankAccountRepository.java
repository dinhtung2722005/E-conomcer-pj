package com.example.demo.wallet.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import com.example.demo.wallet.entity.LinkedBankAccount;

public interface LinkedBankAccountRepository extends JpaRepository<LinkedBankAccount, Long> {
    List<LinkedBankAccount> findByWalletIdOrderByIdAsc(Long walletId);
    Optional<LinkedBankAccount> findByIdAndWalletId(Long id, Long walletId);
    Optional<LinkedBankAccount> findByProviderAndAccountToken(String provider, String accountToken);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from LinkedBankAccount a where a.id=:id and a.wallet.id=:walletId")
    Optional<LinkedBankAccount> findByIdAndWalletIdForUpdate(Long id, Long walletId);
}
