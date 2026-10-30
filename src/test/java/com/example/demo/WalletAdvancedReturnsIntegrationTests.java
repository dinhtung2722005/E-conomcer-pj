package com.example.demo;
import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.example.demo.support.IsolatedMySqlDatabase;
import com.example.demo.user.entity.*;
import com.example.demo.user.exception.*;
import com.example.demo.user.repository.UserRepository;
import com.example.demo.user.security.UserPrincipal;
import com.example.demo.ecommerce.entity.*;
import com.example.demo.ecommerce.entity.Order;
import com.example.demo.ecommerce.repository.*;
import com.example.demo.ecommerce.service.OrderService;
import com.example.demo.wallet.dto.*;
import com.example.demo.wallet.entity.*;
import com.example.demo.wallet.repository.*;
import com.example.demo.wallet.service.*;
@SpringBootTest(properties={"debug=false","logging.level.org.springframework=WARN","logging.level.org.hibernate=WARN"})
@ContextConfiguration(initializers=IsolatedMySqlDatabase.class)
@org.springframework.context.annotation.Import(WalletFundingIntegrationTests.GatewayConfiguration.class)
class WalletAdvancedReturnsIntegrationTests {
    static final AtomicInteger CURRENCIES=new AtomicInteger();
    @Autowired UserRepository users;@Autowired WalletRepository wallets;@Autowired WalletSecurityRepository security;
    @Autowired WalletPaymentService payments;@Autowired WalletRefundService refunds;@Autowired WalletSettlementService settlement;
    @Autowired WalletReserveService reserve;@Autowired WalletRecoveryDebtRepository debts;@Autowired WalletRefundReserveRepository reserves;
    @Autowired WalletRefundCaseRepository cases;@Autowired WalletHoldRepository holds;@Autowired WalletJournalRepository journals;
    @Autowired WalletReconciliationService reconciliation;@Autowired OrderService orderService;@Autowired WalletSettlementPolicy policy;
    @Autowired OrderRepository orders;@Autowired ProductRepository products;@Autowired InventoryRepository inventories;
    @Autowired PlatformTransactionManager manager;@Autowired PasswordEncoder encoder;
    @Autowired WalletFundingService funding;@Autowired WalletBankWorker bankWorker;@Autowired WalletBankManagementService banks;@Autowired BankTransferRepository transfers;
    User buyer,seller,admin;Wallet buyerWallet,sellerWallet,adminWallet;String currency;
    @BeforeEach void prepare(){
        int n=CURRENCIES.getAndIncrement();currency="Z"+(char)('A'+n/26)+(char)('A'+n%26);
        buyer=user(UserRole.CUSTOMER);seller=user(UserRole.SELLER);admin=user(UserRole.ADMIN);
        buyerWallet=wallet(buyer,"1000","40");sellerWallet=wallet(seller,"0","0");adminWallet=wallet(admin,"1000","0");auth(buyer);
    }
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    @Test void partialReturnRestoresOriginalSourcesAndSettlementPaysOnlyRemainingAmount(){
        var order=paid(3,"20",true,false);complete(order);var c=request(order,1,false);approve(order,c);
        money("20",orders.findById(order.getId()).orElseThrow().getRefundedAmount());money("986.6666",fresh(buyerWallet).getBalance());money("13.3334",fresh(buyerWallet).getBonusBalance());
        due(order);assertTrue(settlement.settle(order.getId()));money("40",fresh(sellerWallet).getBalance());money("0",fresh(buyerWallet).getPendingBalance());money("0",fresh(buyerWallet).getPendingBonusBalance());
        checkLedger(buyerWallet);checkLedger(sellerWallet);
    }
    @Test void twoPartialReturnsCannotExceedOriginalQuantityOrRefundTwice(){
        var order=paid(3,"20",true,false);complete(order);var first=request(order,1,false);approve(order,first);auth(buyer);
        var excessive=body(order,3);error(ErrorCode.INVALID_REQUEST,()->refunds.requestRefund(order.getId(),excessive));
        var second=request(order,2,false);approve(order,second);approve(order,second);
        assertEquals(OrderStatus.REFUNDED,orders.findById(order.getId()).orElseThrow().getStatus());money("1000",fresh(buyerWallet).getBalance());money("40",fresh(buyerWallet).getBonusBalance());checkLedger(buyerWallet);
    }
    @Test void postSettlementRefundDebitsAvailableSellerMoneyWithoutReleasingCapturedHold(){
        var order=paid(1,"20",false,false);complete(order);due(order);assertTrue(settlement.settle(order.getId()));
        var before=holds.findByWalletIdAndOrderIdOrderByIdAsc(buyerWallet.getId(),order.getId()).getFirst().getCapturedAmount();
        var c=request(order,1,true);approve(order,c);money("1000",fresh(buyerWallet).getBalance());money("0",fresh(sellerWallet).getBalance());
        assertEquals(before,holds.findByWalletIdAndOrderIdOrderByIdAsc(buyerWallet.getId(),order.getId()).getFirst().getCapturedAmount());
        assertEquals(OrderStatus.REFUNDED,orders.findById(order.getId()).orElseThrow().getStatus());checkLedger(buyerWallet);checkLedger(sellerWallet);
    }
    @Test void insufficientReserveRollsBackRefundAndLeavesCasePending(){
        var order=paid(1,"20",false,false);complete(order);due(order);settlement.settle(order.getId());spendSellerCash();
        var c=request(order,1,true);auth(admin);error(ErrorCode.WALLET_REFUND_RESERVE_INSUFFICIENT,()->refunds.decide(order.getId(),decision(c)));
        assertEquals(WalletRefundCase.Status.REQUESTED,cases.findById(c.getId()).orElseThrow().getStatus());money("980",fresh(buyerWallet).getBalance());money("0",debts.total(seller.getId(),WalletBalanceType.CASH,currency));
    }
    @Test void reserveIsPrefundedAndNextSellerSettlementRepaysActualAdvance(){
        var order=paid(1,"20",false,false);complete(order);due(order);settlement.settle(order.getId());spendSellerCash();
        auth(admin);var funding=new WalletReserveFundingRequest(new BigDecimal("20"),"123456","Prefund refunds");Long t=reserve.fund(funding,"fund");assertEquals(t,reserve.fund(funding,"fund"));money("980",fresh(adminWallet).getBalance());
        var c=request(order,1,true);approve(order,c);money("0",reserve.balance(currency).balance());money("20",debts.total(seller.getId(),WalletBalanceType.CASH,currency));money("1000",fresh(buyerWallet).getBalance());
        var next=paid(1,"30",false,false);complete(next);due(next);settlement.settle(next.getId());
        money("10",fresh(sellerWallet).getBalance());money("20",reserves.findById(currency).orElseThrow().getBalance());money("0",debts.total(seller.getId(),WalletBalanceType.CASH,currency));
        checkLedger(buyerWallet);checkLedger(sellerWallet);checkLedger(adminWallet);
    }
    @Test void spentCashbackCreatesBonusDebtRecoveredFromLaterRewardWithoutNegativeBalance(){
        var original=paid(1,"20",false,true);complete(original);due(original);settlement.settle(original.getId());money("41",fresh(buyerWallet).getBonusBalance());
        paid(1,"41",true,false);money("0",fresh(buyerWallet).getBonusBalance());
        var c=request(original,1,true);approve(original,c);money("1",debts.total(buyer.getId(),WalletBalanceType.BONUS,currency));money("0",fresh(buyerWallet).getBonusBalance());
        var next=paid(1,"20",false,true);complete(next);due(next);settlement.settle(next.getId());
        money("0",debts.total(buyer.getId(),WalletBalanceType.BONUS,currency));money("0",fresh(buyerWallet).getBonusBalance());checkLedger(buyerWallet);
    }
    @Test void repeatedCaseDecisionCannotRefundQuantityTwiceAcrossThreads()throws Exception {
        var order=paid(2,"20",false,false);complete(order);var c=request(order,1,false);
        try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)){
            var start=new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<Long> task=()->{auth(admin);start.await();try{return refunds.decide(order.getId(),decision(c)).getId();}finally{SecurityContextHolder.clearContext();}};
            var first=executor.submit(task);var second=executor.submit(task);start.countDown();assertEquals(first.get(20,java.util.concurrent.TimeUnit.SECONDS),second.get(20,java.util.concurrent.TimeUnit.SECONDS));
        }
        money("20",orders.findById(order.getId()).orElseThrow().getRefundedAmount());money("980",fresh(buyerWallet).getBalance());money("20",fresh(buyerWallet).getPendingBalance());checkLedger(buyerWallet);
    }
    @Test void sameRequestKeyWithDifferentQuantityIsRejected(){
        var order=paid(2,"20",false,false);complete(order);var first=body(order,1);refunds.requestRefund(order.getId(),first);
        var changed=body(order,2);changed.setRequestKey(first.getRequestKey());error(ErrorCode.WALLET_IDEMPOTENCY_CONFLICT,()->refunds.requestRefund(order.getId(),changed));
    }
    @Test void postSettlementRefundReversesBothSellerNetAndPlatformFee(){
        var order=paid(1,"20",false,false,true);complete(order);due(order);settlement.settle(order.getId());money("18",fresh(sellerWallet).getBalance());money("2",journals.accountBalance("platform:FEES",currency));
        var c=request(order,1,true);approve(order,c);money("1000",fresh(buyerWallet).getBalance());money("0",fresh(sellerWallet).getBalance());money("0",journals.accountBalance("platform:FEES",currency));checkLedger(buyerWallet);checkLedger(sellerWallet);
    }
    @Test void queuedWithdrawalIsSuspendedWhenRefundDebtAppearsAndCanBeCancelledToRepay(){
        var order=paid(1,"20",false,false);complete(order);due(order);settlement.settle(order.getId());auth(seller);
        var link=new LinkBankAccountRequest();link.setVerificationToken("verified:"+seller.getId());Long account=funding.linkAccount(link).getLinkedBankAccountId();
        var request=new WithdrawRequest();request.setAmount(new BigDecimal("20"));request.setPin("123456");request.setLinkedBankAccountId(account);var withdrawal=funding.withdraw(request,"seller-pending");
        auth(admin);reserve.fund(new WalletReserveFundingRequest(new BigDecimal("20"),"123456","Prefund refund"),"fund");var c=request(order,1,true);approve(order,c);
        var transfer=transfers.findByTransactionId(withdrawal.getTransactionId()).orElseThrow();bankWorker.process(transfer.getId());assertTrue(transfers.findById(transfer.getId()).orElseThrow().isSuspended());
        auth(seller);banks.cancelWithdrawal(withdrawal.getTransactionId(),new WalletBankActionRequest("123456","Repay refund debt"));
        reserve.repay(new WalletReserveFundingRequest(new BigDecimal("20"),"123456","Repay refund advance"),"repay");money("0",fresh(sellerWallet).getBalance());money("0",debts.total(seller.getId(),WalletBalanceType.CASH,currency));money("20",reserves.findById(currency).orElseThrow().getBalance());checkLedger(sellerWallet);
    }
    private void spendSellerCash(){
        User merchant=user(UserRole.SELLER);wallet(merchant,"0","0");
        var p=products.saveAndFlush(Product.builder().name("Other seller "+UUID.randomUUID()).sellerId(merchant.getId()).price(new BigDecimal("20")).status(ProductStatus.ACTIVE).build());
        inventories.saveAndFlush(Inventory.builder().product(p).availableQuantity(0).lockedQuantity(1).build());
        var o=Order.builder().userId(seller.getId()).totalAmount(new BigDecimal("20")).finalAmount(new BigDecimal("20")).status(OrderStatus.PENDING).receiverName("Seller buyer").receiverPhone("0901234567").shippingAddress("Test").build();
        o.addItem(OrderItem.builder().product(p).productName(p.getName()).sellerId(merchant.getId()).quantity(1).priceAtPurchase(p.getPrice()).build());policy.snapshot(o);o=orders.saveAndFlush(o);auth(seller);
        var request=new PayOrderRequest();request.setPin("123456");payments.payOrder(o.getId(),request,"spend:"+o.getId());
    }
    private Order paid(int quantity,String price,boolean bonus,boolean cashback){
        return paid(quantity,price,bonus,cashback,false);
    }
    private Order paid(int quantity,String price,boolean bonus,boolean cashback,boolean fee){
        auth(buyer);var p=products.saveAndFlush(Product.builder().name("Return "+UUID.randomUUID()).sellerId(seller.getId()).price(new BigDecimal(price)).status(ProductStatus.ACTIVE).build());inventories.saveAndFlush(Inventory.builder().product(p).availableQuantity(0).lockedQuantity(quantity).build());
        BigDecimal total=p.getPrice().multiply(BigDecimal.valueOf(quantity));var o=Order.builder().userId(buyer.getId()).totalAmount(total).finalAmount(total).status(OrderStatus.PENDING).receiverName("Buyer").receiverPhone("0901234567").shippingAddress("Test").build();o.addItem(OrderItem.builder().product(p).productName(p.getName()).sellerId(seller.getId()).quantity(quantity).priceAtPurchase(p.getPrice()).build());policy.snapshot(o);if(cashback)o.setCashbackPercent(new BigDecimal("5"));if(fee){o.setFeePercent(new BigDecimal("10"));o.getItems().getFirst().setPlatformFee(total.multiply(new BigDecimal("0.1")));}o=orders.saveAndFlush(o);
        var request=new PayOrderRequest();request.setPin("123456");request.setUseBonus(bonus);payments.payOrder(o.getId(),request,"pay:"+o.getId());return o;
    }
    private RefundCaseRequest body(Order order,int quantity){var r=new RefundCaseRequest();r.setReason("Returned goods");r.setRequestKey(UUID.randomUUID().toString());r.setItems(List.of(new RefundCaseRequest.Line(order.getItems().getFirst().getId(),quantity)));return r;}
    private RefundCaseResponse request(Order order,int quantity,boolean adminException){auth(adminException?admin:buyer);return adminException?refunds.requestAdminRefund(order.getId(),body(order,quantity)):refunds.requestRefund(order.getId(),body(order,quantity));}
    private RefundDecisionRequest decision(RefundCaseResponse c){var d=new RefundDecisionRequest();d.setCaseId(c.getId());d.setApproved(true);d.setReason("Checked returned goods");return d;}
    private void approve(Order order,RefundCaseResponse c){auth(admin);refunds.decide(order.getId(),decision(c));}
    private void complete(Order order){auth(buyer);orderService.updateOrderStatus(order.getId(),OrderStatus.SHIPPED);orderService.confirmReceipt(order.getId());}
    private void due(Order order){new TransactionTemplate(manager).executeWithoutResult(tx->orders.findByIdForUpdate(order.getId()).orElseThrow().setSettlementAvailableAt(LocalDateTime.now().minusSeconds(1)));}
    private User user(UserRole role){String n="advanced_"+UUID.randomUUID().toString().replace("-","");return users.saveAndFlush(User.builder().username(n).email(n+"@example.test").password("test-only").role(role).build());}
    private Wallet wallet(User user,String cash,String bonus){var w=wallets.saveAndFlush(Wallet.builder().userId(user.getId()).currency(currency).balance(new BigDecimal(cash)).bonusBalance(new BigDecimal(bonus)).build());security.saveAndFlush(WalletSecurity.builder().wallet(w).pinHash(encoder.encode("123456")).build());return w;}
    private Wallet fresh(Wallet w){return wallets.findById(w.getId()).orElseThrow();}
    private void auth(User user){var p=new UserPrincipal(user);SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p,null,p.getAuthorities()));}
    private void money(String expected,BigDecimal actual){assertEquals(0,new BigDecimal(expected).compareTo(actual));}
    private void checkLedger(Wallet wallet){assertEquals(Boolean.TRUE,reconciliation.inspect(wallet.getId()).getLedgerConsistent());assertEquals(0,journals.countUnbalancedJournals());}
    private void error(ErrorCode code,org.junit.jupiter.api.function.Executable action){assertEquals(code,assertThrows(AppException.class,action).getErrorCode());}
}
