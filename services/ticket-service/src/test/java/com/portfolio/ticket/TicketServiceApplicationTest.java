package com.portfolio.ticket;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

class TicketServiceApplicationTest {

    @Test
    void exposesExpectedApplicationIdentity() {
        var yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));

        assertThat(TicketServiceApplication.class.getPackageName())
                .isEqualTo("com.portfolio.ticket");
        assertThat(yaml.getObject()).containsEntry("spring.application.name", "ticket-service");
    }
}
