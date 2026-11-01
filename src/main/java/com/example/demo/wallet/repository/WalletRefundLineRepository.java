package com.example.demo.wallet.repository;
import java.util.List;
import org.springframework.data.jpa.repository.*;
import com.example.demo.wallet.entity.WalletRefundLine;
public interface WalletRefundLineRepository extends JpaRepository<WalletRefundLine,Long> {
    @Query("select l from WalletRefundLine l where l.refundCase.orderId=:orderId and l.refundCase.reviewedAt is not null and l.refundCase.status='APPROVED' order by l.id")
    List<WalletRefundLine> approved(Long orderId);
}
