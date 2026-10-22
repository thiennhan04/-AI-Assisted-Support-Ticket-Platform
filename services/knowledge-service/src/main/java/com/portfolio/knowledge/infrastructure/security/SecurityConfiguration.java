package com.portfolio.knowledge.infrastructure.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(ResourceServerProperties.class)
public class SecurityConfiguration {

    @Bean
    JwtDecoder jwtDecoder(ResourceServerProperties properties) {
        var decoder =
                NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri().toString())
                        .jwsAlgorithm(SignatureAlgorithm.RS256)
                        .build();
        OAuth2TokenValidator<Jwt> audienceValidator =
                token ->
                        token.getAudience().contains(properties.audience())
                                ? OAuth2TokenValidatorResult.success()
                                : OAuth2TokenValidatorResult.failure(
                                        new OAuth2Error("invalid_token", "Invalid audience", null));
        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(
                        JwtValidators.createDefaultWithIssuer(properties.issuer()),
                        audienceValidator,
                        new JwtClaimsValidator()));
        return decoder;
    }

    @Bean
    PlatformJwtAuthenticationConverter platformJwtAuthenticationConverter() {
        return new PlatformJwtAuthenticationConverter();
    }

    @Bean
    @Order(1)
    SecurityFilterChain internalSecurityFilterChain(
            HttpSecurity http,
            @Value("${security.service-jwt.issuer}") String issuer,
            @Value("${security.service-jwt.jwk-set-uri}") String jwksUri,
            @Value("${security.service-jwt.audience}") String audience,
            @Value("${security.service-jwt.subject}") String subject)
            throws Exception {
        var decoderBuilder = NimbusJwtDecoder.withJwkSetUri(jwksUri);
        decoderBuilder.jwsAlgorithm(SignatureAlgorithm.RS256);
        var decoder = decoderBuilder.build();
        var timestampAndIssuer = JwtValidators.createDefaultWithIssuer(issuer);
        var serviceClaims = new ServiceJwtClaimsValidator(audience, subject);
        var validators = new DelegatingOAuth2TokenValidator<Jwt>(timestampAndIssuer, serviceClaims);
        decoder.setJwtValidator(validators);
        http.securityMatcher("/internal/**");
        http.csrf(csrf -> csrf.disable());
        http.sessionManagement(
                session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        http.authorizeHttpRequests(
                requests ->
                        requests.requestMatchers("/internal/v1/knowledge/search")
                                .hasAuthority("SCOPE_knowledge:search")
                                .anyRequest()
                                .denyAll());
        http.oauth2ResourceServer(
                resourceServer -> resourceServer.jwt(jwt -> jwt.decoder(decoder)));
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain securityFilterChain(
            HttpSecurity http, PlatformJwtAuthenticationConverter converter) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        requests ->
                                requests.requestMatchers("/actuator/health/**", "/actuator/info")
                                        .permitAll()
                                        .requestMatchers("/internal/**")
                                        .denyAll()
                                        .anyRequest()
                                        .authenticated())
                .oauth2ResourceServer(
                        resourceServer ->
                                resourceServer.jwt(
                                        jwt -> jwt.jwtAuthenticationConverter(converter)))
                .build();
    }
}
