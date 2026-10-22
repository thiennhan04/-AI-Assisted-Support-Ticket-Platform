package com.portfolio.knowledge.infrastructure.persistence;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface ChunkSpringDataRepository extends JpaRepository<ChunkJpaEntity, UUID> {
    List<ChunkJpaEntity> findByTenantIdAndDocumentIdAndDocumentVersionIdOrderByOrdinal(
            UUID tenantId, UUID documentId, UUID documentVersionId);

    @Query(
            value =
                    """
            WITH eligible AS MATERIALIZED (
                SELECT c.id, c.document_id, d.title, v.version_number,
                       c.page_number, c.heading, c.content, c.embedding
                FROM knowledge.document_chunk c
                JOIN knowledge.knowledge_document d ON d.id = c.document_id AND d.tenant_id = c.tenant_id
                JOIN knowledge.document_version v ON v.id = c.document_version_id AND v.document_id = d.id
                WHERE c.tenant_id = :tenantId
                  AND c.document_version_id = d.active_version_id
                  AND d.status NOT IN ('DELETING', 'DELETED')
                  AND v.status = 'ACTIVE'
                  AND v.embedding_model = :modelId AND v.embedding_dimension = 1536
                  AND c.embedding IS NOT NULL
                  AND d.tags @> CAST(:tags AS jsonb)
                  AND (
                      d.visibility = 'ALL_TENANT_USERS'
                      OR (d.visibility = 'SUPPORT_TEAM_ONLY' AND :staff)
                      OR (d.visibility = 'LIMITED_ACCESS' AND EXISTS (
                          SELECT 1 FROM knowledge.document_acl a
                          WHERE a.document_id = d.id
                            AND ((a.principal_type = 'USER' AND a.principal_value = :principalId)
                              OR (a.principal_type = 'ROLE' AND a.principal_value IN (:roles)))
                      ))
                  )
            )
            SELECT id AS "chunkId", document_id AS "documentId", title,
                   version_number AS "version", page_number AS "pageNumber", heading,
                   left(content, 1500) AS excerpt,
                   1 - cosine_distance(embedding, CAST(:query AS vector)) AS score
            FROM eligible
            ORDER BY cosine_distance(embedding, CAST(:query AS vector)), id
            LIMIT :topK
            """,
            nativeQuery = true)
    List<SearchRow> search(
            UUID tenantId,
            String principalId,
            Set<String> roles,
            boolean staff,
            String modelId,
            String query,
            String tags,
            int topK);

    interface SearchRow {
        UUID getChunkId();

        UUID getDocumentId();

        String getTitle();

        int getVersion();

        Integer getPageNumber();

        String getHeading();

        String getExcerpt();

        double getScore();
    }
}
