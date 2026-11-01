package com.example.demo.wallet.service;

import java.math.BigDecimal;
import java.time.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import com.example.demo.shared.error.*;
import com.example.demo.wallet.api.TransactionType;
import com.example.demo.wallet.config.WalletLimitProperties;
import com.example.demo.wallet.entity.*;
import com.example.demo.wallet.repository.WalletLimitReservationRepository;

@Service @Transactional(propagation=Propagation.MANDATORY)
public class WalletRiskService {
    private final WalletLimitReservationRepository reservations;
    private final WalletLimitProperties policy;
    private final Clock clock;
    private final ZoneId zone;

    public WalletRiskService(WalletLimitReservationRepository reservations,WalletLimitProperties policy,
            @Qualifier("walletRiskClock") Clock clock) {
        this.reservations=reservations; this.policy=policy; this.clock=clock;
        zone=ZoneId.of(policy.getZone());
        if(policy.getRequestWindow().isNegative()||policy.getRequestWindow().isZero()
                ||policy.getRequestWindow().compareTo(Duration.ofHours(24))>0)
            throw new IllegalArgumentException("Wallet request window must be positive and at most 24 hours");
    }

    /** Caller holds the wallet write lock. Call only after checking idempotency; all writes roll back together. */
    public void reserve(Wallet wallet,WalletOperation operation,BigDecimal amount) {
        TransactionType type=operation.getType();
        boolean external=type==TransactionType.DEPOSIT||type==TransactionType.WITHDRAW;
        if(amount==null||amount.signum()<0||external&&amount.signum()==0
                ||amount.stripTrailingZeros().scale()>4
                ||external&&"VND".equals(wallet.getCurrency())&&amount.stripTrailingZeros().scale()>0)
            throw new AppException(ErrorCode.WALLET_AMOUNT_INVALID);
        var limit=switch(type) {
            case DEPOSIT -> policy.getTopUp(); case WITHDRAW -> policy.getWithdraw(); case PAYMENT -> policy.getPayment();
            default -> throw new IllegalArgumentException("Unsupported wallet quota operation");
        };
        if(amount.compareTo(limit.getPerTransaction())>0)throw new AppException(ErrorCode.WALLET_TRANSACTION_LIMIT);
        Instant now=clock.instant(), cutoff=now.minus(policy.getRequestWindow());
        Instant dayStart=now.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant();
        var recent=reservations.window(wallet.getId(),type,dayStart.isBefore(cutoff)?dayStart:cutoff);
        if(recent.stream().filter(r->!r.getReservedAt().isBefore(cutoff)).count()>=policy.getMaxRequests())
            throw new AppException(ErrorCode.WALLET_REQUEST_RATE_LIMIT);
        BigDecimal used=recent.stream().filter(r->!r.getReservedAt().isBefore(dayStart))
                .map(WalletLimitReservation::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
        if(used.add(amount).compareTo(limit.getPerDay())>0)throw new AppException(ErrorCode.WALLET_DAILY_LIMIT);
        reservations.saveAndFlush(WalletLimitReservation.builder().wallet(wallet).operation(operation).type(type)
                .amount(amount).reservedAt(now).build());
    }
}
