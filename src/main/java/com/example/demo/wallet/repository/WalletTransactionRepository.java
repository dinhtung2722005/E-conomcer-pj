package com.example.demo.wallet.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.demo.wallet.entity.TransactionStatus;
import com.example.demo.wallet.entity.TransactionType;
import com.example.demo.wallet.entity.WalletTransaction;

import jakarta.persistence.LockModeType;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {
    Optional<WalletTransaction> findByIdAndWalletId(Long id, Long walletId);

    Optional<WalletTransaction> findByWalletIdAndIdempotencyKey(Long walletId, String idempotencyKey);

    Optional<WalletTransaction> findByProviderAndProviderTransactionId(String provider, String providerTransactionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM WalletTransaction t WHERE t.id = :id AND t.wallet.id = :walletId")
    Optional<WalletTransaction> findByIdAndWalletIdForUpdate(@Param("id") Long id, @Param("walletId") Long walletId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM WalletTransaction t WHERE t.wallet.id = :walletId AND t.idempotencyKey = :key")
    Optional<WalletTransaction> findByWalletIdAndIdempotencyKeyForUpdate(@Param("walletId") Long walletId,
            @Param("key") String idempotencyKey);

    /** Backend reconciliation/callback use only; the service must authenticate the provider. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM WalletTransaction t WHERE t.provider = :provider AND t.providerTransactionId = :providerId")
    Optional<WalletTransaction> findByProviderAndProviderTransactionIdForUpdate(@Param("provider") String provider,
            @Param("providerId") String providerTransactionId);

    /** Null filters are omitted. Time interval is [fromInclusive, toExclusive). */
    @Query("SELECT t FROM WalletTransaction t WHERE t.wallet.id = :walletId "
            + "AND (:type IS NULL OR t.type = :type) "
            + "AND (:status IS NULL OR t.status = :status) "
            + "AND (:fromInclusive IS NULL OR t.createdAt >= :fromInclusive) "
            + "AND (:toExclusive IS NULL OR t.createdAt < :toExclusive) "
            + "ORDER BY t.createdAt DESC, t.id DESC")
    Page<WalletTransaction> findHistory(@Param("walletId") Long walletId, @Param("type") TransactionType type,
            @Param("status") TransactionStatus status, @Param("fromInclusive") LocalDateTime fromInclusive,
            @Param("toExclusive") LocalDateTime toExclusive, Pageable pageable);

    List<WalletTransaction> findByWalletIdAndReferenceIdOrderByIdAsc(Long walletId, String referenceId);

    /**
     * Lock the original operation first, then use this current locking read before computing
     * total refunds/reversals. Include PENDING, PROCESSING and SUCCESS in reserved amounts.
     * Reading existing children alone cannot serialize creation of a new refund.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM WalletTransaction t WHERE t.originalTransaction.id = :originalId "
            + "AND t.type = :type ORDER BY t.id")
    List<WalletTransaction> findRelatedTransactionsForUpdate(@Param("originalId") Long originalTransactionId,
            @Param("type") TransactionType type);

    /** Bounded scan for reconciliation; a stale request is not automatically a failed payment. */
    Slice<WalletTransaction> findByStatusInAndCreatedAtBeforeOrderByCreatedAtAscIdAsc(
            Collection<TransactionStatus> statuses, LocalDateTime cutoff, Pageable pageable);
}
