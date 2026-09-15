package com.portfolio.identity.application;

public class InvalidRefreshTokenException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidRefreshTokenException() {
        super("Refresh token is invalid");
    }
}
