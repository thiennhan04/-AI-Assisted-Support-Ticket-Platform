package com.portfolio.ticket.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import com.portfolio.ticket.application.AuthenticatedPrincipal;
import com.sun.net.httpserver.HttpServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class ResourceServerSecurityTest {

    private static final String ISSUER = "https://identity.test";
    private static final String AUDIENCE = "ticket-platform";
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();

    private static RSAKey currentKey;
    private static RSAKey previousKey;
    private static RSAKey unknownKey;
    private static HttpServer jwksServer;
    private static org.springframework.security.oauth2.jwt.JwtDecoder decoder;

    @BeforeAll
    static void startJwksServer() throws Exception {
        currentKey = generateKey("current-key");
        previousKey = generateKey("previous-key");
        unknownKey = generateKey("unknown-key");
        var body =
                new JWKSet(List.of(currentKey.toPublicJWK(), previousKey.toPublicJWK()))
                        .toString()
                        .getBytes(StandardCharsets.UTF_8);
        jwksServer =
                HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        jwksServer.createContext(
                "/jwks",
                exchange -> {
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, body.length);
                    try (var response = exchange.getResponseBody()) {
                        response.write(body);
                    }
                });
        jwksServer.start();

        var uri = URI.create("http://127.0.0.1:" + jwksServer.getAddress().getPort() + "/jwks");
        decoder =
                new SecurityConfiguration()
                        .jwtDecoder(new ResourceServerProperties(ISSUER, AUDIENCE, uri));
    }

    @AfterAll
    static void stopJwksServer() {
        jwksServer.stop(0);
    }

    @Test
    void acceptsCurrentAndPreviousKeysDuringRotation() {
        assertThat(decoder.decode(token(currentKey, ISSUER, AUDIENCE, TENANT_ID.toString())))
                .isNotNull();
        assertThat(decoder.decode(token(previousKey, ISSUER, AUDIENCE, TENANT_ID.toString())))
                .isNotNull();
    }

    @Test
    void mapsVerifiedClaimsToTenantAwarePrincipalAndAuthorities() {
        var jwt = decoder.decode(token(currentKey, ISSUER, AUDIENCE, TENANT_ID.toString()));
        var authentication = new PlatformJwtAuthenticationConverter().convert(jwt);

        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal())
                .isEqualTo(
                        new AuthenticatedPrincipal(
                                USER_ID, TENANT_ID, java.util.Set.of("AGENT", "ADMIN")));
        assertThat(authentication.getAuthorities())
                .extracting(Object::toString)
                .containsExactlyInAnyOrder("ROLE_AGENT", "ROLE_ADMIN");
    }

    @Test
    void rejectsUnknownSigningKey() {
        assertThatThrownBy(
                        () ->
                                decoder.decode(
                                        token(unknownKey, ISSUER, AUDIENCE, TENANT_ID.toString())))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsWrongIssuerAudienceOrTenantClaim() {
        assertRejected(token(currentKey, "https://wrong.test", AUDIENCE, TENANT_ID.toString()));
        assertRejected(token(currentKey, ISSUER, "wrong-audience", TENANT_ID.toString()));
        assertRejected(token(currentKey, ISSUER, AUDIENCE, "not-a-uuid"));
    }

    private static void assertRejected(String token) {
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    private static String token(RSAKey key, String issuer, String audience, String tenantId) {
        var now = Instant.now();
        var claims =
                JwtClaimsSet.builder()
                        .issuer(issuer)
                        .audience(List.of(audience))
                        .subject(USER_ID.toString())
                        .issuedAt(now)
                        .expiresAt(now.plusSeconds(300))
                        .claim("tid", tenantId)
                        .claim("roles", List.of("AGENT", "ADMIN"))
                        .build();
        var header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(key.getKeyID()).build();
        var encoder = new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(key)));
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private static RSAKey generateKey(String keyId) throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var pair = generator.generateKeyPair();
        return new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                .privateKey((RSAPrivateKey) pair.getPrivate())
                .keyID(keyId)
                .build();
    }
}
