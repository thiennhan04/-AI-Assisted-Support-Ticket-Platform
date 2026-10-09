package com.portfolio.knowledge.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.contracts.EventEnvelope;
import com.portfolio.contracts.KnowledgeIngestRequestedV1;
import com.portfolio.knowledge.domain.DocumentRepository;
import com.portfolio.knowledge.domain.KnowledgeDocument;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

@Repository
public class JpaDocumentRepositoryAdapter implements DocumentRepository {
    private final DocumentSpringDataRepository documents;
    private final KnowledgeOutboxSpringDataRepository outbox;
    private final ObjectMapper mapper;

    public JpaDocumentRepositoryAdapter(
            DocumentSpringDataRepository documents,
            KnowledgeOutboxSpringDataRepository outbox,
            ObjectMapper mapper) {
        this.documents = documents;
        this.outbox = outbox;
        this.mapper = mapper;
    }

    @Override
    public KnowledgeDocument save(KnowledgeDocument document) {
        return documents.saveAndFlush(DocumentJpaEntity.fromDomain(document)).toDomain();
    }

    @Override
    public Optional<KnowledgeDocument> find(UUID tenantId, UUID id) {
        return documents.findByTenantIdAndId(tenantId, id).map(DocumentJpaEntity::toDomain);
    }

    @Override
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
    public void appendIngestionRequested(KnowledgeDocument document, Instant now) {
        var eventId = UUID.randomUUID();
        var type = "knowledge.ingest.requested.v1";
        var data = new KnowledgeIngestRequestedV1(document.id(), document.version().id());
        var envelope =
                new EventEnvelope<>(
                        eventId,
                        type,
                        now,
                        "knowledge-service",
                        eventId.toString(),
                        null,
                        document.tenantId(),
                        document.id(),
                        1,
                        data);
        var entity = new KnowledgeOutboxJpaEntity();
        entity.id = eventId;
        entity.tenantId = document.tenantId();
        entity.subjectId = document.id();
        entity.eventType = type;
        entity.routingKey = type;
        entity.payload = mapper.valueToTree(envelope);
        entity.status = "PENDING";
        entity.createdAt = now;
        entity.nextAttemptAt = now;
        outbox.save(entity);
    }
}
