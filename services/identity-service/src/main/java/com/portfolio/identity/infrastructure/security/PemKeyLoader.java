package com.portfolio.identity.infrastructure.security;

import java.io.IOException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import org.springframework.core.io.Resource;

public final class PemKeyLoader {

    private PemKeyLoader() {}

    public static RSAPrivateKey readPrivateKey(Resource resource) {
        var bytes = decode(resource, "PRIVATE KEY");
        try {
            return (RSAPrivateKey)
                    KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(bytes));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException exception) {
            throw new IllegalStateException("Cannot read RSA private key", exception);
        }
    }

    public static RSAPublicKey readPublicKey(Resource resource) {
        var bytes = decode(resource, "PUBLIC KEY");
        try {
            return (RSAPublicKey)
                    KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(bytes));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException exception) {
            throw new IllegalStateException("Cannot read RSA public key", exception);
        }
    }

    private static byte[] decode(Resource resource, String type) {
        try {
            var pem =
                    new String(
                            resource.getInputStream().readAllBytes(),
                            java.nio.charset.StandardCharsets.US_ASCII);
            var normalized =
                    pem.replace("-----BEGIN " + type + "-----", "")
                            .replace("-----END " + type + "-----", "")
                            .replaceAll("\\s", "");
            return Base64.getDecoder().decode(normalized);
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalStateException("Cannot load PEM resource " + resource, exception);
        }
    }
}
