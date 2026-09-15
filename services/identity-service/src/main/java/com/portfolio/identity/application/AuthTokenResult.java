package com.portfolio.identity.application;

import com.portfolio.identity.domain.Role;
import com.portfolio.identity.domain.UserAccount;
import java.util.Set;
import java.util.UUID;

public record AuthTokenResult(
        String accessToken, String refreshToken, long expiresInSeconds, UserSummary user) {

    public static AuthTokenResult from(
            AccessTokenIssuer.IssuedAccessToken accessToken,
            RefreshTokenFactory.GeneratedRefreshToken refreshToken,
            UserAccount user) {
        return new AuthTokenResult(
                accessToken.value(),
                refreshToken.rawValue(),
                accessToken.expiresInSeconds(),
                new UserSummary(
                        user.id(),
                        user.tenantId(),
                        user.email(),
                        user.displayName(),
                        user.roles()));
    }

    public record UserSummary(
            UUID id, UUID tenantId, String email, String displayName, Set<Role> roles) {

        public UserSummary {
            roles = Set.copyOf(roles);
        }
    }
}
