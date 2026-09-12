package com.portfolio.identity.infrastructure.security;

import com.portfolio.identity.application.LoginRateLimiter;
import com.portfolio.identity.infrastructure.config.LoginProtectionProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisLoginRateLimiter implements LoginRateLimiter {

    private final StringRedisTemplate redis;
    private final LoginProtectionProperties properties;

    public RedisLoginRateLimiter(StringRedisTemplate redis, LoginProtectionProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    @Override
    public Optional<Duration> consume(
            String tenantCode, String normalizedEmail, String clientAddress) {
        var identityKey = "login:identity:" + sha256(tenantCode + ":" + normalizedEmail);
        var addressKey = "login:ip:" + sha256(clientAddress == null ? "unknown" : clientAddress);
        var identityRetry = consumeKey(identityKey);
        var addressRetry = consumeKey(addressKey);
        if (identityRetry.isEmpty()) {
            return addressRetry;
        }
        if (addressRetry.isEmpty()) {
            return identityRetry;
        }
        return Optional.of(
                identityRetry.orElseThrow().compareTo(addressRetry.orElseThrow()) >= 0
                        ? identityRetry.orElseThrow()
                        : addressRetry.orElseThrow());
    }

    private Optional<Duration> consumeKey(String key) {
        var count = redis.opsForValue().increment(key);
        if (count != null && count == 1) {
            redis.expire(key, properties.rateWindow());
        }
        if (count == null || count <= properties.rateLimit()) {
            return Optional.empty();
        }
        var ttl = redis.getExpire(key);
        return Optional.of(Duration.ofSeconds(Math.max(1, ttl)));
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
