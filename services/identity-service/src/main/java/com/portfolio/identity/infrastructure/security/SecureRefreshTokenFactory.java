package com.portfolio.identity.infrastructure.security;

import com.portfolio.identity.application.RefreshTokenFactory;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

@Component
public class SecureRefreshTokenFactory implements RefreshTokenFactory {

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public GeneratedRefreshToken generate() {
        var bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        var raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new GeneratedRefreshToken(raw, hash(raw));
    }

    @Override
    public String hash(String rawValue) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of()
                    .formatHex(digest.digest(rawValue.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
