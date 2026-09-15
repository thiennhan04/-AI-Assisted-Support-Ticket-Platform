package com.portfolio.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class IdentityLoginIT {

    private static final UUID ACME_ID = UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final UUID GLOBEX_ID = UUID.fromString("20000000-0000-4000-8000-000000000001");
    private static final UUID ACME_USER_ID =
            UUID.fromString("10000000-0000-4000-8000-000000000101");
    private static final String PASSWORD = "CorrectPassword123!";
    private static final KeyFiles KEY_FILES = createKeyFiles();

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(
                            DockerImageName.parse("pgvector/pgvector:0.8.1-pg16")
                                    .asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("identity_test")
                    .withUsername("identity_test")
                    .withPassword("identity_test");

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:8.2.1-alpine"))
                    .withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("identity.jwt.private-key", () -> KEY_FILES.privateKey().toUri().toString());
        registry.add("identity.jwt.public-key", () -> KEY_FILES.publicKey().toUri().toString());
        registry.add("identity.jwt.key-id", () -> "integration-key");
        registry.add("identity.jwt.issuer", () -> "http://identity.test");
        registry.add("identity.jwt.audience", () -> "ticket-platform");
        registry.add("identity.jwt.access-ttl", () -> "PT15M");
        registry.add("identity.jwt.refresh-ttl", () -> "P30D");
        registry.add("identity.login-protection.max-failures", () -> "5");
        registry.add("identity.login-protection.lock-duration", () -> "PT15M");
        registry.add("identity.login-protection.rate-limit", () -> "10");
        registry.add("identity.login-protection.rate-window", () -> "PT15M");
        registry.add("identity.login-protection.failure-delay-min", () -> "PT0S");
        registry.add("identity.login-protection.failure-delay-max", () -> "PT0S");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("debug", () -> "false");
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtDecoder jwtDecoder;
    @Autowired JwtEncoder jwtEncoder;
    @Autowired RedisConnectionFactory redisConnectionFactory;

    @BeforeEach
    void seed() {
        try (var connection = redisConnectionFactory.getConnection()) {
            connection.serverCommands().flushAll();
        }
        jdbc.update("DELETE FROM identity.refresh_session");
        jdbc.update("DELETE FROM identity.user_role");
        jdbc.update("DELETE FROM identity.app_user");
        jdbc.update("DELETE FROM identity.tenant");
        insertTenant(ACME_ID, "acme", "ACTIVE");
        insertTenant(GLOBEX_ID, "globex", "ACTIVE");
        insertUser(ACME_USER_ID, ACME_ID, "agent@example.com", "ACTIVE");
        jdbc.update(
                "INSERT INTO identity.user_role(tenant_id, user_id, role) VALUES (?, ?, 'AGENT')",
                ACME_ID,
                ACME_USER_ID);
    }

    @Test
    void loginReturnsVerifiableJwtUserSummaryAndHashedRefreshSession() throws Exception {
        var response =
                mvc.perform(login("acme", "AGENT@example.com", PASSWORD))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.tokenType").value("Bearer"))
                        .andExpect(jsonPath("$.expiresIn").value(900))
                        .andExpect(jsonPath("$.user.tenantId").value(ACME_ID.toString()))
                        .andExpect(jsonPath("$.user.roles[0]").value("AGENT"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        JsonNode json = objectMapper.readTree(response);
        var accessToken = json.get("accessToken").asText();
        var refreshToken = json.get("refreshToken").asText();
        var jwt = jwtDecoder.decode(accessToken);

        assertThat(jwt.getSubject()).isEqualTo(ACME_USER_ID.toString());
        assertThat(jwt.getClaimAsString("tid")).isEqualTo(ACME_ID.toString());
        assertThat(jwt.getAudience()).containsExactly("ticket-platform");
        assertThat(jwt.getHeaders()).containsEntry("kid", "integration-key");
        assertThat(jwt.getId()).isNotBlank();
        assertThat(jwt.getIssuedAt()).isNotNull();
        assertThat(jwt.getExpiresAt()).isAfter(jwt.getIssuedAt());

        var storedHash =
                jdbc.queryForObject(
                        "SELECT token_hash FROM identity.refresh_session WHERE tenant_id = ?",
                        String.class,
                        ACME_ID);
        var expectedHash =
                HexFormat.of()
                        .formatHex(
                                java.security.MessageDigest.getInstance("SHA-256")
                                        .digest(refreshToken.getBytes(StandardCharsets.UTF_8)));
        assertThat(storedHash).isEqualTo(expectedHash).doesNotContain(refreshToken);
    }

    @Test
    void refreshRotatesTokenAndLinksSessionsInTheSameFamily() throws Exception {
        var initial = loginTokens();
        var oldRefreshToken = initial.get("refreshToken").asText();
        var oldSession =
                jdbc.queryForMap(
                        "SELECT id, family_id FROM identity.refresh_session WHERE token_hash = ?",
                        sha256(oldRefreshToken));

        var response =
                mvc.perform(refresh(oldRefreshToken))
                        .andExpect(status().isOk())
                        .andExpect(header().string("Cache-Control", "no-store"))
                        .andExpect(header().string("Pragma", "no-cache"))
                        .andExpect(jsonPath("$.tokenType").value("Bearer"))
                        .andExpect(jsonPath("$.expiresIn").value(900))
                        .andExpect(jsonPath("$.user.id").value(ACME_USER_ID.toString()))
                        .andReturn();
        var rotated = objectMapper.readTree(response.getResponse().getContentAsString());
        var newRefreshToken = rotated.get("refreshToken").asText();
        var jwt = jwtDecoder.decode(rotated.get("accessToken").asText());

        assertThat(newRefreshToken).isNotEqualTo(oldRefreshToken);
        assertThat(jwt.getSubject()).isEqualTo(ACME_USER_ID.toString());
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM identity.refresh_session WHERE family_id = ?",
                                Integer.class,
                                oldSession.get("family_id")))
                .isEqualTo(2);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT replaced_by_id FROM identity.refresh_session WHERE id = ?",
                                UUID.class,
                                oldSession.get("id")))
                .isNotNull();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM identity.refresh_session WHERE token_hash = ?",
                                Integer.class,
                                sha256(newRefreshToken)))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT last_used_at IS NOT NULL FROM identity.refresh_session WHERE id = ?",
                                Boolean.class,
                                oldSession.get("id")))
                .isTrue();
    }

    @Test
    void reuseOfRotatedTokenRevokesTheEntireFamily() throws Exception {
        var oldRefreshToken = loginTokens().get("refreshToken").asText();
        var rotated =
                objectMapper.readTree(
                        mvc.perform(refresh(oldRefreshToken))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString());

        mvc.perform(refresh(oldRefreshToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REFRESH_REUSE_DETECTED"));

        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM identity.refresh_session WHERE revoked_at IS NULL",
                                Integer.class))
                .isZero();
        mvc.perform(refresh(rotated.get("refreshToken").asText()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_REFRESH_TOKEN"));
    }

    @Test
    void concurrentRefreshAllowsExactlyOneSuccessAndRevokesFamilyOnReuse() throws Exception {
        var oldRefreshToken = loginTokens().get("refreshToken").asText();
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> concurrentRefresh(oldRefreshToken, ready, start));
            var second = executor.submit(() -> concurrentRefresh(oldRefreshToken, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            var results =
                    List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
            assertThat(results.stream().map(result -> result.getResponse().getStatus()).toList())
                    .containsExactlyInAnyOrder(200, 401);
            var rejected =
                    results.stream()
                            .filter(result -> result.getResponse().getStatus() == 401)
                            .findFirst()
                            .orElseThrow();
            assertThat(
                            objectMapper
                                    .readTree(rejected.getResponse().getContentAsString())
                                    .get("code")
                                    .asText())
                    .isEqualTo("AUTH_REFRESH_REUSE_DETECTED");
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT count(*) FROM identity.refresh_session WHERE revoked_at IS NULL",
                                    Integer.class))
                    .isZero();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void expiredSuspendedAndDisabledSessionsCannotRefresh() throws Exception {
        var expiredToken = loginTokens().get("refreshToken").asText();
        jdbc.update("UPDATE identity.refresh_session SET expires_at = now() - interval '1 second'");
        assertInvalidRefresh(expiredToken);

        jdbc.update("DELETE FROM identity.refresh_session");
        var disabledToken = loginTokens().get("refreshToken").asText();
        jdbc.update("UPDATE identity.app_user SET status = 'DISABLED' WHERE id = ?", ACME_USER_ID);
        assertInvalidRefresh(disabledToken);

        jdbc.update("UPDATE identity.app_user SET status = 'ACTIVE' WHERE id = ?", ACME_USER_ID);
        jdbc.update("DELETE FROM identity.refresh_session");
        var suspendedToken = loginTokens().get("refreshToken").asText();
        jdbc.update("UPDATE identity.tenant SET status = 'SUSPENDED' WHERE id = ?", ACME_ID);
        assertInvalidRefresh(suspendedToken);
    }

    @Test
    void logoutRevokesFamilyAndIsIdempotentWithoutTokenDisclosure() throws Exception {
        var initialToken = loginTokens().get("refreshToken").asText();
        var rotated =
                objectMapper.readTree(
                        mvc.perform(refresh(initialToken))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        var currentToken = rotated.get("refreshToken").asText();

        mvc.perform(logout(currentToken)).andExpect(status().isNoContent());
        mvc.perform(logout(currentToken)).andExpect(status().isNoContent());
        mvc.perform(logout("unknown-refresh-token-value-with-valid-length"))
                .andExpect(status().isNoContent());

        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM identity.refresh_session WHERE revoked_at IS NULL",
                                Integer.class))
                .isZero();
        assertInvalidRefresh(currentToken);
    }

    @Test
    void refreshAndLogoutRejectMalformedOrUnknownFields() throws Exception {
        mvc.perform(refresh("too-short"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(
                        post("/v1/auth/refresh")
                                .contentType("application/json")
                                .content(
                                        """
                                        {"refreshToken":"unknown-refresh-token-value-with-valid-length",
                                         "userId":"10000000-0000-4000-8000-000000000101"}
                                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(logout("too-short"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void unknownUserAndWrongPasswordReturnSameSafeProblem() throws Exception {
        var unknown = mvc.perform(login("acme", "unknown@example.com", PASSWORD)).andReturn();
        var unknownTenant =
                mvc.perform(login("unknown", "agent@example.com", PASSWORD)).andReturn();
        var wrong =
                mvc.perform(login("acme", "agent@example.com", "WrongPassword123!")).andReturn();

        assertThat(unknown.getResponse().getStatus()).isEqualTo(401);
        assertThat(unknownTenant.getResponse().getStatus()).isEqualTo(401);
        assertThat(wrong.getResponse().getStatus()).isEqualTo(401);
        assertThat(
                        objectMapper
                                .readTree(unknown.getResponse().getContentAsString())
                                .get("code")
                                .asText())
                .isEqualTo("AUTH_INVALID_CREDENTIALS");
        assertThat(
                        objectMapper
                                .readTree(unknownTenant.getResponse().getContentAsString())
                                .get("code")
                                .asText())
                .isEqualTo("AUTH_INVALID_CREDENTIALS");
        assertThat(
                        objectMapper
                                .readTree(wrong.getResponse().getContentAsString())
                                .get("code")
                                .asText())
                .isEqualTo("AUTH_INVALID_CREDENTIALS");
    }

    @Test
    void suspendedTenantAndDisabledUserReturnGeneric401() throws Exception {
        jdbc.update("UPDATE identity.tenant SET status = 'SUSPENDED' WHERE id = ?", ACME_ID);
        mvc.perform(login("acme", "agent@example.com", PASSWORD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"));

        jdbc.update("UPDATE identity.tenant SET status = 'ACTIVE' WHERE id = ?", ACME_ID);
        jdbc.update("UPDATE identity.app_user SET status = 'DISABLED' WHERE id = ?", ACME_USER_ID);
        mvc.perform(login("acme", "agent@example.com", PASSWORD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"));
    }

    @Test
    void decoderRejectsWrongIssuerAudienceAndSignature() throws Exception {
        var now = Instant.now();
        var wrongIssuer = signedToken("http://wrong-issuer.test", "ticket-platform", now);
        var wrongAudience = signedToken("http://identity.test", "wrong-audience", now);
        var valid = signedToken("http://identity.test", "ticket-platform", now);

        assertThatThrownBy(() -> jwtDecoder.decode(wrongIssuer)).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> jwtDecoder.decode(wrongAudience)).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> jwtDecoder.decode(tamperSignature(valid)))
                .isInstanceOf(Exception.class);
    }

    @Test
    void tenantContextSelectsTheCorrectSameEmailAccount() throws Exception {
        var globexUser = UUID.fromString("20000000-0000-4000-8000-000000000101");
        insertUser(globexUser, GLOBEX_ID, "agent@example.com", "ACTIVE");
        jdbc.update(
                "INSERT INTO identity.user_role(tenant_id, user_id, role) VALUES (?, ?, 'ADMIN')",
                GLOBEX_ID,
                globexUser);

        var body =
                mvc.perform(login("globex", "agent@example.com", PASSWORD))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        var jwt = jwtDecoder.decode(objectMapper.readTree(body).get("accessToken").asText());
        assertThat(jwt.getClaimAsString("tid")).isEqualTo(GLOBEX_ID.toString());
        assertThat(jwt.getSubject()).isEqualTo(globexUser.toString());
    }

    @Test
    void fiveFailuresLockAccountAndValidPasswordStillReturnsGeneric401() throws Exception {
        for (int attempt = 0; attempt < 5; attempt++) {
            mvc.perform(login("acme", "agent@example.com", "WrongPassword123!"))
                    .andExpect(status().isUnauthorized());
        }

        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM identity.app_user WHERE id = ?",
                                String.class,
                                ACME_USER_ID))
                .isEqualTo("LOCKED");
        mvc.perform(login("acme", "agent@example.com", PASSWORD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"));
    }

    @Test
    void rateLimitReturns429WithRetryAfter() throws Exception {
        for (int attempt = 0; attempt < 10; attempt++) {
            mvc.perform(login("acme", "unknown@example.com", PASSWORD))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(login("acme", "unknown@example.com", PASSWORD))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("AUTH_RATE_LIMITED"));
    }

    @Test
    void jwksPublishesPublicKeyAndCachesIt() throws Exception {
        mvc.perform(get("/.well-known/jwks.json"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "max-age=300, public"))
                .andExpect(jsonPath("$.keys[0].kid").value("integration-key"))
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].d").doesNotExist());
    }

    @Test
    void rejectsUnknownSecuritySensitiveFields() throws Exception {
        mvc.perform(
                        post("/v1/auth/login")
                                .contentType("application/json")
                                .content(
                                        """
                                        {"tenantCode":"acme","email":"agent@example.com",
                                         "password":"CorrectPassword123!","roles":["ADMIN"]}
                                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder login(
            String tenantCode, String email, String password) throws Exception {
        return post("/v1/auth/login")
                .contentType("application/json")
                .content(
                        objectMapper.writeValueAsString(
                                java.util.Map.of(
                                        "tenantCode", tenantCode,
                                        "email", email,
                                        "password", password)));
    }

    private JsonNode loginTokens() throws Exception {
        return objectMapper.readTree(
                mvc.perform(login("acme", "agent@example.com", PASSWORD))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder refresh(
            String refreshToken) throws Exception {
        return tokenRequest("/v1/auth/refresh", refreshToken);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder logout(
            String refreshToken) throws Exception {
        return tokenRequest("/v1/auth/logout", refreshToken);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder tokenRequest(
            String path, String refreshToken) throws Exception {
        return post(path)
                .contentType("application/json")
                .content(
                        objectMapper.writeValueAsString(
                                java.util.Map.of("refreshToken", refreshToken)));
    }

    private MvcResult concurrentRefresh(String token, CountDownLatch ready, CountDownLatch start)
            throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent refresh start timed out");
        }
        return mvc.perform(refresh(token)).andReturn();
    }

    private void assertInvalidRefresh(String token) throws Exception {
        mvc.perform(refresh(token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_REFRESH_TOKEN"));
    }

    private String sha256(String value) throws Exception {
        return HexFormat.of()
                .formatHex(
                        java.security.MessageDigest.getInstance("SHA-256")
                                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private void insertTenant(UUID id, String code, String status) {
        jdbc.update(
                "INSERT INTO identity.tenant(id, code, name, status) VALUES (?, ?, ?, ?)",
                id,
                code,
                code,
                status);
    }

    private void insertUser(UUID id, UUID tenantId, String email, String status) {
        jdbc.update(
                """
                INSERT INTO identity.app_user(
                  id, tenant_id, email, password_hash, display_name, status)
                VALUES (?, ?, ?, ?, 'Test User', ?)
                """,
                id,
                tenantId,
                email,
                passwordEncoder.encode(PASSWORD),
                status);
    }

    private String signedToken(String issuer, String audience, Instant now) {
        var header = JwsHeader.with(SignatureAlgorithm.RS256).keyId("integration-key").build();
        var claims =
                JwtClaimsSet.builder()
                        .issuer(issuer)
                        .audience(java.util.List.of(audience))
                        .subject(ACME_USER_ID.toString())
                        .issuedAt(now)
                        .expiresAt(now.plusSeconds(900))
                        .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private String tamperSignature(String token) {
        var parts = token.split("\\.");
        var first = parts[2].charAt(0) == 'A' ? 'B' : 'A';
        parts[2] = first + parts[2].substring(1);
        return String.join(".", parts);
    }

    private static KeyFiles createKeyFiles() {
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            var pair = generator.generateKeyPair();
            var directory = Files.createTempDirectory("identity-it-keys");
            var privatePath = directory.resolve("private.pem");
            var publicPath = directory.resolve("public.pem");
            writePem(privatePath, "PRIVATE KEY", ((RSAPrivateKey) pair.getPrivate()).getEncoded());
            writePem(publicPath, "PUBLIC KEY", ((RSAPublicKey) pair.getPublic()).getEncoded());
            return new KeyFiles(privatePath, publicPath);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot create integration-test RSA keys", exception);
        }
    }

    private static void writePem(Path path, String type, byte[] encoded) throws Exception {
        var base64 = Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(encoded);
        Files.writeString(
                path,
                "-----BEGIN " + type + "-----\n" + base64 + "\n-----END " + type + "-----\n",
                StandardCharsets.US_ASCII);
    }

    private record KeyFiles(Path privateKey, Path publicKey) {}
}
