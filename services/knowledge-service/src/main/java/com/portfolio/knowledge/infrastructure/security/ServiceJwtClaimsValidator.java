package com.portfolio.knowledge.infrastructure.security;

import com.portfolio.knowledge.application.AuthenticatedPrincipal;
import com.portfolio.knowledge.application.Roles;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

public class ServiceJwtClaimsValidator implements OAuth2TokenValidator<Jwt> {
    private static final Set<String> KNOWN_ROLES = Set.of(Roles.ADMIN, Roles.AGENT, Roles.CUSTOMER);
    private final String audience;
    private final String serviceSubject;

    public ServiceJwtClaimsValidator(String audience, String serviceSubject) {
        this.audience = audience;
        this.serviceSubject = serviceSubject;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        try {
            if (!serviceSubject.equals(token.getSubject())
                    || !token.getAudience().contains(audience)
                    || token.getExpiresAt() == null
                    || token.getIssuedAt() == null) {
                return invalid();
            }
            delegatedCaller(token);
            return OAuth2TokenValidatorResult.success();
        } catch (RuntimeException exception) {
            return invalid();
        }
    }

    public static AuthenticatedPrincipal delegatedCaller(Jwt token) {
        var tenantId = UUID.fromString(token.getClaimAsString("tid"));
        var principalId = UUID.fromString(token.getClaimAsString("principal_id"));
        List<String> roleClaims = token.getClaimAsStringList("roles");
        if (roleClaims == null || roleClaims.isEmpty() || !KNOWN_ROLES.containsAll(roleClaims)) {
            throw new IllegalArgumentException("Invalid delegated roles");
        }
        var roles = new HashSet<>(roleClaims);
        return new AuthenticatedPrincipal(principalId, tenantId, roles);
    }

    private OAuth2TokenValidatorResult invalid() {
        var error =
                new OAuth2Error("invalid_token", "Invalid service identity or delegation", null);
        return OAuth2TokenValidatorResult.failure(error);
    }
}
