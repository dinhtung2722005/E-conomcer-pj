package com.example.demo.wallet.repository;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.*;
import com.example.demo.wallet.entity.WalletAlert;
public interface WalletAlertRepository extends JpaRepository<WalletAlert,Long>,JpaSpecificationExecutor<WalletAlert> {
    @Modifying @Query(value="insert into wallet_alerts (alert_key,category,reference,message,status,created_at,last_seen_at,version) "
        +"values (:key,:category,:reference,:message,'OPEN',:now,:now,0) on duplicate key update "
        +"last_seen_at=:now, message=:message, status=if(status='RESOLVED','OPEN',status),version=version+1",nativeQuery=true)
    int record(String key,String category,String reference,String message,LocalDateTime now);
    long countByStatus(WalletAlert.Status status);
}
