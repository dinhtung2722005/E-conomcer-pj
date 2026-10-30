package com.example.demo.wallet.service;
import java.math.*;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.*;
import com.example.demo.ecommerce.entity.Order;
import com.example.demo.ecommerce.entity.OrderStatus;
import com.example.demo.ecommerce.repository.OrderRepository;
import com.example.demo.ecommerce.service.OrderService;
import com.example.demo.user.entity.*;
import com.example.demo.user.exception.*;
import com.example.demo.user.repository.UserRepository;
import com.example.demo.user.service.CurrentUserService;
import com.example.demo.wallet.dto.*;
import com.example.demo.wallet.entity.*;
import com.example.demo.wallet.repository.*;
import lombok.RequiredArgsConstructor;

@Service @RequiredArgsConstructor @Transactional(isolation=Isolation.READ_COMMITTED)
public class WalletReturnService {
    private final OrderRepository orders;private final OrderService orderService;private final UserRepository users;
    private final CurrentUserService currentUser;private final WalletRefundCaseRepository cases;
    private final WalletRefundLineRepository lines;private final WalletRepository wallets;private final WalletTransactionRepository transactions;
    private final WalletHoldRepository holds;private final WalletOperationService operations;private final WalletLedgerService ledger;
    private final WalletDebtService debts;
    public RefundCaseResponse request(Long id,RefundCaseRequest request,boolean adminException){
        var actor=actor(adminException);var order=order(id,actor,adminException);
        String canonical=request.getReason().strip()+"|"+(request.getItems()==null?"ALL":request.getItems().stream().sorted(Comparator.comparing(RefundCaseRequest.Line::orderItemId)).map(l->l.orderItemId()+":"+l.quantity()).toList().toString());
        String fingerprint=operations.fingerprint(canonical),key=request.getRequestKey()==null?"legacy:"+fingerprint:request.getRequestKey();
        var previous=cases.findByOrderIdAndRequestKey(id,key);if(previous.isPresent()){
            if(!previous.get().getFingerprint().equals(fingerprint))throw new AppException(ErrorCode.WALLET_IDEMPOTENCY_CONFLICT);return response(previous.get());
        }
        if(order.getStatus()!=OrderStatus.COMPLETED||!adminException&&(order.getSettledAt()!=null||order.getSettlementAvailableAt()==null||!LocalDateTime.now().isBefore(order.getSettlementAvailableAt())))throw new AppException(ErrorCode.ORDER_STATUS_INVALID);
        if(cases.findFirstByOrderIdOrderByIdDesc(id).filter(c->c.getStatus()==WalletRefundCase.Status.REQUESTED).isPresent())throw new AppException(ErrorCode.DATA_CONFLICT);
        var approved=lines.approved(id);Map<Long,Integer> returned=new HashMap<>();Map<Long,BigDecimal> amountReturned=new HashMap<>(),feeReturned=new HashMap<>();
        approved.forEach(l->{returned.merge(l.getItem().getId(),l.getQuantity(),Integer::sum);amountReturned.merge(l.getItem().getId(),l.getAmount(),BigDecimal::add);feeReturned.merge(l.getItem().getId(),l.getFeeAmount(),BigDecimal::add);});
        Map<Long,Integer> requested=new HashMap<>();if(request.getItems()!=null)for(var line:request.getItems())if(requested.put(line.orderItemId(),line.quantity())!=null)throw new AppException(ErrorCode.INVALID_REQUEST);
        var c=WalletRefundCase.builder().order(order).reason(request.getReason().strip()).requestKey(key).fingerprint(fingerprint).amount(BigDecimal.ZERO).feeAmount(BigDecimal.ZERO).build();
        for(var item:order.getItems()){
            int remaining=item.getQuantity()-returned.getOrDefault(item.getId(),0);
            int quantity=request.getItems()==null?remaining:requested.getOrDefault(item.getId(),0);requested.remove(item.getId());
            if(quantity==0)continue;if(quantity<0||quantity>remaining)throw new AppException(ErrorCode.INVALID_REQUEST);
            BigDecimal amount=quantity==remaining?item.getSettlementAmount().subtract(amountReturned.getOrDefault(item.getId(),BigDecimal.ZERO)):item.getSettlementAmount().multiply(BigDecimal.valueOf(quantity)).divide(BigDecimal.valueOf(item.getQuantity()),4,RoundingMode.DOWN);
            BigDecimal fee=quantity==remaining?item.getPlatformFee().subtract(feeReturned.getOrDefault(item.getId(),BigDecimal.ZERO)):item.getPlatformFee().multiply(BigDecimal.valueOf(quantity)).divide(BigDecimal.valueOf(item.getQuantity()),4,RoundingMode.DOWN);
            c.getLines().add(WalletRefundLine.builder().refundCase(c).item(item).quantity(quantity).amount(amount).feeAmount(fee).build());c.setAmount(c.getAmount().add(amount));c.setFeeAmount(c.getFeeAmount().add(fee));
        }
        if(!requested.isEmpty()||c.getLines().isEmpty()||c.getAmount().compareTo(order.getFinalAmount().subtract(order.getRefundedAmount()))>0)throw new AppException(ErrorCode.INVALID_REQUEST);
        return response(cases.saveAndFlush(c));
    }
    public RefundCaseResponse get(Long id){var actor=actor(false);order(id,actor,false);return response(cases.findFirstByOrderIdOrderByIdDesc(id).orElseThrow(()->new AppException(ErrorCode.WALLET_REFUND_NOT_FOUND)));}
    @PreAuthorize("hasRole('ADMIN')") public RefundCaseResponse decide(Long id,RefundDecisionRequest request){
        var actor=actor(true);var order=order(id,actor,true);
        if(request.getCaseId()==null&&cases.countByOrderId(id)>1)throw new AppException(ErrorCode.INVALID_REQUEST,"Phải chỉ định caseId khi đơn có nhiều hồ sơ hoàn");
        var c=(request.getCaseId()==null?cases.findFirstByOrderIdOrderByIdDesc(id):cases.findByIdAndOrderId(request.getCaseId(),id)).orElseThrow(()->new AppException(ErrorCode.WALLET_REFUND_NOT_FOUND));
        var target=request.getApproved()?WalletRefundCase.Status.APPROVED:WalletRefundCase.Status.REJECTED;
        if(c.getStatus()!=WalletRefundCase.Status.REQUESTED){if(c.getStatus()!=target||!request.getReason().strip().equals(c.getDecisionReason()))throw new AppException(ErrorCode.DATA_CONFLICT);return response(c);}
        if(request.getApproved())apply(order,c);
        c.setStatus(target);c.setReviewerId(actor.getId());c.setDecisionReason(request.getReason().strip());c.setReviewedAt(LocalDateTime.now());return response(cases.saveAndFlush(c));
    }
    private void apply(Order order,WalletRefundCase c){
        if(order.getStatus()!=OrderStatus.COMPLETED||c.getLines().isEmpty())throw new AppException(ErrorCode.ORDER_STATUS_INVALID);
        boolean settled=order.getSettledAt()!=null;Set<Long> owners=new HashSet<>();owners.add(order.getUserId());
        if(settled)c.getLines().forEach(l->{if(l.getAmount().signum()==0)return;if(l.getItem().getSellerId()==null)throw new AppException(ErrorCode.WALLET_ORDER_NOT_READY);owners.add(l.getItem().getSellerId());});
        var locked=wallets.findAllByIdInForUpdate(wallets.findIdsByUserIds(owners));Map<Long,Wallet> byUser=new HashMap<>();locked.forEach(w->byUser.put(w.getUserId(),w));
        if(byUser.size()!=owners.size())throw new AppException(ErrorCode.WALLET_ORDER_NOT_READY);
        Wallet buyer=byUser.get(order.getUserId());for(var w:locked){if(!w.getCurrency().equals(buyer.getCurrency()))throw new AppException(ErrorCode.INVALID_REQUEST);ledger.open(w);}
        var payments=transactions.findOrderPaymentsForUpdate(buyer.getId(),order.getId().toString());Map<Long,BigDecimal> remaining=new HashMap<>();BigDecimal cash=BigDecimal.ZERO,bonus=BigDecimal.ZERO;
        for(var p:payments){BigDecimal amount=p.getAmount().subtract(refunded(p));remaining.put(p.getId(),amount);if(p.getBalanceType()==WalletBalanceType.CASH)cash=cash.add(amount);else bonus=bonus.add(amount);}
        BigDecimal available=cash.add(bonus);if(c.getAmount().compareTo(available)>0)throw new AppException(ErrorCode.INVALID_REQUEST);
        BigDecimal cashReturn=available.signum()==0?BigDecimal.ZERO:c.getAmount().compareTo(available)==0?cash:c.getAmount().multiply(cash).divide(available,4,RoundingMode.DOWN);
        BigDecimal bonusReturn=c.getAmount().subtract(cashReturn),cashLeft=cashReturn,bonusLeft=bonusReturn;Map<String,BigDecimal> postings=new HashMap<>();
        if(settled)compensate(c,byUser,buyer,postings);
        for(var p:payments){BigDecimal amount=remaining.get(p.getId()).min(p.getBalanceType()==WalletBalanceType.CASH?cashLeft:bonusLeft);if(amount.signum()==0)continue;
            if(!settled){var hold=holds.findByIdAndWalletIdForUpdate(p.getHold().getId(),buyer.getId()).orElseThrow();if(hold.getRemainingAmount().compareTo(amount)<0)throw new AppException(ErrorCode.DATA_CONFLICT);hold.release(amount);
                if(p.getBalanceType()==WalletBalanceType.CASH)buyer.setPendingBalance(buyer.getPendingBalance().subtract(amount));else buyer.setPendingBonusBalance(buyer.getPendingBonusBalance().subtract(amount));
                add(postings,ledger.account(buyer,p.getBalanceType(),true),amount.negate());}
            transactions.saveAndFlush(WalletTransaction.builder().wallet(buyer).referenceId(order.getId().toString()).idempotencyKey("return:"+c.getId()+":"+p.getId()).type(TransactionType.REFUND).balanceType(p.getBalanceType()).amount(amount).originalTransaction(p).status(TransactionStatus.SUCCESS).completedAt(LocalDateTime.now()).build());
            if(p.getBalanceType()==WalletBalanceType.CASH)cashLeft=cashLeft.subtract(amount);else bonusLeft=bonusLeft.subtract(amount);
        }
        buyer.setBalance(buyer.getBalance().add(cashReturn));buyer.setBonusBalance(buyer.getBonusBalance().add(bonusReturn));add(postings,ledger.account(buyer,WalletBalanceType.CASH,false),cashReturn);add(postings,ledger.account(buyer,WalletBalanceType.BONUS,false),bonusReturn);
        if(c.getAmount().signum()>0)ledger.post("return:"+c.getId(),buyer.getCurrency(),postings);
        if(settled)recoverBonus(order,c,buyer);
        Map<Long,Integer> returned=new HashMap<>();lines.approved(order.getId()).forEach(l->returned.merge(l.getItem().getId(),l.getQuantity(),Integer::sum));c.getLines().forEach(l->returned.merge(l.getItem().getId(),l.getQuantity(),Integer::sum));
        boolean full=order.getItems().stream().allMatch(i->returned.getOrDefault(i.getId(),0).intValue()==i.getQuantity().intValue());
        order.setRefundedAmount(order.getRefundedAmount().add(c.getAmount()));orderService.applyApprovedReturn(order,c.getLines(),full);
    }
    private void compensate(WalletRefundCase c,Map<Long,Wallet> byUser,Wallet buyer,Map<String,BigDecimal> postings){
        Map<Long,BigDecimal> sellers=new TreeMap<>();c.getLines().forEach(l->{var net=l.getAmount().subtract(l.getFeeAmount());if(net.signum()>0)sellers.merge(l.getItem().getSellerId(),net,BigDecimal::add);});BigDecimal shortage=BigDecimal.ZERO;
        for(var entry:sellers.entrySet()){Wallet seller=byUser.get(entry.getKey());BigDecimal take=seller.getBalance().min(entry.getValue());seller.setBalance(seller.getBalance().subtract(take));add(postings,ledger.account(seller,WalletBalanceType.CASH,false),take.negate());
            BigDecimal missing=entry.getValue().subtract(take);shortage=shortage.add(missing);debts.create(seller.getUserId(),c.getId(),buyer.getCurrency(),WalletBalanceType.CASH,missing);
            if(take.signum()>0)transactions.saveAndFlush(WalletTransaction.builder().wallet(seller).referenceId(c.getOrder().getId().toString()).idempotencyKey("return-debit:"+c.getId()).type(TransactionType.DEBT_REPAYMENT).amount(take).status(TransactionStatus.SUCCESS).completedAt(LocalDateTime.now()).build());}
        if(shortage.signum()>0){var reserve=debts.reserve(buyer.getCurrency());if(reserve.getBalance().compareTo(shortage)<0)throw new AppException(ErrorCode.WALLET_REFUND_RESERVE_INSUFFICIENT);reserve.setBalance(reserve.getBalance().subtract(shortage));add(postings,"platform:REFUND_RESERVE",shortage.negate());}
        add(postings,"platform:FEES",c.getFeeAmount().negate());
    }
    private void recoverBonus(Order order,WalletRefundCase c,Wallet buyer){
        var original=transactions.findByWalletIdAndReferenceIdOrderByIdAsc(buyer.getId(),order.getId().toString()).stream().filter(t->t.getType()==TransactionType.CASHBACK&&t.getStatus()==TransactionStatus.SUCCESS).findFirst();
        if(original.isEmpty()||order.getSettledAmount().signum()==0)return;
        BigDecimal already=lines.approved(order.getId()).stream().map(WalletRefundLine::getRefundCase).distinct().map(WalletRefundCase::getBonusRecoveryAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal postSettlementReturned=order.getRefundedAmount().subtract(order.getFinalAmount().subtract(order.getSettledAmount()));
        BigDecimal expected=postSettlementReturned.add(c.getAmount()).compareTo(order.getSettledAmount())==0?original.get().getAmount().subtract(already):c.getAmount().multiply(original.get().getAmount()).divide(order.getSettledAmount(),4,RoundingMode.DOWN).min(original.get().getAmount().subtract(already));
        BigDecimal actual=buyer.getBonusBalance().min(expected);c.setBonusRecoveryAmount(expected);buyer.setBonusBalance(buyer.getBonusBalance().subtract(actual));
        if(actual.signum()>0){var reversal=transactions.saveAndFlush(WalletTransaction.builder().wallet(buyer).referenceId(order.getId().toString()).idempotencyKey("return-bonus:"+c.getId()).type(TransactionType.CASHBACK_REVERSAL).balanceType(WalletBalanceType.BONUS).amount(actual).originalTransaction(original.get()).status(TransactionStatus.SUCCESS).completedAt(LocalDateTime.now()).build());ledger.move("return-bonus:"+c.getId(),buyer.getCurrency(),ledger.account(buyer,WalletBalanceType.BONUS,false),"platform:PROMOTIONS",actual);}
        debts.create(buyer.getUserId(),c.getId(),buyer.getCurrency(),WalletBalanceType.BONUS,expected.subtract(actual));
    }
    private BigDecimal refunded(WalletTransaction p){return transactions.findRelatedTransactionsForUpdate(p.getId(),TransactionType.REFUND).stream().filter(t->t.getStatus()==TransactionStatus.SUCCESS||t.getStatus()==TransactionStatus.PENDING||t.getStatus()==TransactionStatus.PROCESSING).map(WalletTransaction::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add);}
    private User actor(boolean admin){var u=users.findByIdForUpdate(currentUser.getCurrentUserId()).orElseThrow(()->new AppException(ErrorCode.USER_NOT_FOUND));if(!u.isActive())throw new AppException(ErrorCode.USER_LOCKED);if(u.getTokenVersion()!=currentUser.getCurrentPrincipal().getTokenVersion())throw new AppException(ErrorCode.TOKEN_INVALID);if(admin&&u.getRole()!=UserRole.ADMIN)throw new AppException(ErrorCode.ACCESS_DENIED);return u;}
    private Order order(Long id,User actor,boolean admin){return (admin?orders.findByIdForUpdate(id):orders.findByIdAndUserIdForUpdate(id,actor.getId())).orElseThrow(()->new AppException(ErrorCode.ORDER_NOT_FOUND));}
    private void add(Map<String,BigDecimal> p,String account,BigDecimal amount){if(amount.signum()!=0)p.merge(account,amount,BigDecimal::add);}
    private RefundCaseResponse response(WalletRefundCase c){var r=new RefundCaseResponse();r.setId(c.getId());r.setOrderId(c.getOrder().getId());r.setAmount(c.getAmount());r.setStatus(c.getStatus());r.setReason(c.getReason());r.setDecisionReason(c.getDecisionReason());r.setCreatedAt(c.getCreatedAt());r.setReviewedAt(c.getReviewedAt());r.setItems(c.getLines().stream().map(l->new RefundCaseResponse.Line(l.getItem().getId(),l.getQuantity(),l.getAmount())).toList());return r;}
}
