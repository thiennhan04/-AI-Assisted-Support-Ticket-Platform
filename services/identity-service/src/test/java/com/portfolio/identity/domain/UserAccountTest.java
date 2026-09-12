package com.portfolio.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserAccountTest {

    private static final Instant NOW = Instant.parse("2026-09-08T00:00:00Z");

    @Test
    void acceptsActiveUserInActiveTenant() {
        assertThat(account(TenantStatus.ACTIVE, UserStatus.ACTIVE, null).canAuthenticateAt(NOW))
                .isTrue();
    }

    @Test
    void acceptsExpiredTemporaryLock() {
        assertThat(
                        account(TenantStatus.ACTIVE, UserStatus.LOCKED, NOW.minusSeconds(1))
                                .canAuthenticateAt(NOW))
                .isTrue();
    }

    @Test
    void rejectsSuspendedTenantAndDisabledOrCurrentlyLockedUser() {
        assertThat(account(TenantStatus.SUSPENDED, UserStatus.ACTIVE, null).canAuthenticateAt(NOW))
                .isFalse();
        assertThat(account(TenantStatus.ACTIVE, UserStatus.DISABLED, null).canAuthenticateAt(NOW))
                .isFalse();
        assertThat(
                        account(TenantStatus.ACTIVE, UserStatus.LOCKED, NOW.plusSeconds(1))
                                .canAuthenticateAt(NOW))
                .isFalse();
    }

    private UserAccount account(
            TenantStatus tenantStatus, UserStatus userStatus, Instant lockedUntil) {
        return new UserAccount(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "acme",
                tenantStatus,
                "agent@acme.local",
                "hash",
                "Agent",
                userStatus,
                0,
                lockedUntil,
                0,
                Set.of(Role.AGENT));
    }
}
