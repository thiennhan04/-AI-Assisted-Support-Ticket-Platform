package com.portfolio.knowledge.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface DocumentSpringDataRepository extends JpaRepository<DocumentJpaEntity, UUID> {
    @Query(
            value =
                    """
            SELECT d.* FROM knowledge.knowledge_document d
            WHERE EXISTS (
                SELECT 1 FROM knowledge.document_version v WHERE v.document_id = d.id
                  AND v.cleanup_after <= :now AND v.cleaned_at IS NULL
                  AND (v.cleanup_lease_until IS NULL OR v.cleanup_lease_until <= :now)
                  AND (d.status = 'DELETING'
                       OR (v.id <> d.current_version_id AND v.id IS DISTINCT FROM d.active_version_id))
            ) ORDER BY d.updated_at, d.id LIMIT 1 FOR UPDATE SKIP LOCKED
            """,
            nativeQuery = true)
    Optional<DocumentJpaEntity> findForCleanup(Instant now);

    @Modifying
    @Query(
            value = "DELETE FROM knowledge.document_acl WHERE document_id = :documentId",
            nativeQuery = true)
    void deleteAcl(UUID documentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            "select d from DocumentJpaEntity d join fetch d.version where d.tenantId = :tenantId and d.id = :id")
    Optional<DocumentJpaEntity> findForPreparation(UUID tenantId, UUID id);

    @EntityGraph(attributePaths = "version")
    Optional<DocumentJpaEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    List<DocumentJpaEntity> findByTenantId(UUID tenantId, Pageable pageable);
}
