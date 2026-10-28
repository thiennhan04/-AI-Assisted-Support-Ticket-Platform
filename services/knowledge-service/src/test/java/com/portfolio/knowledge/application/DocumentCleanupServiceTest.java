package com.portfolio.knowledge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.portfolio.knowledge.domain.DocumentException;
import com.portfolio.knowledge.domain.DocumentLifecycleRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

class DocumentCleanupServiceTest {
    private final DocumentLifecycleRepository documents = mock(DocumentLifecycleRepository.class);
    private final DocumentStorage storage = mock(DocumentStorage.class);
    private final Instant now = Instant.parse("2026-10-24T00:00:00Z");
    private final DocumentCleanupService service =
            new DocumentCleanupService(documents, storage, Clock.fixed(now, ZoneOffset.UTC));
    private final DocumentLifecycleRepository.CleanupWork work =
            new DocumentLifecycleRepository.CleanupWork(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "documents/tenant/version",
                    UUID.randomUUID());

    @Test
    void deletesObjectBeforeCompletingDatabaseCleanup() {
        when(documents.claimCleanup(now)).thenReturn(Optional.of(work));
        assertThat(service.processNext()).isTrue();
        var order = inOrder(documents, storage);
        order.verify(documents).claimCleanup(now);
        order.verify(storage).delete(work.objectKey());
        order.verify(documents).completeCleanup(work, now);
    }

    @Test
    void storageFailureSchedulesRetryWithoutDeletingChunkMetadata() {
        when(documents.claimCleanup(now)).thenReturn(Optional.of(work));
        doThrow(new DocumentException("STORAGE_UNAVAILABLE", 503, "Unavailable"))
                .when(storage)
                .delete(work.objectKey());
        assertThat(service.processNext()).isTrue();
        verify(documents).retryCleanup(work, now);
        verify(documents, never()).completeCleanup(any(), any());
    }

    @Test
    void databaseFailureLeavesDurableLeaseForRecovery() {
        when(documents.claimCleanup(now)).thenReturn(Optional.of(work));
        doThrow(new DataAccessResourceFailureException("Database unavailable"))
                .when(documents)
                .completeCleanup(work, now);
        assertThatThrownBy(service::processNext)
                .isInstanceOf(DataAccessResourceFailureException.class);
        verify(storage).delete(work.objectKey());
        verify(documents, never()).retryCleanup(any(), any());
    }

    @Test
    void noDueJobDoesNotTouchStorage() {
        when(documents.claimCleanup(now)).thenReturn(Optional.empty());
        assertThat(service.processNext()).isFalse();
        verifyNoInteractions(storage);
    }
}
