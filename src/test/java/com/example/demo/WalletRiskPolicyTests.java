package com.example.demo;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.example.demo.shared.error.*;
import com.example.demo.wallet.api.TransactionType;
import com.example.demo.wallet.config.WalletLimitProperties;
import com.example.demo.wallet.entity.*;
import com.example.demo.wallet.repository.WalletLimitReservationRepository;
import com.example.demo.wallet.service.WalletRiskService;

class WalletRiskPolicyTests {
    private final Instant now=Instant.parse("2026-10-31T17:01:00Z"); // 00:01 in Vietnam on November 1.
    private final WalletLimitReservationRepository rows=mock(WalletLimitReservationRepository.class);
    private final WalletLimitProperties policy=new WalletLimitProperties();
    private final Wallet wallet=Wallet.builder().id(1L).userId(1L).build();
    private final WalletOperation operation=WalletOperation.builder().wallet(wallet).type(TransactionType.DEPOSIT).build();
    private WalletRiskService service(){return new WalletRiskService(rows,policy,Clock.fixed(now,ZoneOffset.UTC));}
    private WalletLimitReservation yesterday(){return WalletLimitReservation.builder().amount(new BigDecimal("100000000"))
        .reservedAt(Instant.parse("2026-10-31T16:59:00Z")).build();}

    @Test void dailyQuotaResetsAtVietnamMidnightWhileRecentWindowSpansBothDays(){
        when(rows.window(eq(1L),eq(TransactionType.DEPOSIT),any())).thenReturn(List.of(yesterday()));
        assertDoesNotThrow(()->service().reserve(wallet,operation,new BigDecimal("50000000")));
        verify(rows).window(1L,TransactionType.DEPOSIT,Instant.parse("2026-10-31T16:51:00Z"));
        verify(rows).saveAndFlush(any(WalletLimitReservation.class));
    }
    @Test void midnightDoesNotResetTheSlidingRequestRate(){
        policy.setMaxRequests(1);when(rows.window(eq(1L),eq(TransactionType.DEPOSIT),any())).thenReturn(List.of(yesterday()));
        assertEquals(ErrorCode.WALLET_REQUEST_RATE_LIMIT,assertThrows(AppException.class,
            ()->service().reserve(wallet,operation,BigDecimal.ONE)).getErrorCode());
        verify(rows,never()).saveAndFlush(any());
    }
}
