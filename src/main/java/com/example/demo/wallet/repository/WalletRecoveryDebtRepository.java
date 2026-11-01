package com.example.demo.wallet.repository;
import com.example.demo.wallet.api.WalletBalanceType;
import java.util.*;
import java.math.BigDecimal;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import com.example.demo.wallet.entity.*;
public interface WalletRecoveryDebtRepository extends JpaRepository<WalletRecoveryDebt,Long>,JpaSpecificationExecutor<WalletRecoveryDebt> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select d from WalletRecoveryDebt d where d.userId=:userId and d.balanceType=:type and d.currency=:currency and d.repaidAmount<d.amount order by d.id")
    List<WalletRecoveryDebt> outstanding(Long userId,WalletBalanceType type,String currency);
    @Query("select coalesce(sum(d.amount-d.repaidAmount),0) from WalletRecoveryDebt d where d.userId=:userId and d.balanceType=:type and d.currency=:currency")
    BigDecimal total(Long userId,WalletBalanceType type,String currency);
}
