package com.example.demo.user.api;

import java.util.Optional;

public interface UserAccountsApi {
    Long getCurrentUserId();
    UserAccount getCurrentUser();
    UserAccount getCurrentPrincipal();
    Optional<UserAccount> findById(Long id);
    /** Requires an existing transaction; obtains the first lock in financial workflows. */
    Optional<UserAccount> findByIdForUpdate(Long id);
    UserAccount requireCurrent(boolean administrator);
    boolean matchesPassword(Long userId, String password);
    void revokeAllSessions(Long userId);
}
