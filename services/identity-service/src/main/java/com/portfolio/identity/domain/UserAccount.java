package com.portfolio.identity.domain;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record UserAccount(
        UUID id,
        UUID tenantId,
        String tenantCode,
        TenantStatus tenantStatus,
        String email,
        String passwordHash,
        String displayName,
        UserStatus status,
        int failedLoginCount,
        Instant lockedUntil,
        long version,
        Set<Role> roles) {

    public UserAccount {
        roles = Set.copyOf(roles);
    }

    public boolean canAuthenticateAt(Instant now) {
        if (tenantStatus != TenantStatus.ACTIVE || status == UserStatus.DISABLED) {
            return false;
        }
        return status == UserStatus.ACTIVE
                || (status == UserStatus.LOCKED
                        && lockedUntil != null
                        && !lockedUntil.isAfter(now));
    }
}
