package com.portfolio.identity.application;

import java.time.Duration;
import java.util.Optional;

public interface LoginRateLimiter {

    Optional<Duration> consume(String tenantCode, String normalizedEmail, String clientAddress);
}
