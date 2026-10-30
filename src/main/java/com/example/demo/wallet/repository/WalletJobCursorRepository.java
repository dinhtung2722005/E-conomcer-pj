package com.example.demo.wallet.repository;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import com.example.demo.wallet.entity.WalletJobCursor;
public interface WalletJobCursorRepository extends JpaRepository<WalletJobCursor,String> {
    @Modifying @Query(value="insert ignore into wallet_job_cursors (name,last_id,version) values (:name,0,0)",nativeQuery=true)
    int initialize(String name);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select c from WalletJobCursor c where c.name=:name")
    Optional<WalletJobCursor> lock(String name);
}
