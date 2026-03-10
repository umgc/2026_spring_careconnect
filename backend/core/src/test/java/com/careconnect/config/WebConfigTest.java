package com.careconnect.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WebConfigTest {

    private WebMvcConfigurer corsConfigurer;
    private CorsConfiguration config;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        WebConfig webConfig = new WebConfig();
        corsConfigurer = webConfig.corsConfigurer();

        CorsRegistry registry = new CorsRegistry();
        corsConfigurer.addCorsMappings(registry);

        Map<String, CorsConfiguration> configs =
                (Map<String, CorsConfiguration>) ReflectionTestUtils.invokeMethod(registry, "getCorsConfigurations");
        config = configs.get("/**");
    }

    @Test
    void corsConfigurer_IsNotNull() {
        assertNotNull(corsConfigurer);
    }

    @Test
    void corsConfigurer_MapsAllPaths() {
        assertNotNull(config, "Expected CORS configuration for '/**' path mapping");
    }

    @Test
    void corsConfigurer_HasCorrectAllowedOrigins() {
        List<String> expected = List.of(
                "http://localhost:8080",
                "http://127.0.0.1:8080",
                "http://localhost:5173",
                "http://localhost",
                "http://127.0.0.1"
        );
        assertEquals(expected, config.getAllowedOrigins());
    }

    @Test
    void corsConfigurer_HasCorrectAllowedOriginPatterns() {
        List<String> patterns = config.getAllowedOriginPatterns();
        assertNotNull(patterns);
        assertTrue(patterns.contains("http://localhost:*"));
        assertTrue(patterns.contains("http://127.0.0.1:*"));
        assertEquals(2, patterns.size());
    }

    @Test
    void corsConfigurer_AllowsCorrectMethods() {
        List<String> methods = config.getAllowedMethods();
        assertNotNull(methods);
        assertTrue(methods.containsAll(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS")));
        assertEquals(5, methods.size());
    }

    @Test
    void corsConfigurer_AllowsCredentials() {
        assertTrue(config.getAllowCredentials());
    }
}
