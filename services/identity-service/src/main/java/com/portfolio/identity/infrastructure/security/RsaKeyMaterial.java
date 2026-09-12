package com.portfolio.identity.infrastructure.security;

import com.nimbusds.jose.jwk.RSAKey;
import java.util.List;

public record RsaKeyMaterial(RSAKey signingKey, List<RSAKey> publicKeys) {

    public RsaKeyMaterial {
        publicKeys = List.copyOf(publicKeys);
    }
}
