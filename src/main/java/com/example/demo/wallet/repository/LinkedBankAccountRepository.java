package com.example.demo.wallet.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.example.demo.wallet.entity.LinkedBankAccount;

public interface LinkedBankAccountRepository extends JpaRepository<LinkedBankAccount, Long> {
    List<LinkedBankAccount> findByWalletIdOrderByIdAsc(Long walletId);
    Optional<LinkedBankAccount> findByIdAndWalletId(Long id, Long walletId);
    Optional<LinkedBankAccount> findByProviderAndAccountToken(String provider, String accountToken);
}
