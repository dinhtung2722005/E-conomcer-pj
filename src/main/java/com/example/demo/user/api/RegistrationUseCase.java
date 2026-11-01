package com.example.demo.user.api;
import com.example.demo.user.api.dto.*;
/** Application-level provisioner: account and wallet are committed together. */
public interface RegistrationUseCase {
    UserProfileResponse register(@jakarta.validation.constraints.NotNull @jakarta.validation.Valid UserCreationRequest request);
    UserProfileResponse createUser(@jakarta.validation.constraints.NotNull @jakarta.validation.Valid UserCreationRequest request);
}
