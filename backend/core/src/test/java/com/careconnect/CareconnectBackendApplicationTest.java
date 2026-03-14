package com.careconnect;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import static org.assertj.core.api.Assertions.assertThat;

class CareconnectBackendApplicationTest {

    @Test
    void applicationHasSpringBootApplicationAnnotation() throws Exception {
        assertThat(CareconnectBackendApplication.class)
                .hasAnnotation(SpringBootApplication.class);
    }

    @Test
    void applicationHasEnableSchedulingAnnotation() throws Exception {
        assertThat(CareconnectBackendApplication.class)
                .hasAnnotation(EnableScheduling.class);
    }

    @Test
    void mainMethodExists() throws Exception {
        assertThat(CareconnectBackendApplication.class)
                .hasDeclaredMethods("main");
    }
}
