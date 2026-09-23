package com.portfolio.ticket.infrastructure.security;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;

public final class JwtClaimsValidator implements OAuth2TokenValidator<Jwt> {

    private static final Set<String> ALLOWED_ROLES = Set.of("CUSTOMER", "AGENT", "ADMIN");
    private static final OAuth2Error INVALID_CLAIMS =
            new OAuth2Error("invalid_token", "Missing or invalid required claims", null);

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        Object tenantClaim = token.getClaims().get("tid");
        if (!isUuid(token.getSubject())
                || !(tenantClaim instanceof String tenantId)
                || !isUuid(tenantId)) {
            return OAuth2TokenValidatorResult.failure(INVALID_CLAIMS);
        }

        Object claim = token.getClaims().get("roles");
        if (!(claim instanceof Collection<?> roles)
                || roles.isEmpty()
                || roles.stream()
                        .anyMatch(
                                role ->
                                        !(role instanceof String value)
                                                || !ALLOWED_ROLES.contains(value))) {
            return OAuth2TokenValidatorResult.failure(INVALID_CLAIMS);
        }

        return OAuth2TokenValidatorResult.success();
    }

    private boolean isUuid(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
