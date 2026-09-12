package com.portfolio.identity.infrastructure.security;

import com.portfolio.identity.application.AccessTokenIssuer;
import com.portfolio.identity.domain.Role;
import com.portfolio.identity.domain.UserAccount;
import com.portfolio.identity.infrastructure.config.JwtProperties;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

@Component
public class JwtAccessTokenIssuer implements AccessTokenIssuer {

    private final JwtEncoder encoder;
    private final JwtProperties properties;

    public JwtAccessTokenIssuer(JwtEncoder encoder, JwtProperties properties) {
        this.encoder = encoder;
        this.properties = properties;
    }

    @Override
    public IssuedAccessToken issue(UserAccount user, java.time.Instant issuedAt) {
        var expiresAt = issuedAt.plus(properties.accessTtl());
        var header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(properties.keyId()).build();
        var claims =
                JwtClaimsSet.builder()
                        .issuer(properties.issuer())
                        .audience(java.util.List.of(properties.audience()))
                        .subject(user.id().toString())
                        .id(UUID.randomUUID().toString())
                        .issuedAt(issuedAt)
                        .expiresAt(expiresAt)
                        .claim("tid", user.tenantId().toString())
                        .claim("roles", user.roles().stream().map(Role::name).sorted().toList())
                        .build();
        var token = encoder.encode(JwtEncoderParameters.from(header, claims));
        return new IssuedAccessToken(token.getTokenValue(), properties.accessTtl().toSeconds());
    }
}
