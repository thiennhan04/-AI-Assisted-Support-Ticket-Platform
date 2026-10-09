package com.portfolio.knowledge.infrastructure.persistence;

import com.portfolio.knowledge.domain.DocumentStatus;
import com.portfolio.knowledge.domain.DocumentVersion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "document_version", schema = "knowledge")
class DocumentVersionJpaEntity {
    @Id UUID id;

    @Column(nullable = false)
    UUID documentId;

    @Column(nullable = false)
    int versionNumber;

    @Column(nullable = false)
    String objectKey;

    @Column(nullable = false)
    String mimeType;

    @Column(nullable = false)
    long sizeBytes;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(columnDefinition = "char(64)", length = 64)
    String sha256;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    DocumentStatus status;

    String errorCode;

    @Column(nullable = false)
    Instant createdAt;

    protected DocumentVersionJpaEntity() {}

    static DocumentVersionJpaEntity from(
            DocumentVersion version, UUID documentId, Instant createdAt) {
        var entity = new DocumentVersionJpaEntity();
        entity.id = version.id();
        entity.documentId = documentId;
        entity.versionNumber = version.versionNumber();
        entity.objectKey = version.objectKey();
        entity.mimeType = version.mimeType();
        entity.sizeBytes = version.sizeBytes();
        entity.sha256 = version.sha256();
        entity.status = version.status();
        entity.errorCode = version.errorCode();
        entity.createdAt = createdAt;
        return entity;
    }

    DocumentVersion toDomain() {
        return new DocumentVersion(
                id, versionNumber, objectKey, mimeType, sizeBytes, sha256, status, errorCode);
    }
}
