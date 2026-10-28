package com.portfolio.knowledge.application;

import com.portfolio.knowledge.domain.DocumentException;
import com.portfolio.knowledge.domain.DocumentLifecycleRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentCleanupService {
    private final DocumentLifecycleRepository documents;
    private final DocumentStorage storage;
    private final Clock clock;

    public DocumentCleanupService(
            DocumentLifecycleRepository documents, DocumentStorage storage, Clock clock) {
        this.documents = documents;
        this.storage = storage;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public boolean processNext() {
        var claimed = documents.claimCleanup(clock.instant());
        if (claimed.isEmpty()) {
            return false;
        }
        var work = claimed.get();
        try {
            // Removing an already absent object is safe when a crashed job is retried.
            storage.delete(work.objectKey());
        } catch (DocumentException exception) {
            documents.retryCleanup(work, clock.instant());
            return true;
        }
        // If this transaction fails, the durable lease expires and the same object is removed
        // again.
        documents.completeCleanup(work, clock.instant());
        return true;
    }
}
