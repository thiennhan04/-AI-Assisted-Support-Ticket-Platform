package com.portfolio.knowledge.infrastructure.security;

import com.portfolio.knowledge.application.AuthenticatedPrincipal;
import java.io.Serial;
import java.util.Collection;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

final class PrincipalAuthenticationToken extends AbstractAuthenticationToken {

    @Serial private static final long serialVersionUID = 1L;

    private final AuthenticatedPrincipal principal;

    PrincipalAuthenticationToken(
            AuthenticatedPrincipal principal, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.principal = principal;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public AuthenticatedPrincipal getPrincipal() {
        return principal;
    }

    @Override
    public String getName() {
        return principal.userId().toString();
    }
}
