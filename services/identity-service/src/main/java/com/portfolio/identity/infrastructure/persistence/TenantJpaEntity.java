package com.portfolio.identity.infrastructure.persistence;

import com.portfolio.identity.domain.TenantStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "tenant", schema = "identity")
class TenantJpaEntity {

    @Id private UUID id;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TenantStatus status;

    protected TenantJpaEntity() {}

    UUID getId() {
        return id;
    }

    String getCode() {
        return code;
    }

    TenantStatus getStatus() {
        return status;
    }
}
