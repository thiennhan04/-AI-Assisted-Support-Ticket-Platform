package com.portfolio.identity.application;

public interface RefreshTokenFactory {

    GeneratedRefreshToken generate();

    record GeneratedRefreshToken(String rawValue, String sha256Hash) {}
}
