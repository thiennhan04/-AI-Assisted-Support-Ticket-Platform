package com.portfolio.identity.application;

public interface RefreshTokenFactory {

    GeneratedRefreshToken generate();

    String hash(String rawValue);

    record GeneratedRefreshToken(String rawValue, String sha256Hash) {}
}
