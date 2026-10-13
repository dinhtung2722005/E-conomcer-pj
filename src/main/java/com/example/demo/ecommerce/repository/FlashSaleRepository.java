package com.example.demo.ecommerce.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.demo.ecommerce.entity.FlashSale;
import com.example.demo.ecommerce.entity.FlashSaleStatus;

public interface FlashSaleRepository extends JpaRepository<FlashSale, Long> {

    List<FlashSale> findByStatus(FlashSaleStatus status);

    Optional<FlashSale> findFirstByStatusAndStartTimeBeforeAndEndTimeAfter(
            FlashSaleStatus status,
            LocalDateTime currentTimeForStart,
            LocalDateTime currentTimeForEnd);

    /** Chương trình dùng cho job đồng bộ trạng thái. */
    @Query("SELECT f FROM FlashSale f WHERE (f.startTime <= :now AND f.endTime > :now AND f.status <> :ongoing) "
            + "OR (f.endTime <= :now AND f.status <> :ended)")
    List<FlashSale> findNeedingStatusSync(@Param("now") LocalDateTime now,
                                          @Param("ongoing") FlashSaleStatus ongoing,
                                          @Param("ended") FlashSaleStatus ended);
}
