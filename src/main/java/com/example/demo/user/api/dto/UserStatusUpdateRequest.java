package com.example.demo.user.api.dto;

import jakarta.validation.constraints.NotNull;

public record UserStatusUpdateRequest(
        @NotNull(message = "isActive không được để trống") Boolean isActive) {
}
