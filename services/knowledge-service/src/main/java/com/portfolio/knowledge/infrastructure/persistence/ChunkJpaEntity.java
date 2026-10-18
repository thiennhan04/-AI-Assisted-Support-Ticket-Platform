package com.portfolio.knowledge.infrastructure.persistence;

import com.portfolio.knowledge.domain.Chunk;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "document_chunk", schema = "knowledge")
class ChunkJpaEntity {
    @Id UUID id;

    @Column(nullable = false)
    UUID tenantId;

    @Column(nullable = false)
    UUID documentId;

    @Column(nullable = false)
    UUID documentVersionId;

    @Column(nullable = false)
    int ordinal;

    Integer pageNumber;

    @Column(length = 500)
    String heading;

    @Column(nullable = false, columnDefinition = "text")
    String content;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, columnDefinition = "char(64)", length = 64)
    String contentChecksum;

    @Column(nullable = false)
    int tokenCount;

    @Column(nullable = false)
    Instant createdAt;

    protected ChunkJpaEntity() {}

    static ChunkJpaEntity from(
            Chunk chunk, UUID tenantId, UUID documentId, UUID versionId, Instant now) {
        var entity = new ChunkJpaEntity();
        entity.id =
                UUID.nameUUIDFromBytes(
                        (tenantId
                                        + ":"
                                        + versionId
                                        + ":"
                                        + chunk.ordinal()
                                        + ":"
                                        + chunk.contentChecksum())
                                .getBytes(StandardCharsets.UTF_8));
        entity.tenantId = tenantId;
        entity.documentId = documentId;
        entity.documentVersionId = versionId;
        entity.ordinal = chunk.ordinal();
        entity.pageNumber = chunk.pageNumber();
        entity.heading = chunk.heading();
        entity.content = chunk.content();
        entity.contentChecksum = chunk.contentChecksum();
        entity.tokenCount = chunk.tokenCount();
        entity.createdAt = now;
        return entity;
    }
}
