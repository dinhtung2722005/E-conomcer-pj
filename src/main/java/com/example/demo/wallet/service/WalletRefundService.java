package com.example.demo.wallet.service;
import com.example.demo.wallet.api.dto.RefundDecisionRequest;
import com.example.demo.wallet.api.dto.RefundCaseResponse;
import com.example.demo.wallet.api.dto.RefundCaseRequest;

import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.access.prepost.PreAuthorize;
import com.example.demo.wallet.api.dto.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;

@Service @Validated @RequiredArgsConstructor
public class WalletRefundService {
    private final com.example.demo.wallet.api.ReturnUseCase returns;
    public RefundCaseResponse requestRefund(@NotNull @Positive Long orderId,@NotNull @Valid RefundCaseRequest request){return returns.requestRefund(orderId,request);}
    @PreAuthorize("hasRole('ADMIN')")
    public RefundCaseResponse requestAdminRefund(@NotNull @Positive Long orderId,@NotNull @Valid RefundCaseRequest request){return returns.requestAdminRefund(orderId,request);}
    public RefundCaseResponse getMyRefund(@NotNull @Positive Long orderId){return returns.getMyRefund(orderId);}
    @PreAuthorize("hasRole('ADMIN')")
    public RefundCaseResponse decide(@NotNull @Positive Long orderId,@NotNull @Valid RefundDecisionRequest request){return returns.decide(orderId,request);}
}