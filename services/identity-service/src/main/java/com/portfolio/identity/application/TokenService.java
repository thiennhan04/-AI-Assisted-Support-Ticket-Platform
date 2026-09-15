package com.portfolio.identity.application;

import com.portfolio.identity.domain.RefreshSession;
import com.portfolio.identity.domain.RefreshSessionRepository;
import com.portfolio.identity.domain.UserRepository;
import com.portfolio.identity.infrastructure.config.JwtProperties;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TokenService {

    private final RefreshSessionRepository sessions;
    private final UserRepository users;
    private final RefreshTokenFactory refreshTokens;
    private final AccessTokenIssuer accessTokens;
    private final JwtProperties jwtProperties;
    private final Clock clock;

    public TokenService(
            RefreshSessionRepository sessions,
            UserRepository users,
            RefreshTokenFactory refreshTokens,
            AccessTokenIssuer accessTokens,
            JwtProperties jwtProperties,
            Clock clock) {
        this.sessions = sessions;
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.accessTokens = accessTokens;
        this.jwtProperties = jwtProperties;
        this.clock = clock;
    }

    @Transactional(noRollbackFor = RefreshTokenReuseDetectedException.class)
    public AuthTokenResult refresh(String rawToken) {
        var now = clock.instant();
        var current =
                sessions.findByTokenHashForUpdate(refreshTokens.hash(rawToken))
                        .orElseThrow(InvalidRefreshTokenException::new);

        if (current.wasRotated()) {
            sessions.revokeFamily(current.tenantId(), current.familyId(), now);
            throw new RefreshTokenReuseDetectedException();
        }
        if (current.isRevoked() || current.isExpiredAt(now)) {
            throw new InvalidRefreshTokenException();
        }

        var user =
                users.findByTenantIdAndUserIdForUpdate(current.tenantId(), current.userId())
                        .filter(candidate -> candidate.canAuthenticateAt(now))
                        .orElseThrow(InvalidRefreshTokenException::new);
        var nextRefreshToken = refreshTokens.generate();
        var nextSessionId = UUID.randomUUID();
        var nextSession =
                new RefreshSession(
                        nextSessionId,
                        current.tenantId(),
                        current.userId(),
                        current.familyId(),
                        nextRefreshToken.sha256Hash(),
                        null,
                        now.plus(jwtProperties.refreshTtl()),
                        null,
                        now,
                        null);
        var nextAccessToken = accessTokens.issue(user, now);

        sessions.save(nextSession);
        sessions.markReplaced(current.id(), nextSessionId, now);
        return AuthTokenResult.from(nextAccessToken, nextRefreshToken, user);
    }

    @Transactional
    public void logout(String rawToken) {
        sessions.findByTokenHashForUpdate(refreshTokens.hash(rawToken))
                .ifPresent(
                        session ->
                                sessions.revokeFamily(
                                        session.tenantId(), session.familyId(), clock.instant()));
    }
}
