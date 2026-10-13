package com.example.demo.ecommerce.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.demo.ecommerce.entity.Inventory;

import jakarta.persistence.LockModeType;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findByProductId(Long productId);

    /**
     * Khóa bi quan dòng tồn kho trước khi trừ/hoàn.
     *
     * <p>Không thể chỉ dựa vào {@code @Version}: khi hai đơn hàng cùng tranh một sản
     * phẩm, optimistic lock sẽ ném {@code OptimisticLockException} (HTTP 500) thay vì
     * xử lý tuần tự. Khóa bi quan khiến request thứ hai phải chờ request thứ nhất.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.product.id = :productId")
    Optional<Inventory> findByProductIdForUpdate(@Param("productId") Long productId);

    List<Inventory> findByProductIdIn(Collection<Long> productIds);
}
