package com.portfolio.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.knowledge.api.dto.DocumentResponse;
import com.portfolio.knowledge.application.AuthenticatedPrincipal;
import com.portfolio.knowledge.application.DocumentCleanupService;
import com.portfolio.knowledge.application.DocumentService;
import com.portfolio.knowledge.application.DocumentStorage;
import com.portfolio.knowledge.application.EmbeddingPort;
import com.portfolio.knowledge.application.IndexingService;
import com.portfolio.knowledge.application.IngestionService;
import com.portfolio.knowledge.application.SearchService;
import com.portfolio.knowledge.domain.DocumentException;
import com.portfolio.knowledge.domain.DocumentLifecycleRepository;
import com.portfolio.knowledge.domain.KnowledgeIndexRepository;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
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

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class DocumentLifecycleIT {
    private static final String BASE = "/v1/knowledge/documents";
    private static final String BUCKET = "lifecycle-tests";
    private static final AuthenticatedPrincipal ADMIN =
            new AuthenticatedPrincipal(UUID.randomUUID(), UUID.randomUUID(), Set.of("ADMIN"));

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
        registry.add("knowledge.storage.bucket", () -> BUCKET);
        registry.add("knowledge.cleanup.enabled", () -> "false");
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired MinioClient minio;
    @Autowired DocumentService documents;
    @Autowired IngestionService ingestion;
    @Autowired IndexingService indexing;
    @Autowired SearchService search;
    @Autowired DocumentCleanupService cleanup;
    @Autowired DocumentLifecycleRepository lifecycle;
    @Autowired KnowledgeIndexRepository index;
    @Autowired EmbeddingPort embedding;
    @MockitoSpyBean DocumentStorage storage;

    @BeforeEach
    void setup() throws Exception {
        jdbc.execute(
                "TRUNCATE knowledge.outbox_event, knowledge.knowledge_document, knowledge.document_version CASCADE");
        var exists = BucketExistsArgs.builder().bucket(BUCKET).build();
        if (!minio.bucketExists(exists)) {
            minio.makeBucket(MakeBucketArgs.builder().bucket(BUCKET).build());
        }
    }

    @Test
    void replacementKeepsOldVersionSearchableUntilAtomicActivationAndDelayedCleanup()
            throws Exception {
        var original = activeDocument();
        var next =
                replacement(original.documentId(), "\"1\"", "New password recovery guide")
                        .andExpect(status().isCreated())
                        .andExpect(header().string("ETag", "\"2\""))
                        .andExpect(
                                jsonPath("$.activeVersionId")
                                        .value(original.versionId().toString()))
                        .andReturn();
        var staged =
                mapper.readValue(
                        next.getResponse().getContentAsByteArray(), DocumentResponse.class);
        assertThat(staged.documentId()).isEqualTo(original.documentId());
        assertThat(staged.versionNumber()).isEqualTo(2);
        assertThat(staged.versionId()).isNotEqualTo(original.versionId());
        assertSearchVersion(1);
        ingestion.prepare(ADMIN.tenantId(), staged.documentId(), staged.versionId());
        assertSearchVersion(1);
        indexing.index(ADMIN.tenantId(), staged.documentId(), staged.versionId());
        assertSearchVersion(2);
        assertThat(versionStatus(original.versionId())).isEqualTo("SUPERSEDED");
        assertThat(cleanup.processNext()).isFalse();
        assertObjectPresent(original.versionId());
        assertThat(chunkCount(original.versionId())).isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM knowledge.outbox_event WHERE event_type = 'knowledge.document.activated.v1'",
                                Integer.class))
                .isEqualTo(2);
        var previous =
                jdbc.queryForObject(
                        "SELECT payload->'data'->>'previousVersionId' FROM knowledge.outbox_event WHERE event_type = 'knowledge.document.activated.v1' AND payload->'data'->>'documentVersionId' = ?",
                        String.class,
                        staged.versionId().toString());
        assertThat(previous).isEqualTo(original.versionId().toString());
        makeCleanupDue(original.versionId());
        assertThat(cleanup.processNext()).isTrue();
        assertObjectAbsent(original.versionId());
        assertThat(chunkCount(original.versionId())).isZero();
        assertObjectPresent(staged.versionId());
        assertSearchVersion(2);
        assertThat(cleanup.processNext()).isFalse();
    }

    @Test
    void failedReplacementLeavesOldActiveAndAllowsAnotherVersion() throws Exception {
        var original = activeDocument();
        var staged = replaceAndRead(original.documentId(), "\"1\"", " \n\t");
        assertThatThrownBy(
                        () ->
                                ingestion.prepare(
                                        ADMIN.tenantId(), staged.documentId(), staged.versionId()))
                .isInstanceOf(DocumentException.class);
        assertThat(versionStatus(staged.versionId())).isEqualTo("FAILED");
        assertSearchVersion(1);
        var retry =
                replaceAndRead(original.documentId(), "\"2\"", "Correct password recovery guide");
        assertThat(retry.versionNumber()).isEqualTo(3);
        assertSearchVersion(1);
        activate(retry);
        assertSearchVersion(3);
    }

    @Test
    void deleteImmediatelyHidesAllVersionsAndLateJobsCannotReactivateThem() throws Exception {
        var original = activeDocument();
        var staged = replaceAndRead(original.documentId(), "\"1\"", "New password recovery guide");
        ingestion.prepare(ADMIN.tenantId(), staged.documentId(), staged.versionId());
        var prepared =
                index.loadPrepared(
                        ADMIN.tenantId(),
                        staged.documentId(),
                        staged.versionId(),
                        embedding.modelId());
        var vectors = new ArrayList<KnowledgeIndexRepository.ChunkEmbedding>();
        for (var chunk : prepared) {
            var vector = embedding.embed(List.of(chunk.content())).getFirst();
            vectors.add(new KnowledgeIndexRepository.ChunkEmbedding(chunk.id(), vector));
        }
        mvc.perform(delete(BASE + "/" + original.documentId()).with(actor(ADMIN)))
                .andExpect(status().isAccepted());
        assertThat(search.search(ADMIN, "password", 20, List.of())).isEmpty();
        assertObjectPresent(original.versionId());
        assertThatThrownBy(
                        () ->
                                index.activate(
                                        ADMIN.tenantId(),
                                        staged.documentId(),
                                        staged.versionId(),
                                        embedding.modelId(),
                                        vectors,
                                        Instant.now()))
                .isInstanceOf(DocumentException.class);
        assertThatThrownBy(
                        () ->
                                ingestion.prepare(
                                        ADMIN.tenantId(), staged.documentId(), staged.versionId()))
                .isInstanceOf(DocumentException.class);
        assertThat(cleanup.processNext()).isTrue();
        assertThat(cleanup.processNext()).isTrue();
        assertThat(cleanup.processNext()).isFalse();
        mvc.perform(get(BASE + "/" + original.documentId()).with(actor(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentStatus").value("DELETED"))
                .andExpect(jsonPath("$.activeVersionId").isEmpty());
        assertObjectAbsent(original.versionId());
        assertObjectAbsent(staged.versionId());
        assertThat(chunkCount(original.versionId()) + chunkCount(staged.versionId())).isZero();
        mvc.perform(delete(BASE + "/" + original.documentId()).with(actor(ADMIN)))
                .andExpect(status().isAccepted());
        replacement(original.documentId(), "\"2\"", "Cannot restore a deleted document")
                .andExpect(status().isConflict());
    }

    @Test
    void replacementRequiresCurrentEtagAndAdminOfSameTenant() throws Exception {
        var original = activeDocument();
        replacement(original.documentId(), null, "New guide")
                .andExpect(status().isPreconditionRequired());
        replacement(original.documentId(), "1", "New guide").andExpect(status().isBadRequest());
        replacement(original.documentId(), "\"2\"", "New guide")
                .andExpect(status().isPreconditionFailed());
        var customer =
                new AuthenticatedPrincipal(UUID.randomUUID(), ADMIN.tenantId(), Set.of("CUSTOMER"));
        mvc.perform(
                        multipart(BASE + "/" + original.documentId() + "/versions")
                                .file(file("New guide"))
                                .header("If-Match", "\"1\"")
                                .with(actor(customer)))
                .andExpect(status().isForbidden());
        mvc.perform(delete(BASE + "/" + original.documentId()).with(actor(customer)))
                .andExpect(status().isForbidden());
        var other =
                new AuthenticatedPrincipal(UUID.randomUUID(), UUID.randomUUID(), Set.of("ADMIN"));
        mvc.perform(
                        multipart(BASE + "/" + original.documentId() + "/versions")
                                .file(file("New guide"))
                                .header("If-Match", "\"1\"")
                                .with(actor(other)))
                .andExpect(status().isNotFound());
        mvc.perform(delete(BASE + "/" + original.documentId()).with(actor(other)))
                .andExpect(status().isNotFound());
        assertSearchVersion(1);
        var staged = replaceAndRead(original.documentId(), "\"1\"", "New guide");
        replacement(original.documentId(), "\"1\"", "Stale guide")
                .andExpect(status().isPreconditionFailed());
        replacement(original.documentId(), "\"2\"", "Another guide")
                .andExpect(status().isConflict());
        assertThat(staged.versionNumber()).isEqualTo(2);
    }

    @Test
    void concurrentReplacementsOnlyCreateOneNewVersion() throws Exception {
        var original = activeDocument();
        var start = new CountDownLatch(1);
        Callable<Integer> replace =
                () -> {
                    start.await();
                    try {
                        documents.replace(
                                ADMIN,
                                original.documentId(),
                                "\"1\"",
                                file("Concurrent password guide"));
                        return 201;
                    } catch (DocumentException exception) {
                        return exception.status();
                    }
                };
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(replace);
            var second = executor.submit(replace);
            start.countDown();
            assertThat(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 412);
        }
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM knowledge.document_version WHERE document_id = ?",
                                Integer.class,
                                original.documentId()))
                .isEqualTo(2);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM knowledge.outbox_event WHERE event_type = 'knowledge.ingest.requested.v1'",
                                Integer.class))
                .isEqualTo(2);
    }

    @Test
    void failedStageOutboxWriteRollsBackVersionAndPointer() throws Exception {
        var original = activeDocument();
        jdbc.execute(
                "ALTER TABLE knowledge.outbox_event ADD CONSTRAINT test_reject_ingest CHECK (event_type <> 'knowledge.ingest.requested.v1') NOT VALID");
        try {
            replacement(original.documentId(), "\"1\"", "New password guide")
                    .andExpect(status().isServiceUnavailable());
        } finally {
            jdbc.execute("ALTER TABLE knowledge.outbox_event DROP CONSTRAINT test_reject_ingest");
        }
        assertThat(documents.get(ADMIN, original.documentId()).version().id())
                .isEqualTo(original.versionId());
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM knowledge.document_version", Integer.class))
                .isEqualTo(1);
        assertSearchVersion(1);
        replaceAndRead(original.documentId(), "\"1\"", "Retry password guide");
    }

    @Test
    void activationEventFailurePreservesOldActiveAndRollsBackRetirement() throws Exception {
        var original = activeDocument();
        var staged = replaceAndRead(original.documentId(), "\"1\"", "New password guide");
        ingestion.prepare(ADMIN.tenantId(), staged.documentId(), staged.versionId());
        jdbc.execute(
                "ALTER TABLE knowledge.outbox_event ADD CONSTRAINT test_reject_activation CHECK (event_type <> 'knowledge.document.activated.v1') NOT VALID");
        try {
            assertThatThrownBy(
                            () ->
                                    indexing.index(
                                            ADMIN.tenantId(),
                                            staged.documentId(),
                                            staged.versionId()))
                    .isInstanceOf(org.springframework.dao.DataAccessException.class);
        } finally {
            jdbc.execute(
                    "ALTER TABLE knowledge.outbox_event DROP CONSTRAINT test_reject_activation");
        }
        assertSearchVersion(1);
        assertThat(versionStatus(original.versionId())).isEqualTo("ACTIVE");
        assertThat(versionStatus(staged.versionId())).isEqualTo("PROCESSING");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT cleanup_after FROM knowledge.document_version WHERE id = ?",
                                Timestamp.class,
                                original.versionId()))
                .isNull();
        indexing.index(ADMIN.tenantId(), staged.documentId(), staged.versionId());
        assertSearchVersion(2);
    }

    @Test
    void storageFailurePersistsRetryAndKeepsDocumentHidden() throws Exception {
        var original = activeDocument();
        documents.delete(ADMIN, original.documentId());
        var key = objectKey(original.versionId());
        doThrow(new DocumentException("STORAGE_UNAVAILABLE", 503, "Unavailable"))
                .doCallRealMethod()
                .when(storage)
                .delete(key);
        assertThat(cleanup.processNext()).isTrue();
        assertThat(chunkCount(original.versionId())).isEqualTo(1);
        assertThat(versionStatus(original.versionId())).isEqualTo("DELETING");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT cleanup_attempts FROM knowledge.document_version WHERE id = ?",
                                Integer.class,
                                original.versionId()))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT cleanup_error_code FROM knowledge.document_version WHERE id = ?",
                                String.class,
                                original.versionId()))
                .isEqualTo("STORAGE_UNAVAILABLE");
        assertThat(search.search(ADMIN, "password", 20, List.of())).isEmpty();
        assertThat(cleanup.processNext()).isFalse();
        makeCleanupDue(original.versionId());
        assertThat(cleanup.processNext()).isTrue();
        assertObjectAbsent(original.versionId());
        assertThat(chunkCount(original.versionId())).isZero();
    }

    @Test
    void expiredLeaseCanBeRecoveredAndStaleWorkerCannotCompleteNewLease() throws Exception {
        var original = activeDocument();
        documents.delete(ADMIN, original.documentId());
        var first = lifecycle.claimCleanup(Instant.now()).orElseThrow();
        assertThat(lifecycle.claimCleanup(Instant.now())).isEmpty();
        storage.delete(first.objectKey());
        jdbc.update(
                "UPDATE knowledge.document_version SET cleanup_lease_until = ? WHERE id = ?",
                Timestamp.from(Instant.now().minusSeconds(1)),
                first.versionId());
        var retry = lifecycle.claimCleanup(Instant.now()).orElseThrow();
        assertThat(retry.token()).isNotEqualTo(first.token());
        lifecycle.completeCleanup(first, Instant.now());
        assertThat(chunkCount(original.versionId())).isEqualTo(1);
        storage.delete(retry.objectKey());
        lifecycle.completeCleanup(retry, Instant.now());
        assertThat(chunkCount(original.versionId())).isZero();
        assertThat(documents.get(ADMIN, original.documentId()).status().name())
                .isEqualTo("DELETED");
    }

    @Test
    void cleanupNeverClaimsCurrentOrActiveVersionsEvenWithExpiredCleanupDate() throws Exception {
        var original = activeDocument();
        makeCleanupDue(original.versionId());
        assertThat(cleanup.processNext()).isFalse();
        var staged = replaceAndRead(original.documentId(), "\"1\"", "New password guide");
        makeCleanupDue(staged.versionId());
        assertThat(cleanup.processNext()).isFalse();
        assertObjectPresent(original.versionId());
        assertObjectPresent(staged.versionId());
        assertSearchVersion(1);
    }

    private DocumentResponse activeDocument() throws Exception {
        var metadata =
                new MockMultipartFile(
                        "metadata",
                        "",
                        "application/json",
                        "{\"title\":\"Account guide\",\"visibility\":\"ALL_TENANT_USERS\"}"
                                .getBytes(StandardCharsets.UTF_8));
        var result =
                mvc.perform(
                                multipart(BASE)
                                        .file(metadata)
                                        .file(file("Password recovery guide"))
                                        .with(actor(ADMIN)))
                        .andExpect(status().isCreated())
                        .andExpect(header().string("ETag", "\"1\""))
                        .andReturn();
        var document =
                mapper.readValue(
                        result.getResponse().getContentAsByteArray(), DocumentResponse.class);
        activate(document);
        return document;
    }

    private void activate(DocumentResponse document) {
        ingestion.prepare(ADMIN.tenantId(), document.documentId(), document.versionId());
        indexing.index(ADMIN.tenantId(), document.documentId(), document.versionId());
    }

    private DocumentResponse replaceAndRead(UUID documentId, String etag, String content)
            throws Exception {
        var result =
                replacement(documentId, etag, content).andExpect(status().isCreated()).andReturn();
        return mapper.readValue(
                result.getResponse().getContentAsByteArray(), DocumentResponse.class);
    }

    private ResultActions replacement(UUID documentId, String etag, String content)
            throws Exception {
        var request = multipart(BASE + "/" + documentId + "/versions").file(file(content));
        if (etag != null) {
            request.header("If-Match", etag);
        }
        return mvc.perform(request.with(actor(ADMIN)));
    }

    private MockMultipartFile file(String content) {
        return new MockMultipartFile(
                "file", "guide.txt", "text/plain", content.getBytes(StandardCharsets.UTF_8));
    }

    private RequestPostProcessor actor(AuthenticatedPrincipal caller) {
        return authentication(new TestingAuthenticationToken(caller, null, "ROLE_ADMIN"));
    }

    private void assertSearchVersion(int expected) {
        var results = search.search(ADMIN, "password", 20, List.of());
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().version()).isEqualTo(expected);
    }

    private String objectKey(UUID versionId) {
        return jdbc.queryForObject(
                "SELECT object_key FROM knowledge.document_version WHERE id = ?",
                String.class,
                versionId);
    }

    private String versionStatus(UUID versionId) {
        return jdbc.queryForObject(
                "SELECT status FROM knowledge.document_version WHERE id = ?",
                String.class,
                versionId);
    }

    private int chunkCount(UUID versionId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM knowledge.document_chunk WHERE document_version_id = ?",
                Integer.class,
                versionId);
    }

    private void makeCleanupDue(UUID versionId) {
        jdbc.update(
                "UPDATE knowledge.document_version SET cleanup_after = ? WHERE id = ?",
                Timestamp.from(Instant.now().minusSeconds(1)),
                versionId);
    }

    private void assertObjectPresent(UUID versionId) throws Exception {
        var args = StatObjectArgs.builder().bucket(BUCKET).object(objectKey(versionId)).build();
        assertThat(minio.statObject(args).size()).isPositive();
    }

    private void assertObjectAbsent(UUID versionId) {
        var args = StatObjectArgs.builder().bucket(BUCKET).object(objectKey(versionId)).build();
        assertThatThrownBy(() -> minio.statObject(args))
                .isInstanceOf(io.minio.errors.ErrorResponseException.class);
    }
}
