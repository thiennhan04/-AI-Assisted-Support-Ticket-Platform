package com.portfolio.identity.infrastructure.config;

import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("local")
@ConditionalOnProperty(prefix = "identity.local-seed", name = "enabled", havingValue = "true")
public class LocalIdentitySeeder implements ApplicationRunner {

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-4000-8000-000000000001");

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final LocalSeedProperties properties;

    public LocalIdentitySeeder(
            JdbcTemplate jdbc, PasswordEncoder passwordEncoder, LocalSeedProperties properties) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        if (properties.password() == null || properties.password().length() < 8) {
            throw new IllegalStateException(
                    "LOCAL_SEED_PASSWORD must contain at least 8 characters");
        }
        jdbc.update(
                """
                INSERT INTO identity.tenant(id, code, name, status)
                VALUES (?, 'acme', 'Acme Demo', 'ACTIVE')
                ON CONFLICT (id) DO NOTHING
                """,
                TENANT_ID);

        var passwordHash = passwordEncoder.encode(properties.password());
        seedUser(
                "00000000-0000-4000-8000-000000000101",
                "admin@acme.local",
                "Admin Demo",
                "ADMIN",
                passwordHash);
        seedUser(
                "00000000-0000-4000-8000-000000000102",
                "agent@acme.local",
                "Agent Demo",
                "AGENT",
                passwordHash);
        seedUser(
                "00000000-0000-4000-8000-000000000103",
                "customer@acme.local",
                "Customer Demo",
                "CUSTOMER",
                passwordHash);
    }

    private void seedUser(
            String id, String email, String displayName, String role, String passwordHash) {
        var userId = UUID.fromString(id);
        jdbc.update(
                """
                INSERT INTO identity.app_user(
                  id, tenant_id, email, password_hash, display_name, status)
                VALUES (?, ?, ?, ?, ?, 'ACTIVE')
                ON CONFLICT (tenant_id, email) DO NOTHING
                """,
                userId,
                TENANT_ID,
                email,
                passwordHash,
                displayName);
        jdbc.update(
                """
                INSERT INTO identity.user_role(tenant_id, user_id, role)
                VALUES (?, ?, ?)
                ON CONFLICT DO NOTHING
                """,
                TENANT_ID,
                userId,
                role);
    }
}
