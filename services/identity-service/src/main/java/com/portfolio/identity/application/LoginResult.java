package com.portfolio.identity.application;

import com.portfolio.identity.domain.Role;
import java.util.Set;
import java.util.UUID;

public record LoginResult(
        String accessToken, String refreshToken, long expiresInSeconds, UserSummary user) {

    public record UserSummary(
            UUID id, UUID tenantId, String email, String displayName, Set<Role> roles) {

        public UserSummary {
            roles = Set.copyOf(roles);
        }
    }
}
