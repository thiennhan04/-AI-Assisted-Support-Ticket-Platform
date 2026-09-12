package com.portfolio.identity.infrastructure.persistence;

import com.portfolio.identity.domain.RefreshSession;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "refresh_session", schema = "identity")
class RefreshSessionJpaEntity {

    @Id private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "token_hash", nullable = false, length = 64, columnDefinition = "char(64)")
    @JdbcTypeCode(SqlTypes.CHAR)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RefreshSessionJpaEntity() {}

    RefreshSessionJpaEntity(RefreshSession session) {
        id = session.id();
        tenantId = session.tenantId();
        userId = session.userId();
        familyId = session.familyId();
        tokenHash = session.tokenHash();
        expiresAt = session.expiresAt();
        createdAt = session.createdAt();
    }
}
