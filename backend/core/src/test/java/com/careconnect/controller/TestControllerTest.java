package com.careconnect.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TestControllerTest {

    @InjectMocks
    private TestController controller;

    @Test
    void healthCheck_returnsOkWithHealthyStatus() throws Exception {
        final ResponseEntity<Map<String, Object>> response = controller.healthCheck();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("status")).isEqualTo("healthy");
        assertThat(response.getBody().get("message")).isEqualTo("CareConnect API is running successfully!");
        assertThat(response.getBody().get("version")).isEqualTo("1.0.0");
    }

    @Test
    void swaggerInfo_returnsOkWithGuideContent() throws Exception {
        final ResponseEntity<Map<String, Object>> response = controller.swaggerInfo();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("message")).isEqualTo("Welcome to CareConnect API!");
        assertThat(response.getBody().get("swaggerUrl")).isEqualTo("/swagger-ui.html");
        assertThat(response.getBody().get("authenticationRequired")).isEqualTo(false);
    }
}
