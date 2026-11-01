package com.example.demo;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;

import com.example.demo.wallet.api.dto.ChangePinRequest;
import com.example.demo.wallet.api.dto.PayOrderRequest;
import com.example.demo.wallet.api.dto.SetupPinRequest;
import com.example.demo.wallet.api.dto.TopUpRequest;
import com.example.demo.wallet.api.dto.WithdrawRequest;
import com.example.demo.wallet.entity.WalletSecurity;
import com.example.demo.wallet.mapper.WalletMapper;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** Checks the money/PIN input boundary and secret handling without starting a database. */
class WalletDtoTests {
    private static ValidatorFactory validatorFactory;
    private static Validator validator;
    private final ObjectMapper json = JsonMapper.builder().build();

    @BeforeAll
    static void openValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"0", "-1", "0.00001", "1000000000000000"})
    void fundingRequestsRejectMissingNonPositiveAndUnrepresentableAmounts(String value) {
        TopUpRequest topUp = validTopUp();
        WithdrawRequest withdraw = validWithdraw();
        BigDecimal amount = value == null ? null : new BigDecimal(value);
        topUp.setAmount(amount);
        withdraw.setAmount(amount);
        assertFalse(validator.validate(topUp).isEmpty());
        assertFalse(validator.validate(withdraw).isEmpty());
    }

    @Test
    void moneyWithinExistingDatabasePrecisionIsAcceptedWithoutRounding() {
        TopUpRequest topUp = validTopUp();
        WithdrawRequest withdraw = validWithdraw();
        BigDecimal amount = new BigDecimal("999999999999999.9999");
        topUp.setAmount(amount);
        withdraw.setAmount(amount);
        assertTrue(validator.validate(topUp).isEmpty());
        assertTrue(validator.validate(withdraw).isEmpty());
        assertEquals(amount, topUp.getAmount());
        assertEquals(amount, withdraw.getAmount());
    }

    @Test
    void fundingRequestsRequirePositiveLinkedAccountIds() {
        for (Long id : new Long[] {null, 0L, -1L}) {
            TopUpRequest topUp = validTopUp();
            WithdrawRequest withdraw = validWithdraw();
            topUp.setLinkedBankAccountId(id);
            withdraw.setLinkedBankAccountId(id);
            assertFalse(validator.validate(topUp).isEmpty());
            assertFalse(validator.validate(withdraw).isEmpty());
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"      ", "12345", "1234567", "abcdef", "１２３４５６", "12345\n"})
    void pinInputsRejectMissingOrMalformedPins(String pin) {
        WithdrawRequest withdraw = validWithdraw();
        withdraw.setPin(pin);
        PayOrderRequest payment = new PayOrderRequest();
        payment.setPin(pin);
        SetupPinRequest setup = validSetup();
        setup.setNewPin(pin);
        setup.setConfirmPin(pin);
        ChangePinRequest change = validChange();
        change.setCurrentPin(pin);
        change.setNewPin(pin);
        change.setConfirmPin(pin);
        assertFalse(validator.validate(withdraw).isEmpty());
        assertFalse(validator.validate(payment).isEmpty());
        assertFalse(validator.validate(setup).isEmpty());
        assertFalse(validator.validate(change).isEmpty());
    }

    @Test
    void pinConfirmationMustMatchAndPasswordIsRequiredForEnrollment() {
        SetupPinRequest setup = validSetup();
        setup.setConfirmPin("999999");
        assertFalse(validator.validate(setup).isEmpty());
        setup.setConfirmPin(setup.getNewPin());
        setup.setCurrentPassword(" ");
        assertFalse(validator.validate(setup).isEmpty());
        ChangePinRequest change = validChange();
        change.setConfirmPin("999999");
        assertFalse(validator.validate(change).isEmpty());
    }

    @Test
    void jsonReadsPinStringsWithLeadingZerosButNeverWritesSecrets() {
        PayOrderRequest payment = json.readValue("{\"pin\":\"001234\"}", PayOrderRequest.class);
        assertEquals("001234", payment.getPin());
        assertFalse(payment.isUseBonus());
        assertTrue(validator.validate(payment).isEmpty());
        assertFalse(json.writeValueAsString(payment).contains("001234"));

        SetupPinRequest setup = json.readValue(
                "{\"currentPassword\":\"legacy-secret\",\"newPin\":\"001234\",\"confirmPin\":\"001234\"}",
                SetupPinRequest.class);
        ChangePinRequest change = json.readValue(
                "{\"currentPin\":\"991234\",\"newPin\":\"001234\",\"confirmPin\":\"001234\"}",
                ChangePinRequest.class);
        WithdrawRequest withdraw = json.readValue(
                "{\"amount\":100,\"linkedBankAccountId\":1,\"pin\":\"001234\"}", WithdrawRequest.class);
        assertEquals("legacy-secret", setup.getCurrentPassword());
        assertEquals("991234", change.getCurrentPin());
        assertEquals("001234", withdraw.getPin());
        for (Object request : new Object[] {setup, change, withdraw}) {
            assertTrue(validator.validate(request).isEmpty());
            var output = json.readTree(json.writeValueAsString(request));
            for (String field : new String[] {"pin", "currentPassword", "currentPin", "newPin", "confirmPin", "pinConfirmed"}) {
                assertFalse(output.has(field), "Request serialization must omit " + field);
            }
        }
    }

    @Test
    void securityResponseReportsEnrollmentWithoutLeakingHash() {
        WalletMapper mapper = Mappers.getMapper(WalletMapper.class);
        WalletSecurity security = WalletSecurity.builder().build();
        assertFalse(mapper.toSecurityResponse(security).isPinConfigured());
        security.setPinHash("private-encoded-hash");
        var response = mapper.toSecurityResponse(security);
        assertTrue(response.isPinConfigured());
        var output = json.readTree(json.writeValueAsString(response));
        assertFalse(output.has("pinHash"));
        assertFalse(output.has("wallet"));
        assertFalse(output.toString().contains("private-encoded-hash"));
    }

    private TopUpRequest validTopUp() {
        TopUpRequest request = new TopUpRequest();
        request.setAmount(new BigDecimal("100"));
        request.setLinkedBankAccountId(1L);
        return request;
    }

    private WithdrawRequest validWithdraw() {
        WithdrawRequest request = new WithdrawRequest();
        request.setAmount(new BigDecimal("100"));
        request.setLinkedBankAccountId(1L);
        request.setPin("001234");
        return request;
    }

    private SetupPinRequest validSetup() {
        SetupPinRequest request = new SetupPinRequest();
        request.setCurrentPassword("legacy-secret");
        request.setNewPin("001234");
        request.setConfirmPin("001234");
        return request;
    }

    private ChangePinRequest validChange() {
        ChangePinRequest request = new ChangePinRequest();
        request.setCurrentPin("991234");
        request.setNewPin("001234");
        request.setConfirmPin("001234");
        return request;
    }
}
