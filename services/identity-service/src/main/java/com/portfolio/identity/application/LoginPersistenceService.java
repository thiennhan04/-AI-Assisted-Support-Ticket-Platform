package com.portfolio.identity.application;

import com.portfolio.identity.domain.RefreshSession;
import com.portfolio.identity.domain.RefreshSessionRepository;
import com.portfolio.identity.domain.TenantStatus;
import com.portfolio.identity.domain.UserAccount;
import com.portfolio.identity.domain.UserRepository;
import com.portfolio.identity.domain.UserStatus;
import com.portfolio.identity.infrastructure.config.LoginProtectionProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoginPersistenceService {

    private final UserRepository users;
    private final RefreshSessionRepository sessions;
    private final LoginProtectionProperties properties;
    private final Clock clock;

    public LoginPersistenceService(
            UserRepository users,
            RefreshSessionRepository sessions,
            LoginProtectionProperties properties,
            Clock clock) {
        this.users = users;
        this.sessions = sessions;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(UserAccount snapshot) {
        var now = clock.instant();
        users.findByTenantIdAndUserIdForUpdate(snapshot.tenantId(), snapshot.id())
                .filter(user -> user.tenantStatus() == snapshot.tenantStatus())
                .ifPresent(
                        user -> {
                            if (user.tenantStatus() != TenantStatus.ACTIVE
                                    || user.status() == UserStatus.DISABLED
                                    || user.status() == UserStatus.INVITED) {
                                return;
                            }
                            var expiredLock =
                                    user.status() == UserStatus.LOCKED
                                            && user.lockedUntil() != null
                                            && !user.lockedUntil().isAfter(now);
                            var failures = expiredLock ? 1 : user.failedLoginCount() + 1;
                            var lock = failures >= properties.maxFailures();
                            users.recordFailedLogin(
                                    user.tenantId(),
                                    user.id(),
                                    failures,
                                    lock ? UserStatus.LOCKED : UserStatus.ACTIVE,
                                    lock ? now.plus(properties.lockDuration()) : null,
                                    now);
                        });
    }

    @Transactional
    public void persistSuccessfulLogin(
            UserAccount snapshot,
            String refreshTokenHash,
            Instant refreshExpiresAt,
            UUID sessionId,
            UUID familyId) {
        var now = clock.instant();
        var current =
                users.findByTenantIdAndUserIdForUpdate(snapshot.tenantId(), snapshot.id())
                        .filter(user -> user.version() == snapshot.version())
                        .filter(user -> user.canAuthenticateAt(now))
                        .orElseThrow(AuthInvalidCredentialsException::new);

        users.recordSuccessfulLogin(current.tenantId(), current.id(), UserStatus.ACTIVE, now);
        sessions.save(
                new RefreshSession(
                        sessionId,
                        current.tenantId(),
                        current.id(),
                        familyId,
                        refreshTokenHash,
                        refreshExpiresAt,
                        now));
    }
}
