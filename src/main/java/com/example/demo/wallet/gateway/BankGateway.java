package com.example.demo.wallet.gateway;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.example.demo.wallet.dto.TopUpInstructionsResponse;
import com.example.demo.wallet.entity.TransactionType;

/**
 * Implement with a partner that supports stable merchant references and idempotent submission/lookup.
 * Adapters must validate signatures, ownership and response authenticity; never trust a browser result.
 * No implementation moving real money is installed until a bank partner is selected.
 */
public interface BankGateway {
    String provider();
    VerifiedAccount verifyOwnership(Long userId, String oneTimeProof);

    /** Resume/lookup first; only submit if absent. Repeating this command MUST NOT transfer twice. */
    Result resolve(Command command);

    record VerifiedAccount(String accountToken, String bankCode, String bankName,
            String maskedAccountNumber, String accountHolderName) {}
    record Command(String merchantReference, TransactionType type, BigDecimal amount, String currency,
            String accountToken) {}
    enum State { PROCESSING, SUCCEEDED, REJECTED }
    record Result(String merchantReference, BigDecimal amount, String currency, String accountToken,
            String providerTransactionId, State state, TopUpInstructionsResponse instructions,
            LocalDateTime expiresAt) {}
}
