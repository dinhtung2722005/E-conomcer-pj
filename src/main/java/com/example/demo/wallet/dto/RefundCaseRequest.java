package com.example.demo.wallet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class RefundCaseRequest {
    @jakarta.validation.constraints.Pattern(regexp="[A-Za-z0-9._:-]{1,80}") private String requestKey;
    @Size(min=1,max=100) @jakarta.validation.Valid private java.util.List<@jakarta.validation.constraints.NotNull Line> items;
    public record Line(@jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Positive Long orderItemId,@jakarta.validation.constraints.Positive int quantity) {}
    @NotBlank @Size(max = 500) private String reason;
}
