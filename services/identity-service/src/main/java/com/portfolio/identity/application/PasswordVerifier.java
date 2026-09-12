package com.portfolio.identity.application;

public interface PasswordVerifier {

    boolean matches(CharSequence rawPassword, String encodedPassword);

    void performDummyCheck(CharSequence rawPassword);
}
