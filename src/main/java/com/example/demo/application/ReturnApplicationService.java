package com.example.demo.application;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.*;
import com.example.demo.user.api.UserAccountsApi;
import com.example.demo.ecommerce.api.OrderLifecycleApi;
import com.example.demo.wallet.api.*;
import com.example.demo.wallet.api.dto.*;
import lombok.RequiredArgsConstructor;

@Service @RequiredArgsConstructor @Transactional(isolation=Isolation.READ_COMMITTED)
public class ReturnApplicationService implements ReturnUseCase {
    private final UserAccountsApi users;
    private final OrderLifecycleApi orders;
    private final WalletReturnsApi wallets;
    @Override public RefundCaseResponse requestRefund(Long id,RefundCaseRequest request){
        var actor=users.requireCurrent(false);return wallets.request(orders.lock(id,actor.getId()),request,false);
    }
    @Override @PreAuthorize("hasRole('ADMIN')") public RefundCaseResponse requestAdminRefund(Long id,RefundCaseRequest request){
        users.requireCurrent(true);return wallets.request(orders.lock(id,null),request,true);
    }
    @Override public RefundCaseResponse getMyRefund(Long id){var actor=users.requireCurrent(false);orders.lock(id,actor.getId());return wallets.get(id);}
    @Override @PreAuthorize("hasRole('ADMIN')") public RefundCaseResponse decide(Long id,RefundDecisionRequest request){
        var actor=users.requireCurrent(true);var order=orders.lock(id,null);var decision=wallets.decide(order,actor,request);
        if(decision.newlyApproved())orders.completeReturn(id,decision.lines().stream().map(l->new OrderLifecycleApi.ReturnedQuantity(l.orderItemId(),l.quantity())).toList(),decision.amount(),decision.fullyReturned());
        return decision.response();
    }
}
