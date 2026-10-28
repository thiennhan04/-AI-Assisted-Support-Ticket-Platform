package com.portfolio.knowledge.infrastructure.persistence;

import com.portfolio.knowledge.domain.DocumentStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface DocumentVersionSpringDataRepository
        extends JpaRepository<DocumentVersionJpaEntity, UUID> {
    List<DocumentVersionJpaEntity> findByDocumentIdOrderByVersionNumber(UUID documentId);

    Optional<DocumentVersionJpaEntity> findByDocumentIdAndId(UUID documentId, UUID id);

    boolean existsByDocumentIdAndStatusNot(UUID documentId, DocumentStatus status);

    @Query(
            value =
                    """
            SELECT v.* FROM knowledge.document_version v
            JOIN knowledge.knowledge_document d ON d.id = v.document_id
            WHERE v.document_id = :documentId AND v.cleanup_after <= :now AND v.cleaned_at IS NULL
              AND (v.cleanup_lease_until IS NULL OR v.cleanup_lease_until <= :now)
              AND (d.status = 'DELETING'
                   OR (v.id <> d.current_version_id AND v.id IS DISTINCT FROM d.active_version_id))
            ORDER BY v.cleanup_after, v.version_number LIMIT 1
            """,
            nativeQuery = true)
    Optional<DocumentVersionJpaEntity> findDueCleanup(UUID documentId, Instant now);
}
