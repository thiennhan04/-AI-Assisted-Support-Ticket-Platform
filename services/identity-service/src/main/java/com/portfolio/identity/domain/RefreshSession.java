package com.portfolio.identity.domain;

import java.time.Instant;
import java.util.UUID;

public record RefreshSession(
        UUID id,
        UUID tenantId,
        UUID userId,
        UUID familyId,
        String tokenHash,
        UUID replacedById,
        Instant expiresAt,
        Instant revokedAt,
        Instant createdAt,
        Instant lastUsedAt) {

    public boolean wasRotated() {
        return replacedById != null;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpiredAt(Instant now) {
        return !expiresAt.isAfter(now);
    }
}
