package com.portfolio.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.knowledge.application.DocumentChunker;
import com.portfolio.knowledge.application.IngestionService;
import com.portfolio.knowledge.domain.Chunk;
import com.portfolio.knowledge.domain.DocumentException;
import com.portfolio.knowledge.domain.DocumentRepository;
import com.portfolio.knowledge.infrastructure.security.PlatformJwtAuthenticationConverter;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
class DocumentUploadIT {
    private static final String BASE = "/v1/knowledge/documents";
    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID OTHER_TENANT = UUID.randomUUID();
    private static final byte[] TEXT = "Account reset guide\n".getBytes(StandardCharsets.UTF_8);

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(
                    DockerImageName.parse("pgvector/pgvector:0.8.1-pg16")
                            .asCompatibleSubstituteFor("postgres"));

    @Container
    static final GenericContainer<?> MINIO =
            new GenericContainer<>("minio/minio:RELEASE.2025-09-07T16-13-09Z")
                    .withEnv("MINIO_ROOT_USER", "test-minio")
                    .withEnv("MINIO_ROOT_PASSWORD", "test-minio-secret")
                    .withCommand("server /data")
                    .withExposedPorts(9000)
                    .waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add(
                "knowledge.storage.endpoint",
                () -> "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000));
        registry.add("knowledge.storage.access-key", () -> "test-minio");
        registry.add("knowledge.storage.secret-key", () -> "test-minio-secret");
        registry.add("knowledge.storage.bucket", () -> "knowledge-test");
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired MinioClient minio;
    @Autowired IngestionService ingestion;
    @MockitoSpyBean DocumentRepository documents;
    @MockitoBean JwtDecoder decoder;
    @LocalServerPort int port;

    @BeforeEach
    void prepareStorageAndDatabase() throws Exception {
        jdbc.execute(
                "TRUNCATE knowledge.outbox_event, knowledge.knowledge_document, knowledge.document_version CASCADE");
        if (!minio.bucketExists(BucketExistsArgs.builder().bucket("knowledge-test").build())) {
            minio.makeBucket(MakeBucketArgs.builder().bucket("knowledge-test").build());
        }
        when(decoder.decode("test-token")).thenReturn(jwt(TENANT, "ADMIN"));
    }

    @Test
    void ingestionPersistsChunksOnceAndWaitsForEmbeddingBeforeActivation() throws Exception {
        var response =
                upload(
                                TENANT,
                                "ADMIN",
                                Map.of("title", "Guide"),
                                "# Account\n\nReset your password."
                                        .getBytes(StandardCharsets.UTF_8),
                                "text/plain")
                        .andReturn();
        var body = mapper.readTree(response.getResponse().getContentAsString());
        var id = UUID.fromString(body.get("documentId").asText());
        var version = UUID.fromString(body.get("versionId").asText());
        ingestion.prepare(TENANT, id, version);
        var original =
                jdbc.queryForMap(
                        "SELECT * FROM knowledge.document_chunk WHERE document_version_id = ?",
                        version);
        ingestion.prepare(TENANT, id, version);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM knowledge.document_chunk WHERE document_version_id = ?",
                                Integer.class,
                                version))
                .isEqualTo(1);
        assertThat(original.get("tenant_id")).isEqualTo(TENANT);
        assertThat(original.get("heading")).isEqualTo("Account");
        assertThat(original.get("page_number")).isNull();
        assertThat(original.get("content")).isEqualTo("# Account\n\nReset your password.");
        assertThat(
                        jdbc.queryForMap(
                                "SELECT * FROM knowledge.document_chunk WHERE document_version_id = ?",
                                version))
                .isEqualTo(original);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT chunking_version FROM knowledge.document_version WHERE id = ?",
                                String.class,
                                version))
                .isEqualTo(DocumentChunker.CHUNKING_VERSION);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM knowledge.document_version WHERE id = ?",
                                String.class,
                                version))
                .isEqualTo("PROCESSING");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT active_version_id FROM knowledge.knowledge_document WHERE id = ?",
                                UUID.class,
                                id))
                .isNull();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM knowledge.outbox_event", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void ingestionDoesNotReadOtherTenantsOrStaleVersions() throws Exception {
        var response =
                upload(TENANT, "ADMIN", Map.of("title", "Guide"), TEXT, "text/plain").andReturn();
        var body = mapper.readTree(response.getResponse().getContentAsString());
        var id = UUID.fromString(body.get("documentId").asText());
        var version = UUID.fromString(body.get("versionId").asText());
        assertThatThrownBy(() -> ingestion.prepare(OTHER_TENANT, id, version))
                .isInstanceOf(DocumentException.class);
        assertThatThrownBy(() -> ingestion.prepare(TENANT, id, UUID.randomUUID()))
                .isInstanceOf(DocumentException.class);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM knowledge.document_chunk", Integer.class))
                .isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM knowledge.document_version WHERE id = ?",
                                String.class,
                                version))
                .isEqualTo("QUEUED");
    }

    @Test
    void emptyExtractionMarksFailedWithoutPersistingChunks() throws Exception {
        var response =
                upload(
                                TENANT,
                                "ADMIN",
                                Map.of("title", "Blank"),
                                " \n\t".getBytes(StandardCharsets.UTF_8),
                                "text/plain")
                        .andReturn();
        var body = mapper.readTree(response.getResponse().getContentAsString());
        var id = UUID.fromString(body.get("documentId").asText());
        var version = UUID.fromString(body.get("versionId").asText());
        assertThatThrownBy(() -> ingestion.prepare(TENANT, id, version))
                .isInstanceOf(DocumentException.class);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT error_code FROM knowledge.document_version WHERE id = ?",
                                String.class,
                                version))
                .isEqualTo("DOCUMENT_EMPTY");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM knowledge.knowledge_document WHERE id = ?",
                                String.class,
                                id))
                .isEqualTo("FAILED");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM knowledge.document_chunk", Integer.class))
                .isZero();
    }

    @Test
    void chunkConstraintFailureRollsBackAllChunksAndPreparedMarker() throws Exception {
        var response =
                upload(TENANT, "ADMIN", Map.of("title", "Guide"), TEXT, "text/plain").andReturn();
        var body = mapper.readTree(response.getResponse().getContentAsString());
        var id = UUID.fromString(body.get("documentId").asText());
        var version = UUID.fromString(body.get("versionId").asText());
        var duplicateOrdinals =
                List.of(
                        new Chunk(0, null, null, "first", "a".repeat(64), 1),
                        new Chunk(0, null, null, "second", "b".repeat(64), 1));
        assertThatThrownBy(
                        () ->
                                documents.saveChunks(
                                        TENANT,
                                        id,
                                        version,
                                        duplicateOrdinals,
                                        DocumentChunker.CHUNKING_VERSION,
                                        Instant.now()))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM knowledge.document_chunk", Integer.class))
                .isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT prepared_at FROM knowledge.document_version WHERE id = ?",
                                Instant.class,
                                version))
                .isNull();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM knowledge.document_version WHERE id = ?",
                                String.class,
                                version))
                .isEqualTo("QUEUED");
        ingestion.prepare(TENANT, id, version);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM knowledge.document_chunk", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void concurrentPreparationsPersistOneChunkSet() throws Exception {
        var response =
                upload(TENANT, "ADMIN", Map.of("title", "Guide"), TEXT, "text/plain").andReturn();
        var body = mapper.readTree(response.getResponse().getContentAsString());
        var id = UUID.fromString(body.get("documentId").asText());
        var version = UUID.fromString(body.get("versionId").asText());
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var start = new java.util.concurrent.CountDownLatch(1);
            var tasks = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < 2; i++) {
                tasks.add(
                        executor.submit(
                                () -> {
                                    start.await();
                                    ingestion.prepare(TENANT, id, version);
                                    return null;
                                }));
            }
            start.countDown();
            for (var task : tasks) {
                task.get(30, java.util.concurrent.TimeUnit.SECONDS);
            }
        }
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM knowledge.document_chunk", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void oneRequestUploadsVerifiedFileAndCreatesQueuedMetadataAndOutbox() throws Exception {
        var response =
                upload(TENANT, "ADMIN", Map.of("title", "Account guide"), TEXT, "application/pdf")
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.status").value("QUEUED"))
                        .andExpect(jsonPath("$.fileName").value("guide.txt"))
                        .andExpect(jsonPath("$.mimeType").value("text/plain"))
                        .andExpect(jsonPath("$.sizeBytes").value(TEXT.length))
                        .andExpect(jsonPath("$.sha256").value(checksum(TEXT)))
                        .andExpect(jsonPath("$.visibility").value("SUPPORT_TEAM_ONLY"))
                        .andExpect(jsonPath("$.uploadUrl").doesNotExist())
                        .andExpect(jsonPath("$.uploadExpiresAt").doesNotExist())
                        .andReturn();
        var metadata = mapper.readTree(response.getResponse().getContentAsString());
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM knowledge.outbox_event", Integer.class))
                .isEqualTo(1);
        var payload =
                mapper.readTree(
                        jdbc.queryForObject(
                                "SELECT payload::text FROM knowledge.outbox_event", String.class));
        assertThat(payload.get("tenantId").asText()).isEqualTo(TENANT.toString());
        assertThat(payload.get("eventType").asText()).isEqualTo("knowledge.ingest.requested.v1");
        assertThat(payload.get("data").get("documentId").asText())
                .isEqualTo(metadata.get("documentId").asText());
        assertThat(payload.get("data").get("documentVersionId").asText())
                .isEqualTo(metadata.get("versionId").asText());
        var key =
                jdbc.queryForObject(
                        "SELECT object_key FROM knowledge.document_version", String.class);
        assertThat(key).startsWith("documents/" + TENANT + "/");
        try (var object =
                minio.getObject(
                        GetObjectArgs.builder().bucket("knowledge-test").object(key).build())) {
            assertThat(object.readAllBytes()).isEqualTo(TEXT);
        }
    }

    @Test
    void unsupportedBinaryIsRejectedWithoutMetadataOrOutbox() throws Exception {
        upload(TENANT, "ADMIN", Map.of("title", "Guide"), new byte[] {0, 1, 2}, "application/pdf")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_DOCUMENT_TYPE"));
        assertDatabaseEmpty();
    }

    @Test
    void rejectsEmptyFileAndInvalidMetadata() throws Exception {
        upload(TENANT, "ADMIN", Map.of("title", "Guide"), new byte[0], "text/plain")
                .andExpect(status().isBadRequest());
        upload(TENANT, "ADMIN", Map.of("title", ""), TEXT, "text/plain")
                .andExpect(status().isBadRequest());
        upload(
                        TENANT,
                        "ADMIN",
                        Map.of("title", "Guide", "tenantId", OTHER_TENANT),
                        TEXT,
                        "text/plain")
                .andExpect(status().isBadRequest());
        assertDatabaseEmpty();
    }

    @Test
    void requiresBothMultipartParts() throws Exception {
        mvc.perform(
                        multipart(BASE)
                                .file(metadata(Map.of("title", "Guide")))
                                .with(actor(TENANT, "ADMIN")))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        multipart(BASE)
                                .file(
                                        new MockMultipartFile(
                                                "file", "guide.txt", "text/plain", TEXT))
                                .with(actor(TENANT, "ADMIN")))
                .andExpect(status().isBadRequest());
        assertDatabaseEmpty();
    }

    @Test
    void metadataIsTenantScopedAndUploadRequiresAdmin() throws Exception {
        upload(TENANT, "CUSTOMER", Map.of("title", "Guide"), TEXT, "text/plain")
                .andExpect(status().isForbidden());
        var response =
                upload(TENANT, "ADMIN", Map.of("title", "Guide"), TEXT, "text/plain").andReturn();
        var id =
                mapper.readTree(response.getResponse().getContentAsString())
                        .get("documentId")
                        .asText();
        mvc.perform(get(BASE + "/" + id).with(actor(TENANT, "ADMIN"))).andExpect(status().isOk());
        mvc.perform(get(BASE + "/" + id).with(actor(OTHER_TENANT, "ADMIN")))
                .andExpect(status().isNotFound());
        mvc.perform(get(BASE).with(actor(OTHER_TENANT, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get(BASE).with(actor(TENANT, "CUSTOMER"))).andExpect(status().isForbidden());
        mvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        mvc.perform(get(BASE + "?page=-1&size=101").with(actor(TENANT, "ADMIN")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void oldUploadAndCompleteEndpointsAreRemoved() throws Exception {
        mvc.perform(
                        post(BASE + "/uploads")
                                .with(actor(TENANT, "ADMIN"))
                                .contentType("application/json")
                                .content("{}"))
                .andExpect(status().isMethodNotAllowed());
        mvc.perform(
                        post(BASE + "/" + UUID.randomUUID() + "/complete")
                                .with(actor(TENANT, "ADMIN"))
                                .contentType("application/json")
                                .content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void outboxFailureRollsBackMetadataAndRetryCanUploadAgain() throws Exception {
        doThrow(new DocumentException("OUTBOX_UNAVAILABLE", 503, "Retry later"))
                .when(documents)
                .appendIngestionRequested(any(), any());
        upload(TENANT, "ADMIN", Map.of("title", "Guide"), TEXT, "text/plain")
                .andExpect(status().isServiceUnavailable());
        assertDatabaseEmpty();
        doCallRealMethod().when(documents).appendIngestionRequested(any(), any());
        upload(TENANT, "ADMIN", Map.of("title", "Guide"), TEXT, "text/plain")
                .andExpect(status().isCreated());
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM knowledge.outbox_event", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void realHttpRejectsFileOver25MiBWith413() throws Exception {
        var boundary = "upload-" + UUID.randomUUID();
        var prefix =
                ("--"
                                + boundary
                                + "\r\nContent-Disposition: form-data; name=\"metadata\"\r\n"
                                + "Content-Type: application/json\r\n\r\n{\"title\":\"Guide\"}\r\n--"
                                + boundary
                                + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"guide.txt\"\r\n"
                                + "Content-Type: text/plain\r\n\r\n")
                        .getBytes(StandardCharsets.UTF_8);
        var suffix = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
        var oversized = new byte[25 * 1024 * 1024 + 1];
        var request =
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + BASE))
                        .timeout(java.time.Duration.ofSeconds(30))
                        .header("Authorization", "Bearer test-token")
                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                        .POST(
                                HttpRequest.BodyPublishers.ofByteArrays(
                                        List.of(prefix, oversized, suffix)))
                        .build();
        try (var client = HttpClient.newHttpClient()) {
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(413);
            assertThat(mapper.readTree(response.body()).get("code").asText())
                    .isEqualTo("UPLOAD_TOO_LARGE");
        }
        assertDatabaseEmpty();
    }

    private void assertDatabaseEmpty() {
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM knowledge.knowledge_document", Integer.class))
                .isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM knowledge.document_version", Integer.class))
                .isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM knowledge.outbox_event", Integer.class))
                .isZero();
    }

    @Test
    void upgradeRetiresOldReservationsWithoutLosingQueuedOrActiveDocuments() throws Exception {
        jdbc.execute("CREATE DATABASE knowledge_upgrade_test");
        var url =
                "jdbc:postgresql://"
                        + POSTGRES.getHost()
                        + ":"
                        + POSTGRES.getMappedPort(5432)
                        + "/knowledge_upgrade_test";
        org.flywaydb.core.Flyway.configure()
                .dataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword())
                .target("1")
                .load()
                .migrate();
        try (var connection =
                java.sql.DriverManager.getConnection(
                        url, POSTGRES.getUsername(), POSTGRES.getPassword())) {
            connection.setAutoCommit(false);
            for (var legacyStatus : List.of("UPLOADING", "QUEUED", "ACTIVE")) {
                var id = UUID.randomUUID();
                var versionId = UUID.randomUUID();
                try (var insertVersion =
                                connection.prepareStatement(
                                        """
                        INSERT INTO knowledge.document_version
                          (id, document_id, version_number, object_key, mime_type, size_bytes, status, upload_expires_at, created_at)
                        VALUES (?, ?, 1, ?, 'text/plain', 5, ?, now() + interval '15 minutes', now())
                        """);
                        var insertDocument =
                                connection.prepareStatement(
                                        """
                        INSERT INTO knowledge.knowledge_document
                          (id, tenant_id, title, file_name, visibility, status, current_version_id, active_version_id, created_by, created_at, updated_at)
                        VALUES (?, ?, 'Legacy guide', 'guide.txt',
                          CASE ? WHEN 'ACTIVE' THEN 'TENANT' WHEN 'QUEUED' THEN 'RESTRICTED' ELSE 'AGENTS_ONLY' END,
                          ?, ?, ?, ?, now(), now())
                        """)) {
                    insertVersion.setObject(1, versionId);
                    insertVersion.setObject(2, id);
                    insertVersion.setString(3, "legacy/" + versionId);
                    insertVersion.setString(4, legacyStatus);
                    insertVersion.executeUpdate();
                    insertDocument.setObject(1, id);
                    insertDocument.setObject(2, TENANT);
                    insertDocument.setString(3, legacyStatus);
                    insertDocument.setString(4, legacyStatus);
                    insertDocument.setObject(5, versionId);
                    insertDocument.setObject(6, legacyStatus.equals("ACTIVE") ? versionId : null);
                    insertDocument.setObject(7, UUID.randomUUID());
                    insertDocument.executeUpdate();
                }
            }
            connection.commit();
        }
        org.flywaydb.core.Flyway.configure()
                .dataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword())
                .load()
                .migrate();
        try (var connection =
                        java.sql.DriverManager.getConnection(
                                url, POSTGRES.getUsername(), POSTGRES.getPassword());
                var statement = connection.createStatement();
                var rows =
                        statement.executeQuery(
                                """
                        SELECT d.status, v.error_code, d.active_version_id, d.visibility
                        FROM knowledge.knowledge_document d JOIN knowledge.document_version v ON d.current_version_id = v.id
                        ORDER BY d.status
                        """)) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString(1)).isEqualTo("ACTIVE");
            assertThat(rows.getString(4)).isEqualTo("ALL_TENANT_USERS");
            assertThat(rows.getObject(3)).isNotNull();
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString(1)).isEqualTo("FAILED");
            assertThat(rows.getString(4)).isEqualTo("SUPPORT_TEAM_ONLY");
            assertThat(rows.getString(2)).isEqualTo("UPLOAD_FLOW_REPLACED");
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString(1)).isEqualTo("QUEUED");
            assertThat(rows.getString(4)).isEqualTo("LIMITED_ACCESS");
            assertThat(rows.next()).isFalse();
        }
    }

    private MockMultipartFile metadata(Map<String, ?> fields) throws Exception {
        return new MockMultipartFile(
                "metadata", "", "application/json", mapper.writeValueAsBytes(fields));
    }

    private ResultActions upload(
            UUID tenant, String role, Map<String, ?> fields, byte[] bytes, String mime)
            throws Exception {
        return mvc.perform(
                multipart(BASE)
                        .file(metadata(fields))
                        .file(new MockMultipartFile("file", "C:\\fakepath\\guide.txt", mime, bytes))
                        .with(actor(tenant, role)));
    }

    private RequestPostProcessor actor(UUID tenant, String role) {
        return authentication(new PlatformJwtAuthenticationConverter().convert(jwt(tenant, role)));
    }

    private Jwt jwt(UUID tenant, String role) {
        return Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .subject(UUID.randomUUID().toString())
                .claim("tid", tenant.toString())
                .claim("roles", List.of(role))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
    }

    private String checksum(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
