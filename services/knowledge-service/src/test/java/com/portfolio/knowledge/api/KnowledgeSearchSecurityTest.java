package com.portfolio.knowledge.api;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.portfolio.knowledge.application.SearchService;
import com.portfolio.knowledge.infrastructure.security.SecurityConfiguration;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(KnowledgeSearchInternalController.class)
@Import(SecurityConfiguration.class)
class KnowledgeSearchSecurityTest {
    private static final String PATH = "/internal/v1/knowledge/search";
    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();
    private static final RSAKey KEY;
    private static final HttpServer JWKS;

    static {
        try {
            KEY = new RSAKeyGenerator(2048).keyID("service-tests").generate();
            JWKS = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            JWKS.createContext(
                    "/jwks",
                    exchange -> {
                        var body =
                                new JWKSet(KEY.toPublicJWK())
                                        .toString()
                                        .getBytes(StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().set("Content-Type", "application/json");
                        exchange.sendResponseHeaders(200, body.length);
                        try (var output = exchange.getResponseBody()) {
                            output.write(body);
                        }
                    });
            JWKS.start();
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add(
                "security.service-jwt.jwk-set-uri",
                () -> "http://127.0.0.1:" + JWKS.getAddress().getPort() + "/jwks");
    }

    @AfterAll
    static void closeServer() {
        JWKS.stop(0);
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockitoBean SearchService search;
    @MockitoBean JwtDecoder userDecoder;

    @Test
    void validSignedServiceDelegationCanSearch() throws Exception {
        when(search.search(any(), anyString(), anyInt(), anyList())).thenReturn(List.of());
        var token =
                token(
                        "ai-orchestrator-service",
                        "knowledge-internal",
                        "knowledge:search",
                        Instant.now().plusSeconds(60));
        mvc.perform(
                        post(PATH)
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body(TENANT)))
                .andExpect(status().isOk());
        verify(search)
                .search(
                        argThat(
                                caller ->
                                        caller.tenantId().equals(TENANT)
                                                && caller.userId().equals(USER)),
                        eq("password reset"),
                        eq(8),
                        eq(List.of()));
    }

    @Test
    void deniesTenantSpoofingEvenForAuthenticatedService() throws Exception {
        var token =
                token(
                        "ai-orchestrator-service",
                        "knowledge-internal",
                        "knowledge:search",
                        Instant.now().plusSeconds(60));
        mvc.perform(
                        post(PATH)
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body(UUID.randomUUID())))
                .andExpect(status().isForbidden());
        verifyNoInteractions(search);
    }

    @Test
    void rejectsUserTokenWrongAudienceExpiredTokenAndMissingScope() throws Exception {
        var validUntil = Instant.now().plusSeconds(60);
        for (var token :
                List.of(
                        token(USER.toString(), "ticket-platform", "knowledge:search", validUntil),
                        token(
                                "ai-orchestrator-service",
                                "ticket-platform",
                                "knowledge:search",
                                validUntil),
                        token(
                                "ai-orchestrator-service",
                                "knowledge-internal",
                                "knowledge:search",
                                Instant.now().minusSeconds(120)))) {
            mvc.perform(
                            post(PATH)
                                    .header("Authorization", "Bearer " + token)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body(TENANT)))
                    .andExpect(status().isUnauthorized());
        }
        var noScope = token("ai-orchestrator-service", "knowledge-internal", "other", validUntil);
        mvc.perform(
                        post(PATH)
                                .header("Authorization", "Bearer " + noScope)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body(TENANT)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(search);
    }

    @Test
    void rejectsAnonymousAndInvalidPagination() throws Exception {
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body(TENANT)))
                .andExpect(status().isUnauthorized());
        var token =
                token(
                        "ai-orchestrator-service",
                        "knowledge-internal",
                        "knowledge:search",
                        Instant.now().plusSeconds(60));
        var badBody = mapper.readTree(body(TENANT));
        ((com.fasterxml.jackson.databind.node.ObjectNode) badBody).put("topK", 21);
        mvc.perform(
                        post(PATH)
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(mapper.writeValueAsBytes(badBody)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(search);
    }

    private String body(UUID tenant) throws Exception {
        return mapper.writeValueAsString(
                Map.of(
                        "tenantId",
                        tenant,
                        "principalId",
                        USER,
                        "roles",
                        List.of("CUSTOMER"),
                        "query",
                        "password reset"));
    }

    private String token(String subject, String audience, String scope, Instant expiresAt)
            throws Exception {
        var claims =
                new JWTClaimsSet.Builder()
                        .issuer("http://localhost:8081")
                        .subject(subject)
                        .audience(audience)
                        .issueTime(Date.from(Instant.now().minusSeconds(180)))
                        .expirationTime(Date.from(expiresAt))
                        .claim("tid", TENANT.toString())
                        .claim("principal_id", USER.toString())
                        .claim("roles", List.of("CUSTOMER"))
                        .claim("scope", scope)
                        .build();
        var header =
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .keyID(KEY.getKeyID())
                        .type(JOSEObjectType.JWT)
                        .build();
        var signed = new SignedJWT(header, claims);
        signed.sign(new RSASSASigner(KEY));
        return signed.serialize();
    }
}
