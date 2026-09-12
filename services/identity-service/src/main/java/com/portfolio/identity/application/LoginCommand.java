package com.portfolio.identity.application;

public record LoginCommand(
        String tenantCode, String email, String password, String clientAddress) {}
