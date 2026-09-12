package com.portfolio.identity.infrastructure.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("identity.login-protection")
public record LoginProtectionProperties(
        @Min(1) int maxFailures,
        @NotNull Duration lockDuration,
        @Min(1) int rateLimit,
        @NotNull Duration rateWindow,
        @NotNull Duration failureDelayMin,
        @NotNull Duration failureDelayMax) {

    public LoginProtectionProperties {
        if (failureDelayMax != null
                && failureDelayMin != null
                && failureDelayMax.compareTo(failureDelayMin) < 0) {
            throw new IllegalArgumentException("failureDelayMax must be >= failureDelayMin");
        }
    }
}
