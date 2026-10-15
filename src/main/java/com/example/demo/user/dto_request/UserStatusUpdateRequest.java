package com.example.demo.user.dto_request;

import jakarta.validation.constraints.NotNull;

public record UserStatusUpdateRequest(
        @NotNull(message = "isActive không được để trống") Boolean isActive) {
}
