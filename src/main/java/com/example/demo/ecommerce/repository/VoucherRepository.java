package com.example.demo.ecommerce.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.demo.ecommerce.entity.Voucher;

import jakarta.persistence.LockModeType;

public interface VoucherRepository extends JpaRepository<Voucher, Long> {

    Optional<Voucher> findByCode(String code);

    boolean existsByCode(String code);

    boolean existsByCodeIgnoreCase(String code);

    /** Khóa bi quan khi áp dụng voucher để hai đơn không cùng vượt hạn mức sử dụng. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM Voucher v WHERE v.code = :code")
    Optional<Voucher> findByCodeForUpdate(@Param("code") String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM Voucher v WHERE v.id = :id")
    Optional<Voucher> findByIdForUpdate(@Param("id") Long id);

    /** Voucher đang trong thời gian hiệu lực và còn lượt. */
    @Query("SELECT v FROM Voucher v WHERE v.startDate <= :now AND v.endDate >= :now "
            + "AND v.usedQuantity < v.totalQuantity ORDER BY v.endDate ASC")
    List<Voucher> findAvailableVouchers(@Param("now") LocalDateTime now);
}
