package com.example.demo.wallet.validation;

import org.springframework.beans.factory.ObjectProvider;
import com.example.demo.ecommerce.api.OrderOwnershipApi;
import com.example.demo.wallet.api.WalletHoldType;
import com.example.demo.wallet.entity.WalletHold;
import jakarta.validation.*;
import lombok.RequiredArgsConstructor;

/** Preserves entity ownership validation while keeping ecommerce's JPA entity private. */
@RequiredArgsConstructor
public class HoldOwnerValidator implements ConstraintValidator<ValidHoldOwner,WalletHold> {
    private final ObjectProvider<OrderOwnershipApi> orders;
    @Override public boolean isValid(WalletHold hold,ConstraintValidatorContext context){
        if(hold==null||hold.getType()!=WalletHoldType.ORDER_PAYMENT||hold.getOrderId()==null
                ||hold.getWallet()==null||hold.getWallet().getUserId()==null)return true;
        return orders.getObject().ownerId(hold.getOrderId()).filter(hold.getWallet().getUserId()::equals).isPresent();
    }
}
