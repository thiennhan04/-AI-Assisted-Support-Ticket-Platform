package com.portfolio.identity.application;

public final class AuthInvalidCredentialsException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AuthInvalidCredentialsException() {
        super("Authentication failed");
    }
}
