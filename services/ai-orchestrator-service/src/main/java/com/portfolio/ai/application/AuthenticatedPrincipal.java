package com.portfolio.ai.application;

import java.io.Serializable;
import java.util.Set;
import java.util.UUID;

public record AuthenticatedPrincipal(UUID userId, UUID tenantId, Set<String> roles)
        implements Serializable {

    public AuthenticatedPrincipal {
        roles = Set.copyOf(roles);
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }
}
