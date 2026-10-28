package com.portfolio.knowledge.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.contracts.KnowledgeIngestRequestedV1;
import com.portfolio.knowledge.application.DocumentCleanupProperties;
import com.portfolio.knowledge.domain.DocumentException;
import com.portfolio.knowledge.domain.DocumentLifecycleRepository;
import com.portfolio.knowledge.domain.DocumentStatus;
import com.portfolio.knowledge.domain.DocumentVersion;
import com.portfolio.knowledge.domain.KnowledgeDocument;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JpaDocumentLifecycleRepositoryAdapter implements DocumentLifecycleRepository {
    private final DocumentSpringDataRepository documents;
    private final DocumentVersionSpringDataRepository versions;
    private final ChunkSpringDataRepository chunks;
    private final KnowledgeOutboxSpringDataRepository outbox;
    private final DocumentCleanupProperties properties;
    private final ObjectMapper mapper;

    public JpaDocumentLifecycleRepositoryAdapter(
            DocumentSpringDataRepository documents,
            DocumentVersionSpringDataRepository versions,
            ChunkSpringDataRepository chunks,
            KnowledgeOutboxSpringDataRepository outbox,
            DocumentCleanupProperties properties,
            ObjectMapper mapper) {
        this.documents = documents;
        this.versions = versions;
        this.chunks = chunks;
        this.outbox = outbox;
        this.properties = properties;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public KnowledgeDocument stageReplacement(
            UUID tenantId,
            UUID documentId,
            int expectedVersion,
            DocumentVersion version,
            String fileName,
            Instant now) {
        var document =
                documents.findForPreparation(tenantId, documentId).orElseThrow(this::notFound);
        if (document.status == DocumentStatus.DELETING
                || document.status == DocumentStatus.DELETED) {
            throw new DocumentException(
                    "DOCUMENT_DELETED", 409, "Deleted documents cannot be replaced");
        }
        var previous = Hibernate.unproxy(document.version, DocumentVersionJpaEntity.class);
        if (previous.versionNumber != expectedVersion) {
            throw new DocumentException(
                    "DOCUMENT_VERSION_CONFLICT",
                    412,
                    "Document changed; reload it before replacing the file");
        }
        if (previous.status == DocumentStatus.QUEUED
                || previous.status == DocumentStatus.PROCESSING) {
            throw new DocumentException(
                    "DOCUMENT_VERSION_BUSY", 409, "The current version is still being processed");
        }
        if (version.versionNumber() != expectedVersion + 1) {
            throw new DocumentException(
                    "DOCUMENT_VERSION_CONFLICT", 409, "Replacement version number is invalid");
        }
        if (!previous.id.equals(document.activeVersionId) && previous.cleanedAt == null) {
            previous.status = DocumentStatus.SUPERSEDED;
            previous.cleanupAfter = now.plus(properties.gracePeriod());
        }
        var replacement = DocumentVersionJpaEntity.from(version, documentId, now);
        replacement.fileName = fileName;
        replacement = versions.saveAndFlush(replacement);
        document.version = replacement;
        document.fileName = fileName;
        document.status = DocumentStatus.QUEUED;
        document.updatedAt = now;
        documents.saveAndFlush(document);
        var data = new KnowledgeIngestRequestedV1(documentId, version.id());
        var event =
                KnowledgeOutboxJpaEntity.event(
                        KnowledgeIngestRequestedV1.EVENT_TYPE,
                        tenantId,
                        documentId,
                        data,
                        now,
                        mapper);
        outbox.save(event);
        return document.toDomain();
    }

    @Override
    @Transactional
    public void requestDeletion(UUID tenantId, UUID documentId, Instant now) {
        var document =
                documents.findForPreparation(tenantId, documentId).orElseThrow(this::notFound);
        if (document.status == DocumentStatus.DELETING
                || document.status == DocumentStatus.DELETED) {
            return;
        }
        // Clearing the search pointer and scheduling every version happen in the same transaction.
        document.activeVersionId = null;
        document.status = DocumentStatus.DELETING;
        document.updatedAt = now;
        var allVersions = versions.findByDocumentIdOrderByVersionNumber(documentId);
        for (var storedVersion : allVersions) {
            var version = Hibernate.unproxy(storedVersion, DocumentVersionJpaEntity.class);
            if (version.cleanedAt == null) {
                version.status = DocumentStatus.DELETING;
                version.cleanupAfter = now;
            }
        }
        versions.saveAll(allVersions);
        documents.deleteAcl(documentId);
        documents.saveAndFlush(document);
    }

    @Override
    @Transactional
    public Optional<CleanupWork> claimCleanup(Instant now) {
        var candidate = documents.findForCleanup(now);
        if (candidate.isEmpty()) {
            return Optional.empty();
        }
        var document = candidate.get();
        document.version = Hibernate.unproxy(document.version, DocumentVersionJpaEntity.class);
        var due = versions.findDueCleanup(document.id, now);
        if (due.isEmpty()) {
            return Optional.empty();
        }
        var version = Hibernate.unproxy(due.get(), DocumentVersionJpaEntity.class);
        if (!canClean(document, version)) {
            return Optional.empty();
        }
        var token = UUID.randomUUID();
        version.cleanupToken = token;
        version.cleanupLeaseUntil = now.plus(properties.leaseDuration());
        version.cleanupAttempts++;
        version.status = DocumentStatus.DELETING;
        versions.saveAndFlush(version);
        var work =
                new CleanupWork(
                        document.tenantId, document.id, version.id, version.objectKey, token);
        return Optional.of(work);
    }

    @Override
    @Transactional
    public void completeCleanup(CleanupWork work, Instant now) {
        var document =
                documents
                        .findForPreparation(work.tenantId(), work.documentId())
                        .orElseThrow(this::notFound);
        var version = findVersion(work.documentId(), work.versionId());
        if (!work.token().equals(version.cleanupToken) || !canClean(document, version)) {
            return;
        }
        chunks.deleteByTenantIdAndDocumentIdAndDocumentVersionId(
                work.tenantId(), work.documentId(), work.versionId());
        version.status = DocumentStatus.DELETED;
        version.cleanedAt = now;
        version.cleanupToken = null;
        version.cleanupLeaseUntil = null;
        version.cleanupErrorCode = null;
        versions.saveAndFlush(version);
        boolean unfinished =
                versions.existsByDocumentIdAndStatusNot(work.documentId(), DocumentStatus.DELETED);
        if (document.status == DocumentStatus.DELETING && !unfinished) {
            document.status = DocumentStatus.DELETED;
            document.updatedAt = now;
            documents.saveAndFlush(document);
        }
    }

    @Override
    @Transactional
    public void retryCleanup(CleanupWork work, Instant now) {
        documents
                .findForPreparation(work.tenantId(), work.documentId())
                .orElseThrow(this::notFound);
        var version = findVersion(work.documentId(), work.versionId());
        if (!work.token().equals(version.cleanupToken) || version.cleanedAt != null) {
            return;
        }
        version.cleanupAfter = now.plus(properties.retryDelay());
        version.cleanupToken = null;
        version.cleanupLeaseUntil = null;
        version.cleanupErrorCode = "STORAGE_UNAVAILABLE";
        versions.saveAndFlush(version);
    }

    private boolean canClean(DocumentJpaEntity document, DocumentVersionJpaEntity version) {
        if (version.cleanedAt != null) {
            return false;
        }
        if (document.status == DocumentStatus.DELETING) {
            return true;
        }
        boolean current = version.id.equals(document.version.id);
        boolean active = version.id.equals(document.activeVersionId);
        return !current && !active;
    }

    private DocumentVersionJpaEntity findVersion(UUID documentId, UUID versionId) {
        var version =
                versions.findByDocumentIdAndId(documentId, versionId).orElseThrow(this::notFound);
        // Queries can return an existing Hibernate proxy; field access needs the underlying entity.
        return Hibernate.unproxy(version, DocumentVersionJpaEntity.class);
    }

    private DocumentException notFound() {
        return new DocumentException("DOCUMENT_NOT_FOUND", 404, "Document was not found");
    }
}
