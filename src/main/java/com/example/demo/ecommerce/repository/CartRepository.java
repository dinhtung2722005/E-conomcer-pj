package com.example.demo.ecommerce.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.demo.ecommerce.entity.Cart;

import jakarta.persistence.LockModeType;

@Repository
public interface CartRepository extends JpaRepository<Cart, Long> {

    Optional<Cart> findByUserId(Long userId);

    /**
     * Khóa bi quan dòng giỏ hàng.
     *
     * <p>Dùng cho mọi thao tác ghi để hai request đồng thời của cùng một người dùng
     * không thể cùng lúc thêm một sản phẩm và tạo ra hai dòng trùng nhau.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Cart c WHERE c.userId = :userId")
    Optional<Cart> findByUserIdForUpdate(@Param("userId") Long userId);
}
