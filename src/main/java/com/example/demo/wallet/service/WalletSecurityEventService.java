package com.example.demo.wallet.service;

import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import com.example.demo.wallet.entity.WalletSecurityEvent;
import com.example.demo.wallet.entity.WalletSecurityEvent.*;
import com.example.demo.wallet.repository.WalletSecurityEventRepository;
import lombok.RequiredArgsConstructor;

@Service @RequiredArgsConstructor
public class WalletSecurityEventService {
    private final WalletSecurityEventRepository events;

    /** Counter/security changes and their event must commit together. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Long actorId, Long walletId, Action action, Outcome outcome, String code, String reference) {
        events.saveAndFlush(WalletSecurityEvent.builder().actorId(actorId).walletId(walletId).action(action)
                .outcome(outcome).code(code).reference(reference).requestId(MDC.get("requestId")).build());
    }

    /** Call only after the rejected business transaction has ended, with no money locks held. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void rejection(Long actorId, Long walletId, Action action, String code, String reference) {
        record(actorId, walletId, action, Outcome.REJECTED, code, reference);
    }
}
