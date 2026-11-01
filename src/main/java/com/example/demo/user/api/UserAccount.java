package com.example.demo.user.api;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Immutable account metadata. Password hashes and persistent entities never cross the module boundary. */
@Getter @AllArgsConstructor
public final class UserAccount {
    private final Long id;
    private final String email;
    private final UserRole role;
    private final boolean active;
    private final long tokenVersion;
}
