package com.example.demo.wallet.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.demo.wallet.entity.Wallet;

import jakarta.persistence.LockModeType;

/** Locking methods require the caller's read/write transaction, covering balances and related records. */
public interface WalletRepository extends JpaRepository<Wallet, Long> {
    @Query("select w.id from Wallet w where w.id > :afterId order by w.id")
    java.util.List<Long> findIdsAfter(Long afterId, org.springframework.data.domain.Pageable page);
    @Query("select w.id from Wallet w where w.userId in :userIds order by w.id")
    java.util.List<Long> findIdsByUserIds(java.util.Collection<Long> userIds);
    Optional<Wallet> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    Optional<Wallet> findByIdAndUserId(Long id, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.id = :id")
    Optional<Wallet> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.userId = :userId")
    Optional<Wallet> findByUserIdForUpdate(@Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.id = :id AND w.userId = :userId")
    Optional<Wallet> findByIdAndUserIdForUpdate(@Param("id") Long id, @Param("userId") Long userId);

    /** Use the same ascending wallet ID order when a settlement touches multiple wallets. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.id IN :ids ORDER BY w.id")
    List<Wallet> findAllByIdInForUpdate(@Param("ids") Collection<Long> ids);
}
