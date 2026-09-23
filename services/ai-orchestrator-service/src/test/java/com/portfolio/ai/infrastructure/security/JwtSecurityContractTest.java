package com.portfolio.ai.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.portfolio.ai.application.AuthenticatedPrincipal;
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
        var jwt = jwt(USER_ID.toString(), TENANT_ID.toString(), List.of("AGENT"));

        assertThat(new JwtClaimsValidator().validate(jwt).hasErrors()).isFalse();
        var authentication = new PlatformJwtAuthenticationConverter().convert(jwt);
        assertThat(authentication.getPrincipal())
                .isEqualTo(new AuthenticatedPrincipal(USER_ID, TENANT_ID, Set.of("AGENT")));
        assertThat(authentication.getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_AGENT");
    }

    @Test
    void rejectsMissingSubjectAndEmptyRoles() {
        assertThat(
                        new JwtClaimsValidator()
                                .validate(jwt(null, TENANT_ID.toString(), List.of("AGENT")))
                                .hasErrors())
                .isTrue();
        assertThat(
                        new JwtClaimsValidator()
                                .validate(jwt(USER_ID.toString(), TENANT_ID.toString(), List.of()))
                                .hasErrors())
                .isTrue();
    }

    private Jwt jwt(String subject, String tenantId, List<String> roles) {
        var builder =
                Jwt.withTokenValue("test-token")
                        .header("alg", "RS256")
                        .issuedAt(Instant.now())
                        .expiresAt(Instant.now().plusSeconds(300))
                        .claim("tid", tenantId)
                        .claim("roles", roles);
        if (subject != null) {
            builder.subject(subject);
        }
        return builder.build();
    }
}
