package com.portfolio.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

class KnowledgeServiceApplicationTest {

    @Test
    void exposesExpectedApplicationIdentity() {
        var yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));

        assertThat(KnowledgeServiceApplication.class.getPackageName())
                .isEqualTo("com.portfolio.knowledge");
        assertThat(yaml.getObject()).containsEntry("spring.application.name", "knowledge-service");
    }
}
