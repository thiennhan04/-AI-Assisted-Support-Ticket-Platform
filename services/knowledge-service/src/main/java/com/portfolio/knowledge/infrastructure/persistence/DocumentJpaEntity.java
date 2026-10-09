package com.portfolio.knowledge.infrastructure.persistence;

import com.portfolio.knowledge.domain.DocumentAccessScope;
import com.portfolio.knowledge.domain.DocumentStatus;
import com.portfolio.knowledge.domain.KnowledgeDocument;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "knowledge_document", schema = "knowledge")
class DocumentJpaEntity {
    @Id UUID id;

    @Column(nullable = false)
    UUID tenantId;

    @Column(nullable = false)
    String title;

    @Column(nullable = false)
    String fileName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    DocumentAccessScope visibility;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    List<String> tags;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    DocumentStatus status;

    @Column(nullable = false)
    UUID createdBy;

    @Column(nullable = false)
    Instant createdAt;

    @Column(nullable = false)
    Instant updatedAt;

    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JoinColumn(name = "current_version_id", nullable = false)
    DocumentVersionJpaEntity version;

    protected DocumentJpaEntity() {}

    static DocumentJpaEntity fromDomain(KnowledgeDocument document) {
        var entity = new DocumentJpaEntity();
        entity.id = document.id();
        entity.tenantId = document.tenantId();
        entity.title = document.title();
        entity.fileName = document.fileName();
        entity.visibility = document.visibility();
        entity.tags = document.tags();
        entity.status = document.version().status();
        entity.createdBy = document.createdBy();
        entity.createdAt = document.createdAt();
        entity.updatedAt = document.updatedAt();
        entity.version =
                DocumentVersionJpaEntity.from(
                        document.version(), document.id(), document.createdAt());
        return entity;
    }

    KnowledgeDocument toDomain() {
        return new KnowledgeDocument(
                id,
                tenantId,
                title,
                fileName,
                visibility,
                tags,
                createdBy,
                createdAt,
                updatedAt,
                version.toDomain());
    }
}
