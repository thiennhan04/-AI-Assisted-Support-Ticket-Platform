package com.portfolio.identity.application;

import com.portfolio.identity.domain.UserAccount;
import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final com.portfolio.identity.domain.UserRepository users;
    private final PasswordVerifier passwords;
    private final LoginRateLimiter rateLimiter;
    private final AccessTokenIssuer accessTokens;
    private final RefreshTokenFactory refreshTokens;
    private final LoginPersistenceService persistence;
    private final com.portfolio.identity.infrastructure.config.JwtProperties jwtProperties;
    private final com.portfolio.identity.infrastructure.config.LoginProtectionProperties
            loginProperties;
    private final Clock clock;

    public AuthService(
            com.portfolio.identity.domain.UserRepository users,
            PasswordVerifier passwords,
            LoginRateLimiter rateLimiter,
            AccessTokenIssuer accessTokens,
            RefreshTokenFactory refreshTokens,
            LoginPersistenceService persistence,
            com.portfolio.identity.infrastructure.config.JwtProperties jwtProperties,
            com.portfolio.identity.infrastructure.config.LoginProtectionProperties loginProperties,
            Clock clock) {
        this.users = users;
        this.passwords = passwords;
        this.rateLimiter = rateLimiter;
        this.accessTokens = accessTokens;
        this.refreshTokens = refreshTokens;
        this.persistence = persistence;
        this.jwtProperties = jwtProperties;
        this.loginProperties = loginProperties;
        this.clock = clock;
    }

    public LoginResult login(LoginCommand command) {
        var tenantCode = command.tenantCode().strip().toLowerCase(Locale.ROOT);
        var email = command.email().strip().toLowerCase(Locale.ROOT);
        rateLimiter
                .consume(tenantCode, email, command.clientAddress())
                .ifPresent(
                        retryAfter -> {
                            throw new LoginRateLimitExceededException(retryAfter);
                        });

        var candidate = users.findByTenantCodeAndEmail(tenantCode, email);
        var passwordMatches = verifyPassword(command.password(), candidate.orElse(null));
        var now = clock.instant();
        if (candidate.isEmpty()
                || !passwordMatches
                || !candidate.orElseThrow().canAuthenticateAt(now)) {
            if (candidate.isPresent() && !passwordMatches) {
                persistence.recordFailure(candidate.orElseThrow());
            }
            delayFailedLogin();
            throw new AuthInvalidCredentialsException();
        }

        var user = candidate.orElseThrow();
        var refreshToken = refreshTokens.generate();
        var accessToken = accessTokens.issue(user, now);
        persistence.persistSuccessfulLogin(
                user,
                refreshToken.sha256Hash(),
                now.plus(jwtProperties.refreshTtl()),
                UUID.randomUUID(),
                UUID.randomUUID());

        return new LoginResult(
                accessToken.value(),
                refreshToken.rawValue(),
                accessToken.expiresInSeconds(),
                new LoginResult.UserSummary(
                        user.id(),
                        user.tenantId(),
                        user.email(),
                        user.displayName(),
                        user.roles()));
    }

    private boolean verifyPassword(String rawPassword, UserAccount candidate) {
        if (candidate == null) {
            passwords.performDummyCheck(rawPassword);
            return false;
        }
        return passwords.matches(rawPassword, candidate.passwordHash());
    }

    private void delayFailedLogin() {
        var minimum = loginProperties.failureDelayMin().toMillis();
        var maximum = loginProperties.failureDelayMax().toMillis();
        var delay =
                minimum == maximum
                        ? minimum
                        : ThreadLocalRandom.current().nextLong(minimum, maximum + 1);
        try {
            Thread.sleep(Duration.ofMillis(delay));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
