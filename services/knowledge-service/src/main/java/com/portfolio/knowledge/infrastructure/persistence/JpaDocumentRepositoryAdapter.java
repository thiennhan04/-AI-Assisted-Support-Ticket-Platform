package com.portfolio.knowledge.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.contracts.KnowledgeIngestRequestedV1;
import com.portfolio.knowledge.domain.Chunk;
import com.portfolio.knowledge.domain.DocumentException;
import com.portfolio.knowledge.domain.DocumentRepository;
import com.portfolio.knowledge.domain.DocumentStatus;
import com.portfolio.knowledge.domain.KnowledgeDocument;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JpaDocumentRepositoryAdapter implements DocumentRepository {
    private final DocumentSpringDataRepository documents;
    private final KnowledgeOutboxSpringDataRepository outbox;
    private final ObjectMapper mapper;
    private final ChunkSpringDataRepository chunks;

    public JpaDocumentRepositoryAdapter(
            DocumentSpringDataRepository documents,
            KnowledgeOutboxSpringDataRepository outbox,
            ObjectMapper mapper,
            ChunkSpringDataRepository chunks) {
        this.documents = documents;
        this.outbox = outbox;
        this.mapper = mapper;
        this.chunks = chunks;
    }

    @Override
    public KnowledgeDocument save(KnowledgeDocument document) {
        return documents.saveAndFlush(DocumentJpaEntity.fromDomain(document)).toDomain();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<KnowledgeDocument> find(UUID tenantId, UUID id) {
        return documents.findByTenantIdAndId(tenantId, id).map(DocumentJpaEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<KnowledgeDocument> list(UUID tenantId, int page, int size) {
        return documents
                .findByTenantId(
                        tenantId,
                        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")))
                .stream()
                .map(DocumentJpaEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isPrepared(UUID tenantId, UUID documentId, UUID versionId) {
        return documents
                .findByTenantIdAndId(tenantId, documentId)
                .filter(document -> document.version.id.equals(versionId))
                .map(document -> document.version.preparedAt != null)
                .orElse(false);
    }

    @Override
    @Transactional
    public void saveChunks(
            UUID tenantId,
            UUID documentId,
            UUID versionId,
            List<Chunk> prepared,
            String chunkingVersion,
            Instant now) {
        if (prepared.isEmpty()) {
            throw new DocumentException(
                    "DOCUMENT_EMPTY", 422, "Document contains no extractable text");
        }
        var document = currentVersionForPreparation(tenantId, documentId, versionId);
        if (document.version.preparedAt != null) {
            return;
        }
        if (document.status != DocumentStatus.QUEUED) {
            throw new DocumentException(
                    "DOCUMENT_NOT_QUEUED", 409, "Document is not queued for extraction");
        }
        chunks.saveAll(
                prepared.stream()
                        .map(
                                chunk ->
                                        ChunkJpaEntity.from(
                                                chunk, tenantId, documentId, versionId, now))
                        .toList());
        document.version.chunkingVersion = chunkingVersion;
        document.version.preparedAt = now;
        document.version.status = DocumentStatus.PROCESSING;
        document.version.errorCode = null;
        document.status = DocumentStatus.PROCESSING;
        document.updatedAt = now;
        // No active pointer is changed until embedding completes in DD-303/DD-304.
        documents.saveAndFlush(document);
    }

    @Override
    @Transactional
    public void failPreparation(
            UUID tenantId, UUID documentId, UUID versionId, String errorCode, Instant now) {
        var document = currentVersionForPreparation(tenantId, documentId, versionId);
        if (document.version.preparedAt != null || document.status != DocumentStatus.QUEUED) {
            return;
        }
        document.version.status = DocumentStatus.FAILED;
        document.version.errorCode = errorCode;
        document.status = DocumentStatus.FAILED;
        document.updatedAt = now;
        documents.saveAndFlush(document);
    }

    private DocumentJpaEntity currentVersionForPreparation(
            UUID tenantId, UUID documentId, UUID versionId) {
        var document =
                documents
                        .findForPreparation(tenantId, documentId)
                        .orElseThrow(
                                () ->
                                        new DocumentException(
                                                "DOCUMENT_NOT_FOUND",
                                                404,
                                                "Document was not found"));
        if (!document.version.id.equals(versionId)) {
            throw new DocumentException(
                    "DOCUMENT_VERSION_CONFLICT", 409, "Document version is no longer current");
        }
        return document;
    }

    @Override
    public void appendIngestionRequested(KnowledgeDocument document, Instant now) {
        var data = new KnowledgeIngestRequestedV1(document.id(), document.version().id());
        var entity =
                KnowledgeOutboxJpaEntity.event(
                        KnowledgeIngestRequestedV1.EVENT_TYPE,
                        document.tenantId(),
                        document.id(),
                        data,
                        now,
                        mapper);
        outbox.save(entity);
    }
}
