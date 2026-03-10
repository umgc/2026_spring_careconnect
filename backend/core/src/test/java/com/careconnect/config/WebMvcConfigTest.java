package com.careconnect.config;

import jakarta.servlet.ServletContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class WebMvcConfigTest {

    private WebMvcConfig webMvcConfig;
    private CorsConfiguration corsConfig;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        webMvcConfig = new WebMvcConfig();

        CorsRegistry corsRegistry = new CorsRegistry();
        webMvcConfig.addCorsMappings(corsRegistry);

        Map<String, CorsConfiguration> configs =
                (Map<String, CorsConfiguration>) ReflectionTestUtils.invokeMethod(corsRegistry, "getCorsConfigurations");
        corsConfig = configs.get("/**");
    }

    // --- CORS Tests ---

    @Test
    void addCorsMappings_MapsAllPaths() {
        assertNotNull(corsConfig, "Expected CORS configuration for '/**' path mapping");
    }

    @Test
    void addCorsMappings_HasCorrectAllowedOriginPatterns() {
        List<String> patterns = corsConfig.getAllowedOriginPatterns();
        assertNotNull(patterns);
        assertTrue(patterns.contains("http://localhost:50030"));
        assertTrue(patterns.contains("http://localhost:3000"));
        assertTrue(patterns.contains("https://care-connect-develop.d26kqsucj1bwc1.amplifyapp.com"));
        assertTrue(patterns.contains("https://isabel-santiagolewis.github.io"));
        assertEquals(4, patterns.size());
    }

    @Test
    void addCorsMappings_AllowsCorrectMethods() {
        List<String> methods = corsConfig.getAllowedMethods();
        assertNotNull(methods);
        assertTrue(methods.containsAll(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS")));
        assertEquals(5, methods.size());
    }

    @Test
    void addCorsMappings_AllowsAllHeaders() {
        assertEquals(List.of("*"), corsConfig.getAllowedHeaders());
    }

    @Test
    void addCorsMappings_AllowsCredentials() {
        assertTrue(corsConfig.getAllowCredentials());
    }

    // --- Resource Handler Tests ---

    @SuppressWarnings("unchecked")
    private List<ResourceHandlerRegistration> getRegistrations() {
        ResourceHandlerRegistry registry = new ResourceHandlerRegistry(
                mock(ApplicationContext.class), mock(ServletContext.class));
        webMvcConfig.addResourceHandlers(registry);
        return (List<ResourceHandlerRegistration>) ReflectionTestUtils.getField(registry, "registrations");
    }

    @Test
    void addResourceHandlers_RegistersOneHandler() {
        List<ResourceHandlerRegistration> registrations = getRegistrations();
        assertNotNull(registrations);
        assertEquals(1, registrations.size());
    }

    @Test
    void addResourceHandlers_RegistersUploadsPattern() {
        List<ResourceHandlerRegistration> registrations = getRegistrations();
        String[] patterns = (String[]) ReflectionTestUtils.getField(registrations.get(0), "pathPatterns");
        assertNotNull(patterns);
        assertEquals(1, patterns.length);
        assertEquals("/uploads/**", patterns[0]);
    }

    @Test
    @SuppressWarnings("unchecked")
    void addResourceHandlers_RegistersCorrectLocation() {
        List<ResourceHandlerRegistration> registrations = getRegistrations();
        List<String> locationValues =
                (List<String>) ReflectionTestUtils.getField(registrations.get(0), "locationValues");
        assertNotNull(locationValues);
        assertEquals(1, locationValues.size());
        assertEquals("file:C:/Users/bompl/Documents/uploads/", locationValues.get(0));
    }
}
