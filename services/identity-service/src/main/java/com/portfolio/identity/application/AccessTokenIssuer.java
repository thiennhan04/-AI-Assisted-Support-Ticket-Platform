package com.portfolio.identity.application;

import com.portfolio.identity.domain.UserAccount;
import java.time.Instant;

public interface AccessTokenIssuer {

    IssuedAccessToken issue(UserAccount user, Instant issuedAt);

    record IssuedAccessToken(String value, long expiresInSeconds) {}
}
