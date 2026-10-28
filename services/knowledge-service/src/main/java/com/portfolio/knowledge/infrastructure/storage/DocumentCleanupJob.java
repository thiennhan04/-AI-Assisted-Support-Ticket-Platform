package com.portfolio.knowledge.infrastructure.storage;

import com.portfolio.knowledge.application.DocumentCleanupProperties;
import com.portfolio.knowledge.application.DocumentCleanupService;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@ConditionalOnProperty(
        name = "knowledge.cleanup.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class DocumentCleanupJob {
    private final DocumentCleanupService service;
    private final DocumentCleanupProperties properties;

    public DocumentCleanupJob(
            DocumentCleanupService service, DocumentCleanupProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${knowledge.cleanup.poll-delay:30s}")
    public void run() {
        try {
            for (int i = 0; i < properties.batchSize(); i++) {
                if (!service.processNext()) {
                    break;
                }
            }
        } catch (RuntimeException exception) {
            LoggerFactory.getLogger(DocumentCleanupJob.class)
                    .warn(
                            "event=document_cleanup_failed causeType={}",
                            exception.getClass().getSimpleName());
        }
    }
}
