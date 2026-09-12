package com.portfolio.identity.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("identity.local-seed")
public record LocalSeedProperties(boolean enabled, String password) {}
