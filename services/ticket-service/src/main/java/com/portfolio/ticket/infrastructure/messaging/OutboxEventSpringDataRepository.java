package com.portfolio.ticket.infrastructure.messaging;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

interface OutboxEventSpringDataRepository extends JpaRepository<OutboxEventJpaEntity, UUID> {

    @Transactional
    @Query(
            value =
                    """
                    WITH candidates AS (
                      SELECT id
                      FROM ticket.outbox_event
                      WHERE (status = 'PENDING' AND next_attempt_at <= :now)
                         OR (status = 'PROCESSING' AND locked_at <= :staleBefore)
                      ORDER BY created_at, id
                      FOR UPDATE SKIP LOCKED
                      LIMIT :batchSize
                    )
                    UPDATE ticket.outbox_event AS event
                    SET status = 'PROCESSING',
                        attempt_count = event.attempt_count + 1,
                        locked_at = :now,
                        last_error = NULL
                    FROM candidates
                    WHERE event.id = candidates.id
                    RETURNING event.*
                    """,
            nativeQuery = true)
    List<OutboxEventJpaEntity> claimBatch(
            @Param("now") Instant now,
            @Param("staleBefore") Instant staleBefore,
            @Param("batchSize") int batchSize);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            """
            update OutboxEventJpaEntity event
               set event.status = 'PUBLISHED',
                   event.publishedAt = :publishedAt,
                   event.lockedAt = null
             where event.id = :eventId
               and event.status = 'PROCESSING'
            """)
    int markPublished(@Param("eventId") UUID eventId, @Param("publishedAt") Instant publishedAt);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            """
            update OutboxEventJpaEntity event
               set event.status = 'PENDING',
                   event.nextAttemptAt = :nextAttemptAt,
                   event.lockedAt = null,
                   event.lastError = :error
             where event.id = :eventId
               and event.status = 'PROCESSING'
            """)
    int reschedule(
            @Param("eventId") UUID eventId,
            @Param("nextAttemptAt") Instant nextAttemptAt,
            @Param("error") String error);
}
