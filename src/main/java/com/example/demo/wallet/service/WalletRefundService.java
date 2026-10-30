package com.example.demo.wallet.service;

import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.access.prepost.PreAuthorize;
import com.example.demo.wallet.dto.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;

@Service @Validated @RequiredArgsConstructor
public class WalletRefundService {
    private final WalletReturnService returns;
    public RefundCaseResponse requestRefund(@NotNull @Positive Long orderId,@NotNull @Valid RefundCaseRequest request){return returns.request(orderId,request,false);}
    @PreAuthorize("hasRole('ADMIN')")
    public RefundCaseResponse requestAdminRefund(@NotNull @Positive Long orderId,@NotNull @Valid RefundCaseRequest request){return returns.request(orderId,request,true);}
    public RefundCaseResponse getMyRefund(@NotNull @Positive Long orderId){return returns.get(orderId);}
    @PreAuthorize("hasRole('ADMIN')")
    public RefundCaseResponse decide(@NotNull @Positive Long orderId,@NotNull @Valid RefundDecisionRequest request){return returns.decide(orderId,request);}
}