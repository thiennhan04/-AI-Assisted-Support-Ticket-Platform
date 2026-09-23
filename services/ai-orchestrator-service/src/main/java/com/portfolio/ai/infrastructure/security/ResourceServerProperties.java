package com.portfolio.ai.infrastructure.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("security.jwt")
public record ResourceServerProperties(
        @NotBlank String issuer, @NotBlank String audience, @NotNull URI jwkSetUri) {}
