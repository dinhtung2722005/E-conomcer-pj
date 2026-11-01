package com.example.demo.wallet.validation;
import java.lang.annotation.*;
import jakarta.validation.*;
@Documented @Target(ElementType.TYPE) @Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy=HoldOwnerValidator.class)
public @interface ValidHoldOwner {
    String message() default "Order and wallet must belong to the same user";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
