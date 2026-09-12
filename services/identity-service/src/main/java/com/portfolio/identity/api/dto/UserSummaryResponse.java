package com.portfolio.identity.api.dto;

import com.portfolio.identity.domain.Role;
import java.util.Set;
import java.util.UUID;

public record UserSummaryResponse(
        UUID id, UUID tenantId, String email, String displayName, Set<Role> roles) {

    public UserSummaryResponse {
        roles = Set.copyOf(roles);
    }
}
