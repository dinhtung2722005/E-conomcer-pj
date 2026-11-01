package com.example.demo.wallet.api.dto;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.*;
import jakarta.validation.constraints.*;
public final class WalletRecoveryDtos {
    private WalletRecoveryDtos() {}
    public record Start(String challengeId,String maskedEmail,LocalDateTime expiresAt) {}
    public record Enroll(@NotNull @Pattern(regexp="[0-9]{6}") @JsonProperty(access=JsonProperty.Access.WRITE_ONLY) String pin) {}
    public record Confirm(@NotBlank @Pattern(regexp="[a-fA-F0-9]{8}(-[a-fA-F0-9]{4}){3}-[a-fA-F0-9]{12}") String challengeId,
        @NotNull @Pattern(regexp="[0-9]{8}") @JsonProperty(access=JsonProperty.Access.WRITE_ONLY) String code,
        @NotNull @Pattern(regexp="[0-9]{6}") @JsonProperty(access=JsonProperty.Access.WRITE_ONLY) String newPin,
        @NotNull @Pattern(regexp="[0-9]{6}") @JsonProperty(access=JsonProperty.Access.WRITE_ONLY) String confirmPin) {
        @AssertTrue @JsonIgnore public boolean isConfirmed(){return newPin==null||confirmPin==null||newPin.equals(confirmPin);}
    }
    public record EmailConfirm(@NotBlank @Pattern(regexp="[a-fA-F0-9]{8}(-[a-fA-F0-9]{4}){3}-[a-fA-F0-9]{12}") String challengeId,
        @NotNull @Pattern(regexp="[0-9]{8}") @JsonProperty(access=JsonProperty.Access.WRITE_ONLY) String code) {}
}
