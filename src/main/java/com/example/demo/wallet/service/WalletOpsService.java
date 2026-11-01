package com.example.demo.wallet.service;
import com.example.demo.wallet.api.AlertStatus;
import com.example.demo.wallet.api.RefundStatus;
import com.example.demo.wallet.api.TransactionStatus;
import com.example.demo.shared.error.ErrorCode;
import com.example.demo.shared.error.AppException;
import com.example.demo.wallet.api.dto.WalletOpsDtos;
import com.example.demo.wallet.api.dto.WalletFreezeRequest;
import com.example.demo.wallet.api.dto.WalletAuditQuery;
import com.example.demo.wallet.api.dto.WalletAlertReviewRequest;
import com.example.demo.shared.web.PageResponse;
import com.example.demo.shared.web.PageQuery;
import java.time.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import com.example.demo.user.api.dto.*;
import com.example.demo.shared.web.*;
import com.example.demo.shared.error.*;
import com.example.demo.ecommerce.api.OrderLifecycleApi;
import com.example.demo.wallet.api.dto.*;
import com.example.demo.wallet.entity.*;
import com.example.demo.wallet.repository.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;

@Service @Validated @RequiredArgsConstructor @PreAuthorize("hasRole('ADMIN')")
public class WalletOpsService {
    private final WalletAdminAccess access;
    private final BankTransferRepository transfers;
    private final WalletHoldRepository holds;
    private final WalletRefundCaseRepository refunds;
    private final OrderLifecycleApi orders;
    private final WalletAlertRepository alerts;
    private final WalletOpsAuditRepository audit;
    private final WalletSecurityEventRepository securityEvents;
    private final WalletJournalRepository journals;
    @Value("${wallet.ops.transfer-sla:PT48H}") private Duration transferSla;
    @Value("${wallet.ops.refund-sla:PT48H}") private Duration refundSla;
    @Transactional(readOnly=true) public WalletOpsDtos.Summary summary() {
        access.requireAdmin(); return new WalletOpsDtos.Summary(transfers.countByFinishedFalse(),
            refunds.countByStatus(RefundStatus.REQUESTED),alerts.countByStatus(AlertStatus.OPEN),journals.countUnbalancedJournals());
    }
    @Transactional(readOnly=true) public PageResponse<WalletOpsDtos.Transfer> transfers(@Valid @NotNull PageQuery page,TransactionStatus status,boolean onlyOverdue) {
        access.requireAdmin(); var cutoff=LocalDateTime.now().minus(transferSla);
        return PageResponse.from(transfers.findAll((r,q,b)->b.and(status==null?b.conjunction():b.equal(r.get("transaction").get("status"),status),
            onlyOverdue?b.and(b.isFalse(r.get("finished")),b.lessThan(r.get("createdAt"),cutoff)):b.conjunction()),page.toPageable()).map(t-> {
                var x=t.getTransaction(); return new WalletOpsDtos.Transfer(t.getId(),x.getId(),x.getWallet().getId(),x.getType(),x.getAmount(),x.getWallet().getCurrency(),
                    x.getStatus(),t.isSuspended(),t.isFinished(),t.getAttempts(),t.getLastError(),t.getCreatedAt(),t.getNextAttemptAt(),t.getDispatchStartedAt(),!t.isFinished()&&t.getCreatedAt().isBefore(cutoff)); }));
    }
    @Transactional(readOnly=true) public PageResponse<WalletOpsDtos.Held> holds(@Valid @NotNull PageQuery page) {
        access.requireAdmin(); return PageResponse.from(holds.findAll((r,q,b)->b.greaterThan(b.diff(b.diff(r.<java.math.BigDecimal>get("amount"),r.<java.math.BigDecimal>get("capturedAmount")),r.<java.math.BigDecimal>get("releasedAmount")),java.math.BigDecimal.ZERO),page.toPageable()).map(h->
            new WalletOpsDtos.Held(h.getId(),h.getWallet().getId(),h.getOrderId(),h.getType(),h.getBalanceType(),h.getRemainingAmount(),h.getStatus(),h.getCreatedAt(),h.getCreatedAt().isBefore(LocalDateTime.now().minusDays(9)))));
    }
    @Transactional(readOnly=true) public PageResponse<WalletOpsDtos.Refund> refunds(@Valid @NotNull PageQuery page,RefundStatus status) {
        access.requireAdmin(); return PageResponse.from(refunds.findAll((r,q,b)->status==null?b.conjunction():b.equal(r.get("status"),status),page.toPageable()).map(c->
            new WalletOpsDtos.Refund(c.getId(),c.getOrderId(),c.getStatus(),c.getReason(),c.getCreatedAt(),c.getReviewedAt(),c.getStatus()==RefundStatus.REQUESTED&&c.getCreatedAt().isBefore(LocalDateTime.now().minus(refundSla)))));
    }
    @Transactional(readOnly=true) public PageResponse<WalletOpsDtos.Settlement> settlements(@Valid @NotNull PageQuery page) {
        access.requireAdmin(); var result=orders.unsettled(page);
        return new PageResponse<>(result.getCurrentPage(),result.getTotalPages(),result.getTotalElements(),result.getData().stream().map(o->
            new WalletOpsDtos.Settlement(o.orderId(),o.amount(),o.availableAt(),o.settledAt(),o.nextAttemptAt(),o.attempts(),o.lastError(),o.slaBreached())).toList());
    }
    @Transactional(readOnly=true) public PageResponse<WalletOpsDtos.Alert> alerts(@Valid @NotNull PageQuery page,AlertStatus status) {
        access.requireAdmin(); return PageResponse.from(alerts.findAll((r,q,b)->status==null?b.conjunction():b.equal(r.get("status"),status),page.toPageable()).map(this::alert));
    }
    @Transactional(readOnly=true) public PageResponse<WalletOpsDtos.Audit> audit(@Valid @NotNull WalletAuditQuery query) {
        access.requireAdmin();
        return PageResponse.from(audit.findAll(auditFilter(query),query.toPageable()).map(a->new WalletOpsDtos.Audit(a.getId(),a.getActorId(),a.getWalletId(),a.getAction(),a.getReference(),a.getReason(),a.getRequestId(),a.getCreatedAt())));
    }
    @Transactional(readOnly=true) public PageResponse<WalletOpsDtos.SecurityEvent> securityEvents(@Valid @NotNull WalletAuditQuery query) {
        access.requireAdmin();
        return PageResponse.from(securityEvents.findAll(auditFilter(query),query.toPageable()).map(a->new WalletOpsDtos.SecurityEvent(a.getId(),a.getActorId(),a.getWalletId(),a.getAction().name(),a.getOutcome().name(),a.getCode(),a.getReference(),a.getRequestId(),a.getCreatedAt())));
    }
    private <T> org.springframework.data.jpa.domain.Specification<T> auditFilter(WalletAuditQuery query) {
        return (root,q,b)->{
            var filters=new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if(query.getActorId()!=null)filters.add(b.equal(root.get("actorId"),query.getActorId()));
            if(query.getWalletId()!=null)filters.add(b.equal(root.get("walletId"),query.getWalletId()));
            if(query.getAction()!=null)filters.add(b.equal(root.get("action").as(String.class),query.getAction()));
            if(query.getReference()!=null)filters.add(b.equal(root.get("reference"),query.getReference()));
            if(query.getRequestId()!=null)filters.add(b.equal(root.get("requestId"),query.getRequestId()));
            if(query.getFrom()!=null)filters.add(b.greaterThanOrEqualTo(root.get("createdAt"),query.getFrom()));
            if(query.getTo()!=null)filters.add(b.lessThan(root.get("createdAt"),query.getTo()));
            if(root.getJavaType()==WalletSecurityEvent.class&&query.getOutcome()!=null)filters.add(b.equal(root.get("outcome").as(String.class),query.getOutcome()));
            return b.and(filters.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }
    @Transactional public WalletOpsDtos.Alert review(@Positive Long id,@Valid @NotNull WalletAlertReviewRequest request) {
        var actor=access.requireAdmin(); var a=alerts.findById(id).orElseThrow(()->new AppException(ErrorCode.ENDPOINT_NOT_FOUND));
        a.setStatus(request.getStatus()); a.setReviewedBy(actor.getId()); a.setReviewedAt(LocalDateTime.now()); a.setReviewReason(request.getReason().strip());
        audit.saveAndFlush(WalletOpsAudit.builder().actorId(actor.getId()).action("ALERT_REVIEW").reference(id.toString()).reason(request.getReason().strip()).build());
        return alert(alerts.saveAndFlush(a));
    }
    @Transactional public void retryTransfer(@Positive Long id,@Valid @NotNull WalletFreezeRequest request) {
        var actor=access.requireAdmin(); var transfer=transfers.findByIdForUpdate(id).orElseThrow(()->new AppException(ErrorCode.WALLET_TRANSACTION_NOT_FOUND));
        if(transfer.isFinished() || transfer.getLeaseUntil()!=null&&transfer.getLeaseUntil().isAfter(LocalDateTime.now())) throw new AppException(ErrorCode.DATA_CONFLICT);
        transfer.setNextAttemptAt(LocalDateTime.now());
        audit.saveAndFlush(WalletOpsAudit.builder().actorId(actor.getId()).walletId(transfer.getTransaction().getWallet().getId()).action("BANK_RETRY").reference(id.toString()).reason(request.getReason().strip()).build());
    }
    @Transactional public void retrySettlement(@Positive Long id,@Valid @NotNull WalletFreezeRequest request) {
        var actor=access.requireAdmin(); orders.scheduleSettlementRetry(id);
        audit.saveAndFlush(WalletOpsAudit.builder().actorId(actor.getId()).action("SETTLEMENT_RETRY").reference(id.toString()).reason(request.getReason().strip()).build());
    }
    private WalletOpsDtos.Alert alert(WalletAlert a) { return new WalletOpsDtos.Alert(a.getId(),a.getCategory(),a.getReference(),a.getMessage(),a.getStatus(),a.getCreatedAt(),a.getLastSeenAt(),a.getReviewedBy(),a.getReviewReason()); }
}
