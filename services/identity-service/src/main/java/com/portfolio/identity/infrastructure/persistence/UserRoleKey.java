package com.portfolio.identity.infrastructure.persistence;

import com.portfolio.identity.domain.Role;
import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class UserRoleKey implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    private UUID tenantId;
    private UUID userId;
    private Role role;

    public UserRoleKey() {}

    @Override
    public boolean equals(Object candidate) {
        if (this == candidate) {
            return true;
        }
        if (!(candidate instanceof UserRoleKey other)) {
            return false;
        }
        return Objects.equals(tenantId, other.tenantId)
                && Objects.equals(userId, other.userId)
                && role == other.role;
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, userId, role);
    }
}
