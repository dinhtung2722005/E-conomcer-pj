package com.example.demo.wallet.repository;
import org.springframework.data.jpa.repository.*;
import com.example.demo.wallet.entity.WalletSecurityEvent;
public interface WalletSecurityEventRepository extends JpaRepository<WalletSecurityEvent, Long>,
        JpaSpecificationExecutor<WalletSecurityEvent> {}
