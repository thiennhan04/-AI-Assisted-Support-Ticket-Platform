package com.portfolio.identity.infrastructure.config;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
    JwtProperties.class,
    LoginProtectionProperties.class,
    LocalSeedProperties.class
})
public class IdentityConfiguration {

    @Bean
    Clock systemClock() {
        return Clock.systemUTC();
    }
}
