package com.portfolio.identity.infrastructure.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import com.portfolio.identity.infrastructure.config.JwtProperties;
import java.util.ArrayList;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfiguration {

    @Bean
    RsaKeyMaterial rsaKeyMaterial(JwtProperties properties) {
        var publicKey = PemKeyLoader.readPublicKey(properties.publicKey());
        var privateKey = PemKeyLoader.readPrivateKey(properties.privateKey());
        var signingKey =
                new RSAKey.Builder(publicKey)
                        .privateKey(privateKey)
                        .keyID(properties.keyId())
                        .build();
        var publicKeys = new ArrayList<RSAKey>();
        publicKeys.add(signingKey.toPublicJWK());
        if (properties.previousPublicKey() != null
                && org.springframework.util.StringUtils.hasText(properties.previousKeyId())
                && properties.previousPublicKey().exists()) {
            publicKeys.add(
                    new RSAKey.Builder(PemKeyLoader.readPublicKey(properties.previousPublicKey()))
                            .keyID(properties.previousKeyId())
                            .build());
        }
        return new RsaKeyMaterial(signingKey, publicKeys);
    }

    @Bean
    JwtEncoder jwtEncoder(RsaKeyMaterial keys) {
        var jwkSet = new JWKSet(keys.signingKey());
        return new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(jwkSet));
    }

    @Bean
    JwtDecoder jwtDecoder(RsaKeyMaterial keys, JwtProperties properties)
            throws com.nimbusds.jose.JOSEException {
        var decoder = NimbusJwtDecoder.withPublicKey(keys.signingKey().toRSAPublicKey()).build();
        var issuerValidator = JwtValidators.createDefaultWithIssuer(properties.issuer());
        var audienceValidator =
                (org.springframework.security.oauth2.core.OAuth2TokenValidator<Jwt>)
                        token ->
                                token.getAudience().contains(properties.audience())
                                        ? OAuth2TokenValidatorResult.success()
                                        : OAuth2TokenValidatorResult.failure(
                                                new org.springframework.security.oauth2.core
                                                        .OAuth2Error(
                                                        "invalid_token", "Invalid audience", null));
        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(issuerValidator, audienceValidator));
        return decoder;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        requests ->
                                requests.requestMatchers(
                                                "/v1/auth/login",
                                                "/.well-known/jwks.json",
                                                "/actuator/health/**",
                                                "/actuator/info")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .oauth2ResourceServer(
                        resourceServer -> resourceServer.jwt(Customizer.withDefaults()))
                .build();
    }
}
