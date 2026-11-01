package com.example.demo.ecommerce.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.demo.ecommerce.entity.FlashSaleItem;
import com.example.demo.ecommerce.api.FlashSaleStatus;

import jakarta.persistence.LockModeType;

@Repository
public interface FlashSaleItemRepository extends JpaRepository<FlashSaleItem, Long> {

    List<FlashSaleItem> findByFlashSaleId(Long flashSaleId);
    Page<FlashSaleItem> findByFlashSaleId(Long flashSaleId, Pageable pageable);

    boolean existsByFlashSaleIdAndProductId(Long flashSaleId, Long productId);

    Optional<FlashSaleItem> findByFlashSaleIdAndProductId(Long flashSaleId, Long productId);

    /**
     * Suất flash sale còn hiệu lực của một sản phẩm (đang trong khung giờ, còn lượt).
     *
     * <p>Điều kiện {@code soldQuantity < totalQuantity} là bắt buộc: nếu thiếu, giỏ hàng
     * vẫn hiển thị giá sốc dù đã bán hết suất.
     */
    @Query("SELECT fsi FROM FlashSaleItem fsi JOIN fsi.flashSale fs "
            + "WHERE fsi.product.id = :productId "
            + "AND fs.status = :status "
            + "AND :now BETWEEN fs.startTime AND fs.endTime "
            + "AND fsi.soldQuantity < fsi.totalQuantity")
    Optional<FlashSaleItem> findActiveItem(@Param("productId") Long productId,
                                           @Param("status") FlashSaleStatus status,
                                           @Param("now") LocalDateTime now);

    /** Như trên nhưng khóa bi quan — dùng khi trừ suất lúc đặt hàng. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT fsi FROM FlashSaleItem fsi JOIN fsi.flashSale fs "
            + "WHERE fsi.product.id = :productId "
            + "AND fs.status = :status "
            + "AND :now BETWEEN fs.startTime AND fs.endTime "
            + "AND fsi.soldQuantity < fsi.totalQuantity")
    Optional<FlashSaleItem> findActiveItemForUpdate(@Param("productId") Long productId,
                                                    @Param("status") FlashSaleStatus status,
                                                    @Param("now") LocalDateTime now);

    /** Lấy giá sốc của nhiều sản phẩm trong một truy vấn (tránh N+1). */
    @Query("SELECT fsi FROM FlashSaleItem fsi JOIN FETCH fsi.product JOIN fsi.flashSale fs "
            + "WHERE fsi.product.id IN :productIds "
            + "AND fs.status = :status "
            + "AND :now BETWEEN fs.startTime AND fs.endTime "
            + "AND fsi.soldQuantity < fsi.totalQuantity")
    List<FlashSaleItem> findActiveItemsByProductIds(@Param("productIds") Collection<Long> productIds,
                                                    @Param("status") FlashSaleStatus status,
                                                    @Param("now") LocalDateTime now);
}
