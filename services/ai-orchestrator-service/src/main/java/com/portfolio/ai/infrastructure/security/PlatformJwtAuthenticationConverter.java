package com.portfolio.ai.infrastructure.security;

import com.portfolio.ai.application.AuthenticatedPrincipal;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

public final class PlatformJwtAuthenticationConverter
        implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        var roles = readRoles(jwt);
        var principal =
                new AuthenticatedPrincipal(
                        UUID.fromString(jwt.getSubject()),
                        UUID.fromString(jwt.getClaimAsString("tid")),
                        roles);
        var authorities =
                roles.stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList();
        return new PrincipalAuthenticationToken(principal, authorities);
    }

    private Set<String> readRoles(Jwt jwt) {
        Collection<?> values = (Collection<?>) jwt.getClaims().get("roles");
        return values.stream().map(String.class::cast).collect(Collectors.toUnmodifiableSet());
    }
}
