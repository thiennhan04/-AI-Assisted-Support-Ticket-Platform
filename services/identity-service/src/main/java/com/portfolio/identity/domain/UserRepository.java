package com.portfolio.identity.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository {

    Optional<UserAccount> findByTenantCodeAndEmail(String tenantCode, String email);

    Optional<UserAccount> findByTenantIdAndUserIdForUpdate(UUID tenantId, UUID userId);

    void recordFailedLogin(
            UUID tenantId,
            UUID userId,
            int failedLoginCount,
            UserStatus status,
            Instant lockedUntil,
            Instant updatedAt);

    void recordSuccessfulLogin(UUID tenantId, UUID userId, UserStatus status, Instant updatedAt);
}
