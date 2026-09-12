package com.portfolio.identity.infrastructure.persistence;

import com.portfolio.identity.domain.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@IdClass(UserRoleKey.class)
@Table(name = "user_role", schema = "identity")
class UserRoleJpaEntity {

    @Id
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    protected UserRoleJpaEntity() {}

    Role getRole() {
        return role;
    }
}
