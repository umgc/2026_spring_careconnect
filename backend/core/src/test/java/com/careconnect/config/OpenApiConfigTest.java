package com.careconnect.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.annotations.info.Info;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class OpenApiConfigTest {

    @Test
    void classHasConfigurationAnnotation() {
        assertTrue(OpenApiConfig.class.isAnnotationPresent(Configuration.class));
    }

    @Test
    void openApiDefinitionAnnotationExists() {
        assertTrue(OpenApiConfig.class.isAnnotationPresent(OpenAPIDefinition.class));
    }

    @Test
    void openApiDefinitionContainsCorrectMetadata() {
        OpenAPIDefinition definition =
                OpenApiConfig.class.getAnnotation(OpenAPIDefinition.class);

        Info info = definition.info();

        assertEquals("CareConnect Backend API", info.title());
        assertEquals("1.0.0", info.version());
        assertTrue(info.description().contains("CareConnect Backend API provides"));

        assertEquals("CareConnect Development Team", info.contact().name());
        assertEquals("support@careconnect.com", info.contact().email());
        assertEquals("https://careconnect.com", info.contact().url());

        assertEquals("MIT License", info.license().name());
        assertEquals("https://opensource.org/licenses/MIT", info.license().url());
    }

    @Test
    void openApiDefinitionContainsServers() {
        OpenAPIDefinition definition =
                OpenApiConfig.class.getAnnotation(OpenAPIDefinition.class);

        Server[] servers = definition.servers();

        assertEquals(2, servers.length);

        assertTrue(Arrays.stream(servers)
                .anyMatch(s -> s.url().equals("http://localhost:8080")));

        assertTrue(Arrays.stream(servers)
                .anyMatch(s -> s.url().equals("https://api.careconnect.com")));
    }

    @Test
    void openApiDefinitionContainsSecurityRequirements() {
        OpenAPIDefinition definition =
                OpenApiConfig.class.getAnnotation(OpenAPIDefinition.class);

        SecurityRequirement[] security = definition.security();

        assertEquals(3, security.length);

        assertTrue(Arrays.stream(security)
                .anyMatch(s -> s.name().equals("JWT Authentication")));

        assertTrue(Arrays.stream(security)
                .anyMatch(s -> s.name().equals("Basic Authentication")));

        assertTrue(Arrays.stream(security)
                .anyMatch(s -> s.name().equals("Cookie Authentication")));
    }

    @Test
    void securitySchemesAreDefined() {
        SecurityScheme[] schemes =
                OpenApiConfig.class.getAnnotationsByType(SecurityScheme.class);

        assertEquals(3, schemes.length);

        assertTrue(Arrays.stream(schemes)
                .anyMatch(s -> s.name().equals("JWT Authentication")));

        assertTrue(Arrays.stream(schemes)
                .anyMatch(s -> s.name().equals("Basic Authentication")));

        assertTrue(Arrays.stream(schemes)
                .anyMatch(s -> s.name().equals("Cookie Authentication")));
    }
}
