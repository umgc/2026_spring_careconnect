package com.careconnect.config;

import com.careconnect.service.SsmParameterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SsmConfigTest {

    @Mock
    private SsmParameterService ssmParameterService;

    private SsmConfig ssmConfig;

    @BeforeEach
    void setUp() throws Exception {
        ssmConfig = new SsmConfig();
    }

    private void injectSsmService(SsmParameterService service) throws Exception {
        Field field = SsmConfig.class.getDeclaredField("ssmParameterService");
        field.setAccessible(true);
        field.set(ssmConfig, service);
    }

    // --- init() tests ---

    @Test
    void init_WithSsmServiceAvailable_LogsInitialized() throws Exception {
        injectSsmService(ssmParameterService);
        // Should not throw
        assertDoesNotThrow(() -> ssmConfig.init());
    }

    @Test
    void init_WithSsmServiceNull_LogsFallbackWarning() {
        // ssmParameterService is null by default (not injected)
        assertDoesNotThrow(() -> ssmConfig.init());
    }

    // --- Bean methods with SSM service available ---

    @Test
    void stripeSecretKey_WithSsmService_ReturnsValueFromSsm() throws Exception {
        injectSsmService(ssmParameterService);
        when(ssmParameterService.getParameterOrDefault(eq("/careconnect/prod/stripe-secret-key"), any()))
                .thenReturn("ssm-stripe-key");

        String result = ssmConfig.stripeSecretKey();

        assertEquals("ssm-stripe-key", result);
        verify(ssmParameterService).getParameterOrDefault(eq("/careconnect/prod/stripe-secret-key"), any());
    }

    @Test
    void stripeWebhookSecret_WithSsmService_ReturnsValueFromSsm() throws Exception {
        injectSsmService(ssmParameterService);
        when(ssmParameterService.getParameterOrDefault(eq("/careconnect/prod/stripe-webhook-secret"), any()))
                .thenReturn("ssm-webhook-secret");

        String result = ssmConfig.stripeWebhookSecret();

        assertEquals("ssm-webhook-secret", result);
    }

    @Test
    void openaiApiKey_WithSsmService_ReturnsValueFromSsm() throws Exception {
        injectSsmService(ssmParameterService);
        when(ssmParameterService.getParameterOrDefault(eq("/careconnect/prod/openai-api-key"), any()))
                .thenReturn("ssm-openai-key");

        String result = ssmConfig.openaiApiKey();

        assertEquals("ssm-openai-key", result);
    }

    @Test
    void deepseekApiKey_WithSsmService_ReturnsValueFromSsm() throws Exception {
        injectSsmService(ssmParameterService);
        when(ssmParameterService.getParameterOrDefault(eq("/careconnect/prod/deepseek-api-key"), any()))
                .thenReturn("ssm-deepseek-key");

        String result = ssmConfig.deepseekApiKey();

        assertEquals("ssm-deepseek-key", result);
    }

    @Test
    void jwtSecret_WithSsmService_ReturnsValueFromSsm() throws Exception {
        injectSsmService(ssmParameterService);
        when(ssmParameterService.getParameterOrDefault(eq("/careconnect/prod/jwt-secret"), any()))
                .thenReturn("ssm-jwt-secret");

        String result = ssmConfig.jwtSecret();

        assertEquals("ssm-jwt-secret", result);
    }

    @Test
    void sendgridApiKey_WithSsmService_ReturnsValueFromSsm() throws Exception {
        injectSsmService(ssmParameterService);
        when(ssmParameterService.getParameterOrDefault(eq("/careconnect/prod/sendgrid-api-key"), any()))
                .thenReturn("ssm-sendgrid-key");

        String result = ssmConfig.sendgridApiKey();

        assertEquals("ssm-sendgrid-key", result);
    }

    @Test
    void googleClientId_WithSsmService_ReturnsValueFromSsm() throws Exception {
        injectSsmService(ssmParameterService);
        when(ssmParameterService.getParameterOrDefault(eq("/careconnect/prod/google-client-id"), any()))
                .thenReturn("ssm-google-id");

        String result = ssmConfig.googleClientId();

        assertEquals("ssm-google-id", result);
    }

    @Test
    void googleClientSecret_WithSsmService_ReturnsValueFromSsm() throws Exception {
        injectSsmService(ssmParameterService);
        when(ssmParameterService.getParameterOrDefault(eq("/careconnect/prod/google-client-secret"), any()))
                .thenReturn("ssm-google-secret");

        String result = ssmConfig.googleClientSecret();

        assertEquals("ssm-google-secret", result);
    }

    @Test
    void fitbitClientId_WithSsmService_ReturnsValueFromSsm() throws Exception {
        injectSsmService(ssmParameterService);
        when(ssmParameterService.getParameterOrDefault(eq("/careconnect/prod/fitbit-client-id"), any()))
                .thenReturn("ssm-fitbit-id");

        String result = ssmConfig.fitbitClientId();

        assertEquals("ssm-fitbit-id", result);
    }

    @Test
    void fitbitClientSecret_WithSsmService_ReturnsValueFromSsm() throws Exception {
        injectSsmService(ssmParameterService);
        when(ssmParameterService.getParameterOrDefault(eq("/careconnect/prod/fitbit-client-secret"), any()))
                .thenReturn("ssm-fitbit-secret");

        String result = ssmConfig.fitbitClientSecret();

        assertEquals("ssm-fitbit-secret", result);
    }

    @Test
    void databasePassword_WithSsmService_ReturnsValueFromSsm() throws Exception {
        injectSsmService(ssmParameterService);
        when(ssmParameterService.getParameterOrDefault(eq("/careconnect/prod/db-password"), any()))
                .thenReturn("ssm-db-password");

        String result = ssmConfig.databasePassword();

        assertEquals("ssm-db-password", result);
    }

    // --- Bean methods without SSM service (fallback to env vars) ---

    @Test
    void stripeSecretKey_WithoutSsmService_ReturnsEnvFallback() {
        // ssmParameterService is null — should return env var value (likely null in test)
        String result = ssmConfig.stripeSecretKey();

        assertEquals(System.getenv("STRIPE_SECRET_KEY"), result);
    }

    @Test
    void jwtSecret_WithoutSsmService_ReturnsEnvFallback() {
        String result = ssmConfig.jwtSecret();

        assertEquals(System.getenv("SECURITY_JWT_SECRET"), result);
    }

    @Test
    void databasePassword_WithoutSsmService_ReturnsEnvFallback() {
        String result = ssmConfig.databasePassword();

        assertEquals(System.getenv("DB_PASSWORD"), result);
    }

    // --- getSsmParameter fallback behavior ---

    @Test
    void getSsmParameter_WhenSsmReturnsNull_FallsBackToEnvValue() throws Exception {
        injectSsmService(ssmParameterService);
        when(ssmParameterService.getParameterOrDefault(eq("/careconnect/prod/jwt-secret"), any()))
                .thenReturn(null);

        String result = ssmConfig.jwtSecret();

        assertNull(result);
    }

    @Test
    void getSsmParameter_WhenSsmReturnsSameAsEnvFallback_ReturnsThatValue() throws Exception {
        injectSsmService(ssmParameterService);
        String envFallback = System.getenv("STRIPE_SECRET_KEY");
        when(ssmParameterService.getParameterOrDefault(eq("/careconnect/prod/stripe-secret-key"), any()))
                .thenReturn(envFallback);

        String result = ssmConfig.stripeSecretKey();

        assertEquals(envFallback, result);
    }

    // --- Verify correct SSM parameter paths ---

    @Test
    void allBeans_UseCorrectSsmParameterPrefix() throws Exception {
        injectSsmService(ssmParameterService);
        when(ssmParameterService.getParameterOrDefault(anyString(), any())).thenReturn("value");

        ssmConfig.stripeSecretKey();
        ssmConfig.stripeWebhookSecret();
        ssmConfig.openaiApiKey();
        ssmConfig.deepseekApiKey();
        ssmConfig.jwtSecret();
        ssmConfig.sendgridApiKey();
        ssmConfig.googleClientId();
        ssmConfig.googleClientSecret();
        ssmConfig.fitbitClientId();
        ssmConfig.fitbitClientSecret();
        ssmConfig.databasePassword();

        verify(ssmParameterService).getParameterOrDefault(eq("/careconnect/prod/stripe-secret-key"), any());
        verify(ssmParameterService).getParameterOrDefault(eq("/careconnect/prod/stripe-webhook-secret"), any());
        verify(ssmParameterService).getParameterOrDefault(eq("/careconnect/prod/openai-api-key"), any());
        verify(ssmParameterService).getParameterOrDefault(eq("/careconnect/prod/deepseek-api-key"), any());
        verify(ssmParameterService).getParameterOrDefault(eq("/careconnect/prod/jwt-secret"), any());
        verify(ssmParameterService).getParameterOrDefault(eq("/careconnect/prod/sendgrid-api-key"), any());
        verify(ssmParameterService).getParameterOrDefault(eq("/careconnect/prod/google-client-id"), any());
        verify(ssmParameterService).getParameterOrDefault(eq("/careconnect/prod/google-client-secret"), any());
        verify(ssmParameterService).getParameterOrDefault(eq("/careconnect/prod/fitbit-client-id"), any());
        verify(ssmParameterService).getParameterOrDefault(eq("/careconnect/prod/fitbit-client-secret"), any());
        verify(ssmParameterService).getParameterOrDefault(eq("/careconnect/prod/db-password"), any());
    }
}
