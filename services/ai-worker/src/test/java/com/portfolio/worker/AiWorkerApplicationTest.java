package com.portfolio.worker;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

class AiWorkerApplicationTest {

    @Test
    void exposesExpectedApplicationIdentity() {
        var yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));

        assertThat(AiWorkerApplication.class.getPackageName()).isEqualTo("com.portfolio.worker");
        assertThat(yaml.getObject()).containsEntry("spring.application.name", "ai-worker");
    }
}
