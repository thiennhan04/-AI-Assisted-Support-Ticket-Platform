package com.portfolio.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RefreshSessionTest {

    private static final Instant NOW = Instant.parse("2026-09-13T12:00:00Z");

    @Test
    void newSessionIsActiveBeforeExpiry() {
        var session = session(null, NOW.plusSeconds(60), null);

        assertThat(session.wasRotated()).isFalse();
        assertThat(session.isRevoked()).isFalse();
        assertThat(session.isExpiredAt(NOW)).isFalse();
    }

    @Test
    void replacementMarksSessionAsRotated() {
        assertThat(session(UUID.randomUUID(), NOW.plusSeconds(60), null).wasRotated()).isTrue();
    }

    @Test
    void expiryBoundaryAndRevocationAreInactive() {
        assertThat(session(null, NOW, null).isExpiredAt(NOW)).isTrue();
        assertThat(session(null, NOW.plusSeconds(60), NOW.minusSeconds(1)).isRevoked()).isTrue();
    }

    private RefreshSession session(UUID replacementId, Instant expiresAt, Instant revokedAt) {
        return new RefreshSession(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "a".repeat(64),
                replacementId,
                expiresAt,
                revokedAt,
                NOW.minusSeconds(10),
                null);
    }
}
