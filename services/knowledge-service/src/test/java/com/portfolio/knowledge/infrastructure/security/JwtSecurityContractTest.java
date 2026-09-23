package com.portfolio.knowledge.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.portfolio.knowledge.application.AuthenticatedPrincipal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class JwtSecurityContractTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();

    @Test
    void acceptsMandatoryClaimsAndMapsTenantAwarePrincipal() {
        var jwt = jwt(USER_ID.toString(), TENANT_ID.toString(), List.of("ADMIN"));

        assertThat(new JwtClaimsValidator().validate(jwt).hasErrors()).isFalse();
        var authentication = new PlatformJwtAuthenticationConverter().convert(jwt);
        assertThat(authentication.getPrincipal())
                .isEqualTo(new AuthenticatedPrincipal(USER_ID, TENANT_ID, Set.of("ADMIN")));
        assertThat(authentication.getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    void rejectsMissingTenantAndUnknownRole() {
        assertThat(
                        new JwtClaimsValidator()
                                .validate(jwt(USER_ID.toString(), null, List.of("ADMIN")))
                                .hasErrors())
                .isTrue();
        assertThat(
                        new JwtClaimsValidator()
                                .validate(
                                        jwt(
                                                USER_ID.toString(),
                                                TENANT_ID.toString(),
                                                List.of("SUPERUSER")))
                                .hasErrors())
                .isTrue();
    }

    private Jwt jwt(String subject, String tenantId, List<String> roles) {
        var builder =
                Jwt.withTokenValue("test-token")
                        .header("alg", "RS256")
                        .subject(subject)
                        .issuedAt(Instant.now())
                        .expiresAt(Instant.now().plusSeconds(300))
                        .claim("roles", roles);
        if (tenantId != null) {
            builder.claim("tid", tenantId);
        }
        return builder.build();
    }
}
