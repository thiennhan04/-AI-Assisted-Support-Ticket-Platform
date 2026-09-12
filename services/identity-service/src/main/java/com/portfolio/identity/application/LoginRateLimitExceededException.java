package com.portfolio.identity.application;

import java.time.Duration;

public final class LoginRateLimitExceededException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final Duration retryAfter;

    public LoginRateLimitExceededException(Duration retryAfter) {
        super("Login rate limit exceeded");
        this.retryAfter = retryAfter;
    }

    public Duration retryAfter() {
        return retryAfter;
    }
}
