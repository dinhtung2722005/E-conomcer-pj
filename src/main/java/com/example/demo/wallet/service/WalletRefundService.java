package com.example.demo.wallet.service;

import java.time.LocalDateTime;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import com.example.demo.ecommerce.entity.Order;
import com.example.demo.ecommerce.entity.OrderStatus;
import com.example.demo.ecommerce.repository.OrderRepository;
import com.example.demo.ecommerce.service.OrderService;
import com.example.demo.user.entity.UserRole;
import com.example.demo.user.exception.*;
import com.example.demo.user.service.CurrentUserService;
import com.example.demo.wallet.dto.*;
import com.example.demo.wallet.entity.WalletRefundCase;
import com.example.demo.wallet.repository.WalletRefundCaseRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;

@Service @Validated @RequiredArgsConstructor
public class WalletRefundService {
    private final OrderRepository orders;
    private final OrderService orderService;
    private final WalletRefundCaseRepository cases;
    private final CurrentUserService currentUser;

    @Transactional
    public RefundCaseResponse requestRefund(@NotNull @Positive Long orderId, @NotNull @Valid RefundCaseRequest request) {
        Order order = ownOrder(orderId);
        var existing = cases.findByOrderId(orderId);
        if (existing.isPresent()) {
            if (!existing.get().getReason().equals(request.getReason().strip())) throw new AppException(ErrorCode.DATA_CONFLICT);
            return response(existing.get());
        }
        if (order.getStatus() != OrderStatus.COMPLETED || order.getSettledAt() != null
                || order.getSettlementAvailableAt() == null || !LocalDateTime.now().isBefore(order.getSettlementAvailableAt())) {
            throw new AppException(ErrorCode.ORDER_STATUS_INVALID, "Đơn không còn trong thời hạn yêu cầu hoàn hàng");
        }
        return response(cases.saveAndFlush(WalletRefundCase.builder().order(order).reason(request.getReason().strip()).build()));
    }

    @Transactional
    public RefundCaseResponse getMyRefund(@NotNull @Positive Long orderId) {
        ownOrder(orderId);
        return response(cases.findByOrderId(orderId).orElseThrow(() -> new AppException(ErrorCode.WALLET_REFUND_NOT_FOUND)));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public RefundCaseResponse decide(@NotNull @Positive Long orderId, @NotNull @Valid RefundDecisionRequest request) {
        var actor = currentUser.getCurrentUser();
        if (!actor.isActive()) throw new AppException(ErrorCode.USER_LOCKED);
        if (actor.getRole() != UserRole.ADMIN) throw new AppException(ErrorCode.ACCESS_DENIED);
        Order order = orders.findByIdForUpdate(orderId).orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));
        var row = cases.findByOrderId(orderId).orElseThrow(() -> new AppException(ErrorCode.WALLET_REFUND_NOT_FOUND));
        var target = request.getApproved() ? WalletRefundCase.Status.APPROVED : WalletRefundCase.Status.REJECTED;
        if (row.getStatus() != WalletRefundCase.Status.REQUESTED) {
            if (row.getStatus() != target || !row.getDecisionReason().equals(request.getReason().strip())) {
                throw new AppException(ErrorCode.DATA_CONFLICT);
            }
            return response(row);
        }
        if (request.getApproved()) orderService.refundReturnedOrder(order);
        row.setStatus(target); row.setReviewerId(actor.getId()); row.setDecisionReason(request.getReason().strip());
        row.setReviewedAt(LocalDateTime.now());
        return response(cases.saveAndFlush(row));
    }

    private Order ownOrder(Long orderId) {
        var user = currentUser.getCurrentUser();
        if (!user.isActive()) throw new AppException(ErrorCode.USER_LOCKED);
        return orders.findByIdAndUserIdForUpdate(orderId, user.getId()).orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));
    }

    private RefundCaseResponse response(WalletRefundCase row) {
        var result = new RefundCaseResponse();
        result.setId(row.getId()); result.setOrderId(row.getOrder().getId()); result.setStatus(row.getStatus());
        result.setReason(row.getReason()); result.setDecisionReason(row.getDecisionReason());
        result.setCreatedAt(row.getCreatedAt()); result.setReviewedAt(row.getReviewedAt());
        return result;
    }
}
