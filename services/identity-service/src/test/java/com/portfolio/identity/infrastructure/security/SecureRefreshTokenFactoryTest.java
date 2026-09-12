package com.portfolio.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class SecureRefreshTokenFactoryTest {

    @Test
    void createsOpaqueUniqueTokensAndMatchingSha256Hashes() throws Exception {
        var factory = new SecureRefreshTokenFactory();
        var first = factory.generate();
        var second = factory.generate();

        assertThat(first.rawValue())
                .hasSizeGreaterThanOrEqualTo(32)
                .isNotEqualTo(second.rawValue());
        assertThat(first.sha256Hash())
                .hasSize(64)
                .isEqualTo(
                        HexFormat.of()
                                .formatHex(
                                        MessageDigest.getInstance("SHA-256")
                                                .digest(
                                                        first.rawValue()
                                                                .getBytes(
                                                                        StandardCharsets.UTF_8))));
        assertThat(first.sha256Hash()).doesNotContain(first.rawValue());
    }
}
