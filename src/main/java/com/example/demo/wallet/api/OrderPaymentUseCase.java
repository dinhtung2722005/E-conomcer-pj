package com.example.demo.wallet.api;
import com.example.demo.wallet.api.dto.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
public interface OrderPaymentUseCase {
    PaymentResponse payOrder(@NotNull @Positive Long id,@NotNull @Valid PayOrderRequest request,String key);
}
