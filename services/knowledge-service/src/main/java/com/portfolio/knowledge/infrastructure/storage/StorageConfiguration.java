package com.portfolio.knowledge.infrastructure.storage;

import io.minio.MinioClient;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StorageConfiguration {
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    MinioClient minioClient(
            @Value("${knowledge.storage.endpoint}") String endpoint,
            @Value("${knowledge.storage.access-key}") String accessKey,
            @Value("${knowledge.storage.secret-key}") String secretKey) {
        var client =
                MinioClient.builder()
                        .endpoint(endpoint)
                        .credentials(accessKey, secretKey)
                        .region("us-east-1")
                        .build();
        client.setTimeout(5000, 30000, 30000);
        return client;
    }
}
