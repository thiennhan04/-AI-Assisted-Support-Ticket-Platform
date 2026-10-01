package com.portfolio.ticket.infrastructure.messaging;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(OutboxProperties.class)
@ConditionalOnProperty(name = "ticket.outbox.enabled", havingValue = "true", matchIfMissing = true)
class OutboxConfiguration {

    static final String DOMAIN_EXCHANGE = "platform.domain.x";

    @Bean
    TopicExchange domainExchange() {
        return new TopicExchange(DOMAIN_EXCHANGE, true, false);
    }
}
