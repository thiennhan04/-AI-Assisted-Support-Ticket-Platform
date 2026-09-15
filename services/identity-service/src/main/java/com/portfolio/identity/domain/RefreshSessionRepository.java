package com.portfolio.identity.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshSessionRepository {

    Optional<RefreshSession> findByTokenHashForUpdate(String tokenHash);

    void save(RefreshSession session);

    void markReplaced(UUID sessionId, UUID replacementId, Instant usedAt);

    void revokeFamily(UUID tenantId, UUID familyId, Instant revokedAt);
}
