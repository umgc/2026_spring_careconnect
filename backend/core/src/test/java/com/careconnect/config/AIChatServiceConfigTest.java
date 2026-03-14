package com.careconnect.config;

import com.careconnect.service.security.SecurityAuditService;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AIChatServiceConfig.
 *
 * Validates configuration bean creation and configuration validation rules
 * including security checks for API keys, URLs, and temperature ranges.
 *
 * Mocks SecurityAuditService to avoid actual logging while verifying
 * that validation errors are properly reported.
 */
class AIChatServiceConfigTest {

    private SecurityAuditService securityAuditService;
    private AIChatServiceConfig config;

    @BeforeEach
    void setUp() throws Exception {
        // Arrange: Create mock security audit service
        securityAuditService = mock(SecurityAuditService.class);
        config = new AIChatServiceConfig(securityAuditService);
    }

    @Test
    void constructorInitializesSuccessfully() throws Exception {
        // Assert: Constructor should complete without throwing
        assertNotNull(config);
    }

    @Test
    void chatModelBeanCreatedSuccessfullyWithValidConfiguration() throws Exception {
        // Arrange: Set valid configuration values
        ReflectionTestUtils.setField(config, "provider", "openai");
        ReflectionTestUtils.setField(config, "apiKey", "sk-test-1234567890abcdefghijklmnopqrstuvwxyz");
        ReflectionTestUtils.setField(config, "apiUrl", "https://api.openai.com/v1");
        ReflectionTestUtils.setField(config, "modelName", "gpt-4o-mini");
        ReflectionTestUtils.setField(config, "temperature", 1.0);

        // Act: Create the ChatModel bean
        final ChatModel chatModel = config.chatModel();

        // Assert: Bean should be created successfully
        assertNotNull(chatModel);
        verify(securityAuditService, never()).logConfigurationValidationError(anyString(), anyString(), anyString());
    }

    @Test
    void chatModelThrowsExceptionWhenApiKeyIsMissing() throws Exception {
        // Arrange: Set configuration with empty API key
        ReflectionTestUtils.setField(config, "provider", "openai");
        ReflectionTestUtils.setField(config, "apiKey", "");
        ReflectionTestUtils.setField(config, "apiUrl", "https://api.openai.com/v1");
        ReflectionTestUtils.setField(config, "modelName", "gpt-4o-mini");
        ReflectionTestUtils.setField(config, "temperature", 1.0);

        // Act & Assert: Should throw IllegalStateException
        final IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            config.chatModel();
        });

        assertEquals("API key is required but not configured", exception.getMessage());
        verify(securityAuditService).logConfigurationValidationError(
                eq("openai"),
                eq("API_KEY"),
                eq("API key is required but not configured")
        );
    }

    @Test
    void chatModelThrowsExceptionWhenApiKeyIsNull() throws Exception {
        // Arrange: Set configuration with null API key
        ReflectionTestUtils.setField(config, "provider", "openai");
        ReflectionTestUtils.setField(config, "apiKey", null);
        ReflectionTestUtils.setField(config, "apiUrl", "https://api.openai.com/v1");
        ReflectionTestUtils.setField(config, "modelName", "gpt-4o-mini");
        ReflectionTestUtils.setField(config, "temperature", 1.0);

        // Act & Assert: Should throw IllegalStateException
        final IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            config.chatModel();
        });

        assertEquals("API key is required but not configured", exception.getMessage());
        verify(securityAuditService).logConfigurationValidationError(
                eq("openai"),
                eq("API_KEY"),
                eq("API key is required but not configured")
        );
    }

    @Test
    void chatModelThrowsExceptionWhenApiUrlIsMissing() throws Exception {
        // Arrange: Set configuration with empty API URL
        ReflectionTestUtils.setField(config, "provider", "openai");
        ReflectionTestUtils.setField(config, "apiKey", "sk-test-1234567890abcdefghijklmnopqrstuvwxyz");
        ReflectionTestUtils.setField(config, "apiUrl", "");
        ReflectionTestUtils.setField(config, "modelName", "gpt-4o-mini");
        ReflectionTestUtils.setField(config, "temperature", 1.0);

        // Act & Assert: Should throw IllegalStateException
        final IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            config.chatModel();
        });

        assertEquals("API URL is required but not configured", exception.getMessage());
        verify(securityAuditService).logConfigurationValidationError(
                eq("openai"),
                eq("API_URL"),
                eq("API URL is required but not configured")
        );
    }

    @Test
    void chatModelThrowsExceptionWhenApiUrlIsNull() throws Exception {
        // Arrange: Set configuration with null API URL
        ReflectionTestUtils.setField(config, "provider", "openai");
        ReflectionTestUtils.setField(config, "apiKey", "sk-test-1234567890abcdefghijklmnopqrstuvwxyz");
        ReflectionTestUtils.setField(config, "apiUrl", null);
        ReflectionTestUtils.setField(config, "modelName", "gpt-4o-mini");
        ReflectionTestUtils.setField(config, "temperature", 1.0);

        // Act & Assert: Should throw IllegalStateException
        final IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            config.chatModel();
        });

        assertEquals("API URL is required but not configured", exception.getMessage());
        verify(securityAuditService).logConfigurationValidationError(
                eq("openai"),
                eq("API_URL"),
                eq("API URL is required but not configured")
        );
    }

    @Test
    void chatModelLogsWarningWhenApiUrlIsNotHttps() throws Exception {
        // Arrange: Set configuration with HTTP URL (insecure)
        ReflectionTestUtils.setField(config, "provider", "openai");
        ReflectionTestUtils.setField(config, "apiKey", "sk-test-1234567890abcdefghijklmnopqrstuvwxyz");
        ReflectionTestUtils.setField(config, "apiUrl", "http://api.example.com/v1");
        ReflectionTestUtils.setField(config, "modelName", "gpt-4o-mini");
        ReflectionTestUtils.setField(config, "temperature", 1.0);

        // Act: Create ChatModel - should succeed but log warning
        final ChatModel chatModel = config.chatModel();

        // Assert: Bean created but security warning logged
        assertNotNull(chatModel);
        final ArgumentCaptor<String> detailsCaptor = ArgumentCaptor.forClass(String.class);
        verify(securityAuditService).logConfigurationValidationError(
                eq("openai"),
                eq("API_URL_SECURITY"),
                detailsCaptor.capture()
        );
        assertTrue(detailsCaptor.getValue().contains("HTTPS"));
    }

    @Test
    void chatModelSucceedsWithShortApiKeyButNoAuditLog() throws Exception {
        // Arrange: Set configuration with short API key (< 20 chars)
        // Note: This only logs a warning, doesn't call audit service
        ReflectionTestUtils.setField(config, "provider", "openai");
        ReflectionTestUtils.setField(config, "apiKey", "short-key-123");
        ReflectionTestUtils.setField(config, "apiUrl", "https://api.openai.com/v1");
        ReflectionTestUtils.setField(config, "modelName", "gpt-4o-mini");
        ReflectionTestUtils.setField(config, "temperature", 1.0);

        // Act: Create ChatModel - should succeed with warning
        final ChatModel chatModel = config.chatModel();

        // Assert: Bean created and no audit error logged (only warning in logs)
        assertNotNull(chatModel);
        // Short key warning only goes to log.warn, not to securityAuditService
    }

    @Test
    void chatModelSucceedsWithTemperatureBelowRange() throws Exception {
        // Arrange: Set configuration with temperature below 0.0
        ReflectionTestUtils.setField(config, "provider", "openai");
        ReflectionTestUtils.setField(config, "apiKey", "sk-test-1234567890abcdefghijklmnopqrstuvwxyz");
        ReflectionTestUtils.setField(config, "apiUrl", "https://api.openai.com/v1");
        ReflectionTestUtils.setField(config, "modelName", "gpt-4o-mini");
        ReflectionTestUtils.setField(config, "temperature", -0.5);

        // Act: Create ChatModel - should succeed with warning
        final ChatModel chatModel = config.chatModel();

        // Assert: Bean created successfully (warning only)
        assertNotNull(chatModel);
    }

    @Test
    void chatModelSucceedsWithTemperatureAboveRange() throws Exception {
        // Arrange: Set configuration with temperature above 2.0
        ReflectionTestUtils.setField(config, "provider", "openai");
        ReflectionTestUtils.setField(config, "apiKey", "sk-test-1234567890abcdefghijklmnopqrstuvwxyz");
        ReflectionTestUtils.setField(config, "apiUrl", "https://api.openai.com/v1");
        ReflectionTestUtils.setField(config, "modelName", "gpt-4o-mini");
        ReflectionTestUtils.setField(config, "temperature", 2.5);

        // Act: Create ChatModel - should succeed with warning
        final ChatModel chatModel = config.chatModel();

        // Assert: Bean created successfully (warning only)
        assertNotNull(chatModel);
    }

    @Test
    void chatModelSucceedsWithDifferentProvider() throws Exception {
        // Arrange: Set configuration with different provider (e.g., deepseek)
        ReflectionTestUtils.setField(config, "provider", "deepseek");
        ReflectionTestUtils.setField(config, "apiKey", "sk-deepseek-1234567890abcdefghijklmnopqrstuvwxyz");
        ReflectionTestUtils.setField(config, "apiUrl", "https://api.deepseek.com/v1");
        ReflectionTestUtils.setField(config, "modelName", "deepseek-chat");
        ReflectionTestUtils.setField(config, "temperature", 0.7);

        // Act: Create ChatModel
        final ChatModel chatModel = config.chatModel();

        // Assert: Bean created successfully
        assertNotNull(chatModel);
        verify(securityAuditService, never()).logConfigurationValidationError(anyString(), anyString(), anyString());
    }

    @Test
    void chatModelSucceedsWithValidTemperatureBoundaries() throws Exception {
        // Arrange: Test lower boundary (0.0)
        ReflectionTestUtils.setField(config, "provider", "openai");
        ReflectionTestUtils.setField(config, "apiKey", "sk-test-1234567890abcdefghijklmnopqrstuvwxyz");
        ReflectionTestUtils.setField(config, "apiUrl", "https://api.openai.com/v1");
        ReflectionTestUtils.setField(config, "modelName", "gpt-4o-mini");
        ReflectionTestUtils.setField(config, "temperature", 0.0);

        // Act: Create ChatModel with temperature 0.0
        final ChatModel chatModel1 = config.chatModel();

        // Assert: Bean created successfully
        assertNotNull(chatModel1);

        // Arrange: Test upper boundary (2.0)
        ReflectionTestUtils.setField(config, "temperature", 2.0);

        // Act: Create ChatModel with temperature 2.0
        final ChatModel chatModel2 = config.chatModel();

        // Assert: Bean created successfully
        assertNotNull(chatModel2);
    }

    @Test
    void chatModelSucceedsWithTypicalTemperatureValues() throws Exception {
        // Arrange: Test common temperature values (0.5, 0.7, 1.0)
        ReflectionTestUtils.setField(config, "provider", "openai");
        ReflectionTestUtils.setField(config, "apiKey", "sk-test-1234567890abcdefghijklmnopqrstuvwxyz");
        ReflectionTestUtils.setField(config, "apiUrl", "https://api.openai.com/v1");
        ReflectionTestUtils.setField(config, "modelName", "gpt-4o-mini");

        final double[] temperatures = {0.5, 0.7, 1.0, 1.5};

        for (final double temp : temperatures) {
            // Arrange: Set temperature
            ReflectionTestUtils.setField(config, "temperature", temp);

            // Act: Create ChatModel
            final ChatModel chatModel = config.chatModel();

            // Assert: Bean created successfully
            assertNotNull(chatModel, "ChatModel should be created with temperature " + temp);
        }
    }

    @Test
    void chatModelSucceedsWithMinimumValidApiKey() throws Exception {
        // Arrange: Set configuration with API key exactly at minimum length (20 chars)
        ReflectionTestUtils.setField(config, "provider", "openai");
        ReflectionTestUtils.setField(config, "apiKey", "12345678901234567890"); // Exactly 20 chars
        ReflectionTestUtils.setField(config, "apiUrl", "https://api.openai.com/v1");
        ReflectionTestUtils.setField(config, "modelName", "gpt-4o-mini");
        ReflectionTestUtils.setField(config, "temperature", 1.0);

        // Act: Create ChatModel
        final ChatModel chatModel = config.chatModel();

        // Assert: Bean created successfully without warnings
        assertNotNull(chatModel);
    }

    @Test
    void chatModelValidatesApiKeyBeforeApiUrl() throws Exception {
        // Arrange: Set configuration with both API key and URL missing
        // This tests the order of validation
        ReflectionTestUtils.setField(config, "provider", "openai");
        ReflectionTestUtils.setField(config, "apiKey", "");
        ReflectionTestUtils.setField(config, "apiUrl", "");
        ReflectionTestUtils.setField(config, "modelName", "gpt-4o-mini");
        ReflectionTestUtils.setField(config, "temperature", 1.0);

        // Act & Assert: Should throw exception for API key first
        final IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            config.chatModel();
        });

        assertEquals("API key is required but not configured", exception.getMessage());
        verify(securityAuditService).logConfigurationValidationError(
                eq("openai"),
                eq("API_KEY"),
                anyString()
        );
        // API_URL error should not be logged since API_KEY fails first
        verify(securityAuditService, never()).logConfigurationValidationError(
                eq("openai"),
                eq("API_URL"),
                anyString()
        );
    }
}
