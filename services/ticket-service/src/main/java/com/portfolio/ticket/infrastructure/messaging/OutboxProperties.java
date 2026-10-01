package com.portfolio.ticket.infrastructure.messaging;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("ticket.outbox")
record OutboxProperties(
        @DefaultValue("20") int batchSize,
        @DefaultValue("30s") Duration claimTimeout,
        @DefaultValue("5s") Duration confirmTimeout,
        @DefaultValue("60s") Duration retryMaxDelay) {}
