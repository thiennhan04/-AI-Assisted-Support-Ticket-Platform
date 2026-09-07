package com.portfolio.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

class AiOrchestratorApplicationTest {

    @Test
    void exposesExpectedApplicationIdentity() {
        var yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));

        assertThat(AiOrchestratorApplication.class.getPackageName()).isEqualTo("com.portfolio.ai");
        assertThat(yaml.getObject())
                .containsEntry("spring.application.name", "ai-orchestrator-service");
    }
}
