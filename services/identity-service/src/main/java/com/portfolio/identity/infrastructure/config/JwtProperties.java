package com.portfolio.identity.infrastructure.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("identity.jwt")
public record JwtProperties(
        @NotBlank String issuer,
        @NotBlank String audience,
        @NotBlank String keyId,
        @NotNull Duration accessTtl,
        @NotNull Duration refreshTtl,
        @NotNull Resource privateKey,
        @NotNull Resource publicKey,
        String previousKeyId,
        Resource previousPublicKey) {}
