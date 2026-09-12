package com.portfolio.identity.infrastructure.security;

import com.portfolio.identity.application.PasswordVerifier;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class BCryptPasswordVerifier implements PasswordVerifier {

    private final PasswordEncoder encoder;
    private final String dummyHash;

    public BCryptPasswordVerifier(PasswordEncoder encoder) {
        this.encoder = encoder;
        this.dummyHash = encoder.encode("identity-dummy-password");
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        return encoder.matches(rawPassword, encodedPassword);
    }

    @Override
    public void performDummyCheck(CharSequence rawPassword) {
        encoder.matches(rawPassword, dummyHash);
    }
}
