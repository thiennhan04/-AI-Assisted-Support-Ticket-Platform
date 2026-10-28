package com.portfolio.knowledge.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("knowledge.cleanup")
public record DocumentCleanupProperties(
        Duration gracePeriod, Duration leaseDuration, Duration retryDelay, int batchSize) {
    public DocumentCleanupProperties {
        if (gracePeriod == null
                || gracePeriod.isNegative()
                || leaseDuration == null
                || leaseDuration.compareTo(Duration.ofMinutes(1)) < 0
                || retryDelay == null
                || retryDelay.isNegative()
                || retryDelay.isZero()
                || batchSize < 1
                || batchSize > 100) {
            throw new IllegalArgumentException("Invalid document cleanup configuration");
        }
    }
}
