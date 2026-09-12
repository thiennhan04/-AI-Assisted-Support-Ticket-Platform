package com.portfolio.identity.domain;

import java.time.Instant;
import java.util.UUID;

public record RefreshSession(
        UUID id,
        UUID tenantId,
        UUID userId,
        UUID familyId,
        String tokenHash,
        Instant expiresAt,
        Instant createdAt) {}
