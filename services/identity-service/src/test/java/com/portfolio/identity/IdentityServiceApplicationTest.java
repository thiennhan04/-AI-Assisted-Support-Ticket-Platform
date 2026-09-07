package com.portfolio.identity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

class IdentityServiceApplicationTest {

    @Test
    void exposesExpectedApplicationIdentity() {
        var yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));

        assertThat(IdentityServiceApplication.class.getPackageName())
                .isEqualTo("com.portfolio.identity");
        assertThat(yaml.getObject()).containsEntry("spring.application.name", "identity-service");
    }
}
