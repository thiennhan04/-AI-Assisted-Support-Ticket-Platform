package com.portfolio.identity.api;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.portfolio.identity.infrastructure.security.RsaKeyMaterial;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class JwksController {

    private final RsaKeyMaterial keys;

    public JwksController(RsaKeyMaterial keys) {
        this.keys = keys;
    }

    @GetMapping("/.well-known/jwks.json")
    public ResponseEntity<Map<String, Object>> jwks() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(java.time.Duration.ofMinutes(5)).cachePublic())
                .body(
                        new JWKSet(keys.publicKeys().stream().map(key -> (JWK) key).toList())
                                .toJSONObject());
    }
}
