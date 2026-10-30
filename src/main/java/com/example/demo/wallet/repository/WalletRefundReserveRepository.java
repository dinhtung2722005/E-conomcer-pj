package com.example.demo.wallet.repository;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import com.example.demo.wallet.entity.WalletRefundReserve;
public interface WalletRefundReserveRepository extends JpaRepository<WalletRefundReserve,String> {
    @Modifying @Query(value="insert ignore into wallet_refund_reserves (currency,balance,version) values (:currency,0,0)",nativeQuery=true) int initialize(String currency);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select r from WalletRefundReserve r where r.currency=:currency") Optional<WalletRefundReserve> lock(String currency);
}
