package com.portfolio.knowledge.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class ServiceJwtClaimsValidatorTest {
    private final ServiceJwtClaimsValidator validator =
            new ServiceJwtClaimsValidator("knowledge-internal", "ai-orchestrator-service");

    @Test
    void acceptsTenantBoundSignedDelegationForKnownService() {
        var token = token("ai-orchestrator-service", "knowledge-internal", List.of("AGENT"));
        assertThat(validator.validate(token).hasErrors()).isFalse();
        var caller = ServiceJwtClaimsValidator.delegatedCaller(token);
        assertThat(caller.tenantId().toString()).isEqualTo(token.getClaimAsString("tid"));
    }

    @Test
    void rejectsUserTokensWrongAudienceAndUnknownRoles() {
        assertThat(
                        validator
                                .validate(
                                        token(
                                                UUID.randomUUID().toString(),
                                                "knowledge-internal",
                                                List.of("ADMIN")))
                                .hasErrors())
                .isTrue();
        assertThat(
                        validator
                                .validate(
                                        token(
                                                "ai-orchestrator-service",
                                                "ticket-platform",
                                                List.of("AGENT")))
                                .hasErrors())
                .isTrue();
        assertThat(
                        validator
                                .validate(
                                        token(
                                                "ai-orchestrator-service",
                                                "knowledge-internal",
                                                List.of("SUPERUSER")))
                                .hasErrors())
                .isTrue();
    }

    private Jwt token(String subject, String audience, List<String> roles) {
        return Jwt.withTokenValue("test")
                .header("alg", "RS256")
                .subject(subject)
                .audience(List.of(audience))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .claim("tid", UUID.randomUUID().toString())
                .claim("principal_id", UUID.randomUUID().toString())
                .claim("roles", roles)
                .claim("scope", "knowledge:search")
                .build();
    }
}
