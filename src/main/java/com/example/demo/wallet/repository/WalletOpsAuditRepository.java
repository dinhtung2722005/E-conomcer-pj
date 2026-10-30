package com.example.demo.wallet.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import com.example.demo.wallet.entity.WalletOpsAudit;
public interface WalletOpsAuditRepository extends JpaRepository<WalletOpsAudit,Long> {}
