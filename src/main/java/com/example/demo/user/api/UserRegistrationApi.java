package com.example.demo.user.api;
import com.example.demo.user.api.dto.*;
public interface UserRegistrationApi {
    UserProfileResponse registerAccount(UserCreationRequest request);
    UserProfileResponse createAccount(UserCreationRequest request);
}
