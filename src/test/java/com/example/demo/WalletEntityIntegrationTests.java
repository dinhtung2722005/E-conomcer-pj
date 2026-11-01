package com.example.demo;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.support.IsolatedMySqlDatabase;
import com.example.demo.ecommerce.entity.Order;
import com.example.demo.ecommerce.api.OrderStatus;
import com.example.demo.user.entity.User;
import com.example.demo.user.api.UserRole;
import com.example.demo.wallet.api.TransactionStatus;
import com.example.demo.wallet.api.TransactionType;
import com.example.demo.wallet.entity.Wallet;
import com.example.demo.wallet.api.WalletBalanceType;
import com.example.demo.wallet.entity.WalletHold;
import com.example.demo.wallet.api.WalletHoldStatus;
import com.example.demo.wallet.api.WalletHoldType;
import com.example.demo.wallet.entity.WalletSecurity;
import com.example.demo.wallet.api.WalletStatus;
import com.example.demo.wallet.entity.WalletTransaction;

import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PersistenceException;
import jakarta.validation.Validator;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {"debug=false", "logging.level.org.springframework=WARN", "logging.level.org.hibernate=WARN"})
@ContextConfiguration(initializers = IsolatedMySqlDatabase.class)
@Transactional
class WalletEntityIntegrationTests {
    @Autowired EntityManager entities;
    @Autowired JdbcTemplate jdbc;
    @Autowired Validator validator;
    @Autowired ObjectMapper json;

    private Wallet wallet;

    @BeforeEach
    void createWallet() {
        wallet = newWallet();
    }

    private Wallet newWallet() {
        String suffix = UUID.randomUUID().toString();
        User user = User.builder().username("wallet-" + suffix).email(suffix + "@example.test")
                .password("test-only-password").role(UserRole.CUSTOMER).build();
        entities.persist(user);
        Wallet created = Wallet.builder().userId(user.getId()).build();
        entities.persist(created);
        entities.flush();
        return created;
    }

    @Test
    void newWalletHasUsableDefaultsAndRoundTrips() {
        Long id = wallet.getId();
        entities.clear();
        Wallet saved = entities.find(Wallet.class, id);
        assertEquals(WalletStatus.ACTIVE, saved.getStatus());
        assertEquals("VND", saved.getCurrency());
        assertEquals(0, saved.getBalance().signum());
        assertEquals(0, saved.getPendingBalance().signum());
        assertEquals(0, saved.getBonusBalance().signum());
        assertEquals(0, saved.getPendingBonusBalance().signum());
        assertNotNull(saved.getVersion());
        assertNotNull(saved.getCreatedAt());
    }

    @Test
    void databaseRejectsSecondWalletForSameUser() {
        assertThrows(PersistenceException.class,
                () -> entities.persist(Wallet.builder().userId(wallet.getUserId()).build()));
    }

    @Test
    void databaseRejectsWalletWithoutAnExistingUser() {
        assertThrows(PersistenceException.class,
                () -> entities.persist(Wallet.builder().userId(Long.MAX_VALUE).build()));
    }

    @Test
    void databaseRejectsNegativeBalancesEvenWhenValidationIsBypassed() {
        assertCheckViolation("UPDATE wallets SET pending_bonus_balance = -1 WHERE id = ?", wallet.getId());
    }

    @Test
    void retryKeyIsUniqueWithinWallet() {
        entities.persist(transaction("request-1"));
        entities.flush();
        assertThrows(PersistenceException.class, () -> entities.persist(transaction("request-1")));
    }

    @Test
    void providerOperationCannotCreditTwoWallets() {
        WalletTransaction first = transaction("request-1");
        first.setProvider("TEST_BANK");
        first.setProviderTransactionId("bank-operation-1");
        entities.persist(first);
        entities.flush();
        Wallet other = newWallet();
        WalletTransaction duplicate = transaction("request-2");
        duplicate.setWallet(other);
        duplicate.setProvider(first.getProvider());
        duplicate.setProviderTransactionId(first.getProviderTransactionId());
        assertThrows(PersistenceException.class, () -> entities.persist(duplicate));
    }

    @Test
    void refundCanShareBusinessReferenceButMustPointToOriginalPayment() {
        WalletHold hold = orderHold("business-1");
        entities.persist(hold);
        WalletTransaction original = transaction("payment-1");
        original.setType(TransactionType.PAYMENT);
        original.setHold(hold);
        original.setStatus(TransactionStatus.SUCCESS);
        entities.persist(original);
        WalletTransaction refund = transaction("refund-1");
        refund.setType(TransactionType.REFUND);
        refund.setOriginalTransaction(original);
        entities.persist(refund);
        entities.flush();
        Long refundId = refund.getId();
        entities.clear();
        assertEquals(original.getId(), entities.find(WalletTransaction.class, refundId).getOriginalTransaction().getId());
    }

    @Test
    void orderCannotHoldMoneyFromAnotherUsersWallet() {
        WalletHold hold = orderHold("business-1");
        hold.setWallet(newWallet());
        assertFalse(validator.validate(hold).isEmpty());
    }

    @Test
    void sameOrderCanReserveCashAndBonusWithoutConvertingTheirSources() {
        WalletHold cash = orderHold("business-1");
        entities.persist(cash);
        WalletHold bonus = WalletHold.builder().wallet(wallet).orderId(cash.getOrderId()).referenceId("business-1")
                .type(WalletHoldType.ORDER_PAYMENT).balanceType(WalletBalanceType.BONUS)
                .amount(new BigDecimal("25")).build();
        entities.persist(bonus);
        WalletTransaction payment = transaction("payment-bonus");
        payment.setType(TransactionType.PAYMENT);
        payment.setBalanceType(WalletBalanceType.BONUS);
        payment.setAmount(new BigDecimal("25"));
        payment.setHold(bonus);
        entities.persist(payment);
        bonus.release(new BigDecimal("25"));
        entities.flush();
        assertEquals(WalletHoldStatus.ACTIVE, cash.getStatus());
        assertEquals(WalletHoldStatus.RELEASED, bonus.getStatus());
        assertEquals(WalletBalanceType.BONUS, payment.getBalanceType());
    }

    @Test
    void paymentCannotUseAnotherWalletsHold() {
        WalletHold hold = orderHold("business-1");
        entities.persist(hold);
        WalletTransaction payment = transaction("payment-1");
        payment.setType(TransactionType.PAYMENT);
        payment.setHold(hold);
        payment.setWallet(newWallet());
        assertFalse(validator.validate(payment).isEmpty());
    }

    @Test
    void refundCannotChangeWalletOrSourceOfFunds() {
        WalletHold hold = orderHold("business-1");
        WalletTransaction original = transaction("payment-1");
        original.setType(TransactionType.PAYMENT);
        original.setHold(hold);
        original.setStatus(TransactionStatus.SUCCESS);
        WalletTransaction refund = transaction("refund-1");
        refund.setType(TransactionType.REFUND);
        refund.setOriginalTransaction(original);
        refund.setWallet(newWallet());
        assertFalse(validator.validate(refund).isEmpty());
        refund.setWallet(wallet);
        refund.setBalanceType(WalletBalanceType.BONUS);
        assertFalse(validator.validate(refund).isEmpty());
        refund.setBalanceType(WalletBalanceType.CASH);
        refund.setAmount(new BigDecimal("101"));
        assertFalse(validator.validate(refund).isEmpty());
    }

    @Test
    void validationRejectsZeroAmountMissingRefundSourceAndExcessPrecision() {
        WalletTransaction invalid = transaction("invalid");
        invalid.setType(TransactionType.REFUND);
        invalid.setAmount(BigDecimal.ZERO);
        assertFalse(validator.validate(invalid).isEmpty());
        invalid.setType(TransactionType.DEPOSIT);
        invalid.setAmount(new BigDecimal("0.00001"));
        assertFalse(validator.validate(invalid).isEmpty());
    }

    @Test
    void databaseRejectsInvalidAmountAndRefundWithoutOriginal() {
        WalletTransaction transaction = transaction("deposit-1");
        entities.persist(transaction);
        entities.flush();
        assertCheckViolation("UPDATE wallet_transactions SET amount = 0 WHERE id = ?", transaction.getId());
        assertCheckViolation("UPDATE wallet_transactions SET transaction_type = 'REFUND' WHERE id = ?", transaction.getId());
    }

    @Test
    void partialCaptureAndReleaseCannotExceedReservedFunds() {
        WalletHold hold = withdrawalHold("withdraw-1");
        entities.persist(hold);
        hold.capture(new BigDecimal("60"));
        assertEquals(WalletHoldStatus.PARTIALLY_PROCESSED, hold.getStatus());
        assertThrows(IllegalArgumentException.class, () -> hold.release(new BigDecimal("41")));
        hold.release(new BigDecimal("40"));
        entities.flush();
        assertEquals(WalletHoldStatus.CLOSED, hold.getStatus());
        assertEquals(0, hold.getRemainingAmount().signum());
        assertThrows(IllegalArgumentException.class, () -> hold.capture(BigDecimal.ONE));
        entities.clear();
        assertEquals(WalletHoldStatus.CLOSED, entities.find(WalletHold.class, hold.getId()).getStatus());
    }

    @Test
    void databaseRejectsOverAllocationAndDuplicateHolds() {
        WalletHold hold = withdrawalHold("withdraw-1");
        entities.persist(hold);
        entities.flush();
        assertCheckViolation("UPDATE wallet_holds SET captured_amount = 101 WHERE id = ?", hold.getId());
        assertCheckViolation("UPDATE wallet_holds SET status = 'CAPTURED' WHERE id = ?", hold.getId());
        assertThrows(PersistenceException.class, () -> entities.persist(withdrawalHold("withdraw-1")));
    }

    @Test
    void promotionalFundsCannotBeWithdrawnOrUsedForDeposits() {
        WalletHold hold = withdrawalHold("withdraw-bonus");
        hold.setBalanceType(WalletBalanceType.BONUS);
        assertFalse(validator.validate(hold).isEmpty());
        WalletTransaction deposit = transaction("deposit-bonus");
        deposit.setBalanceType(WalletBalanceType.BONUS);
        assertFalse(validator.validate(deposit).isEmpty());
        WalletTransaction cashback = transaction("cashback-cash");
        cashback.setType(TransactionType.CASHBACK);
        assertFalse(validator.validate(cashback).isEmpty());
        cashback.setBalanceType(WalletBalanceType.BONUS);
        assertTrue(validator.validate(cashback).isEmpty());
    }

    @Test
    void withdrawalRequiresHoldAndVerifiedDestinationReference() {
        WalletTransaction withdrawal = transaction("withdraw-1");
        withdrawal.setReferenceId("withdraw-1");
        withdrawal.setType(TransactionType.WITHDRAW);
        assertFalse(validator.validate(withdrawal).isEmpty());
        WalletHold hold = withdrawalHold("withdraw-1");
        entities.persist(hold);
        withdrawal.setHold(hold);
        withdrawal.setDestinationAccountReference("verified-bank-token");
        assertTrue(validator.validate(withdrawal).isEmpty());
        entities.persist(withdrawal);
        entities.flush();
    }

    @Test
    void staleWalletUpdateDoesNotOverwriteConcurrentBalanceChange() {
        jdbc.update("UPDATE wallets SET balance = 20, version = version + 1 WHERE id = ?", wallet.getId());
        wallet.setBalance(BigDecimal.TEN);
        assertThrows(OptimisticLockException.class, entities::flush);
    }

    @Test
    void securityUsesWalletIdentityAndNeverSerializesPinHash() {
        WalletSecurity security = WalletSecurity.builder().wallet(wallet).pinHash("test-only-encoded-hash").build();
        entities.persist(security);
        entities.flush();
        assertEquals(wallet.getId(), security.getId());
        String serialized = json.writeValueAsString(security);
        assertFalse(serialized.contains("pinHash"));
        assertFalse(serialized.contains("test-only-encoded-hash"));
    }

    private WalletTransaction transaction(String key) {
        return WalletTransaction.builder().wallet(wallet).referenceId("business-1").idempotencyKey(key)
                .type(TransactionType.DEPOSIT).amount(new BigDecimal("100")).build();
    }

    private void assertCheckViolation(String sql, Long id) {
        DataAccessException error = assertThrows(DataAccessException.class, () -> jdbc.update(sql, id));
        SQLException cause = assertInstanceOf(SQLException.class, error.getMostSpecificCause());
        assertEquals(3819, cause.getErrorCode(), "Must fail because MySQL enforces CHECK, not because of malformed SQL");
    }

    private WalletHold withdrawalHold(String reference) {
        return WalletHold.builder().wallet(wallet).referenceId(reference).type(WalletHoldType.WITHDRAWAL)
                .amount(new BigDecimal("100")).build();
    }

    private WalletHold orderHold(String reference) {
        Order order = Order.builder().userId(wallet.getUserId()).receiverName("Test Buyer")
                .receiverPhone("0900000000").shippingAddress("Test address").status(OrderStatus.PAID)
                .totalAmount(new BigDecimal("100")).finalAmount(new BigDecimal("100")).build();
        entities.persist(order);
        return WalletHold.builder().wallet(wallet).orderId(order.getId()).referenceId(reference)
                .type(WalletHoldType.ORDER_PAYMENT).amount(new BigDecimal("100")).build();
    }
}
