package com.careconnect.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CorsFilterConfigTest {

    private CorsFilterConfig corsFilterConfig;

    @BeforeEach
    void setUp() {
        corsFilterConfig = new CorsFilterConfig();

        // Inject allowed origins manually (since @Value won't run)
        ReflectionTestUtils.setField(
                corsFilterConfig,
                "allowedOrigins",
                List.of("http://localhost:3000", "https://careconnect.com")
        );
    }

    @Test
    void corsConfigurationSource_IsCreated() {
        CorsConfigurationSource source = corsFilterConfig.corsFilter();
        assertNotNull(source);
        assertTrue(source instanceof UrlBasedCorsConfigurationSource);
    }

    @Test
    void corsConfiguration_HasCorrectAllowedOrigins() {
        CorsConfigurationSource source = corsFilterConfig.corsFilter();
        UrlBasedCorsConfigurationSource urlSource =
                (UrlBasedCorsConfigurationSource) source;

        CorsConfiguration config =
                urlSource.getCorsConfigurations().get("/**");

        assertNotNull(config);
        assertEquals(
                List.of("http://localhost:3000", "https://careconnect.com"),
                config.getAllowedOriginPatterns()
        );
    }

    @Test
    void corsConfiguration_AllowsCredentials() {
        UrlBasedCorsConfigurationSource source =
                (UrlBasedCorsConfigurationSource) corsFilterConfig.corsFilter();

        CorsConfiguration config =
                source.getCorsConfigurations().get("/**");

        assertTrue(config.getAllowCredentials());
    }

    @Test
    void corsConfiguration_AllowsAllHeaders() {
        UrlBasedCorsConfigurationSource source =
                (UrlBasedCorsConfigurationSource) corsFilterConfig.corsFilter();

        CorsConfiguration config =
                source.getCorsConfigurations().get("/**");

        assertEquals(List.of("*"), config.getAllowedHeaders());
        assertEquals(List.of("*"), config.getExposedHeaders());
    }

    @Test
    void corsConfiguration_HasCorrectAllowedMethods() {
        UrlBasedCorsConfigurationSource source =
                (UrlBasedCorsConfigurationSource) corsFilterConfig.corsFilter();

        CorsConfiguration config =
                source.getCorsConfigurations().get("/**");

        assertEquals(
                List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"),
                config.getAllowedMethods()
        );
    }

    @Test
    void corsConfiguration_IsRegisteredForAllPaths() {
        UrlBasedCorsConfigurationSource source =
                (UrlBasedCorsConfigurationSource) corsFilterConfig.corsFilter();

        assertTrue(source.getCorsConfigurations().containsKey("/**"));
    }
}
