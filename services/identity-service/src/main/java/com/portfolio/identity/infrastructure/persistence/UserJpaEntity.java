package com.portfolio.identity.infrastructure.persistence;

import com.portfolio.identity.domain.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "app_user", schema = "identity")
class UserJpaEntity {

    @Id private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 254, columnDefinition = "citext")
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    @Column(name = "failed_login_count", nullable = false)
    private int failedLoginCount;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserJpaEntity() {}

    UUID getId() {
        return id;
    }

    UUID getTenantId() {
        return tenantId;
    }

    String getEmail() {
        return email;
    }

    String getPasswordHash() {
        return passwordHash;
    }

    String getDisplayName() {
        return displayName;
    }

    UserStatus getStatus() {
        return status;
    }

    int getFailedLoginCount() {
        return failedLoginCount;
    }

    Instant getLockedUntil() {
        return lockedUntil;
    }

    long getVersion() {
        return version;
    }

    void recordFailedLogin(
            int failures, UserStatus newStatus, Instant newLockedUntil, Instant now) {
        failedLoginCount = failures;
        status = newStatus;
        lockedUntil = newLockedUntil;
        updatedAt = now;
    }

    void recordSuccessfulLogin(UserStatus newStatus, Instant now) {
        failedLoginCount = 0;
        status = newStatus;
        lockedUntil = null;
        updatedAt = now;
    }
}
