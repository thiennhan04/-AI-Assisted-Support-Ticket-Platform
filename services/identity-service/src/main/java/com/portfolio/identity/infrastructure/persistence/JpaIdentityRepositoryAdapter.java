package com.portfolio.identity.infrastructure.persistence;

import com.portfolio.identity.domain.RefreshSession;
import com.portfolio.identity.domain.RefreshSessionRepository;
import com.portfolio.identity.domain.Role;
import com.portfolio.identity.domain.UserAccount;
import com.portfolio.identity.domain.UserRepository;
import com.portfolio.identity.domain.UserStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

@Repository
public class JpaIdentityRepositoryAdapter implements UserRepository, RefreshSessionRepository {

    private final TenantSpringDataRepository tenants;
    private final UserSpringDataRepository users;
    private final UserRoleSpringDataRepository roles;
    private final RefreshSessionSpringDataRepository sessions;

    public JpaIdentityRepositoryAdapter(
            TenantSpringDataRepository tenants,
            UserSpringDataRepository users,
            UserRoleSpringDataRepository roles,
            RefreshSessionSpringDataRepository sessions) {
        this.tenants = tenants;
        this.users = users;
        this.roles = roles;
        this.sessions = sessions;
    }

    @Override
    public Optional<UserAccount> findByTenantCodeAndEmail(String tenantCode, String email) {
        return tenants.findByCode(tenantCode)
                .flatMap(
                        tenant ->
                                users.findByTenantIdAndEmail(tenant.getId(), email)
                                        .map(user -> toDomain(tenant, user)));
    }

    @Override
    public Optional<UserAccount> findByTenantIdAndUserIdForUpdate(UUID tenantId, UUID userId) {
        return tenants.findById(tenantId)
                .flatMap(
                        tenant ->
                                users.findByTenantIdAndId(tenantId, userId)
                                        .map(user -> toDomain(tenant, user)));
    }

    @Override
    public void recordFailedLogin(
            UUID tenantId,
            UUID userId,
            int failedLoginCount,
            UserStatus status,
            Instant lockedUntil,
            Instant updatedAt) {
        var user = users.findByTenantIdAndId(tenantId, userId).orElseThrow();
        user.recordFailedLogin(failedLoginCount, status, lockedUntil, updatedAt);
    }

    @Override
    public void recordSuccessfulLogin(
            UUID tenantId, UUID userId, UserStatus status, Instant updatedAt) {
        var user = users.findByTenantIdAndId(tenantId, userId).orElseThrow();
        user.recordSuccessfulLogin(status, updatedAt);
    }

    @Override
    public void save(RefreshSession session) {
        sessions.save(new RefreshSessionJpaEntity(session));
    }

    private UserAccount toDomain(TenantJpaEntity tenant, UserJpaEntity user) {
        var assignedRoles =
                roles.findAllByTenantIdAndUserId(tenant.getId(), user.getId()).stream()
                        .map(UserRoleJpaEntity::getRole)
                        .collect(Collectors.toUnmodifiableSet());
        return new UserAccount(
                user.getId(),
                tenant.getId(),
                tenant.getCode(),
                tenant.getStatus(),
                user.getEmail(),
                user.getPasswordHash(),
                user.getDisplayName(),
                user.getStatus(),
                user.getFailedLoginCount(),
                user.getLockedUntil(),
                user.getVersion(),
                assignedRoles.isEmpty() ? java.util.Set.of(Role.CUSTOMER) : assignedRoles);
    }
}
