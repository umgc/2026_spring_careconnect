package com.careconnect.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;
import software.amazon.awssdk.services.ssm.model.Parameter;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

class SsmPropertySourceInitializerTest {

    private SsmPropertySourceInitializer initializer;
    private GenericApplicationContext context;
    private MockEnvironment environment;

    @BeforeEach
    void setup() {
        initializer = new SsmPropertySourceInitializer();
        context = new GenericApplicationContext();
        environment = new MockEnvironment();
        context.setEnvironment(environment);
    }

    // ==========================================
    // Should NOT initialize if not prod
    // ==========================================
    @Test
    void shouldNotInitializeIfNotProdProfile() {
        environment.setActiveProfiles("dev");

        initializer.initialize(context);

        assertNull(environment.getPropertySources().get("ssmPropertySource"));
    }

    // ==========================================
    // Should NOT initialize if AWS disabled
    // ==========================================
    @Test
    void shouldNotInitializeIfAwsDisabled() {
        environment.setActiveProfiles("prod");
        environment.setProperty("careconnect.aws.enabled", "false");

        initializer.initialize(context);

        assertNull(environment.getPropertySources().get("ssmPropertySource"));
    }

    // ==========================================
    // Test loadParametersFromSsm (via reflection)
    // ==========================================
    @Test
    void shouldLoadParametersFromSsm() throws Exception {

        // Mock SSM Client
        SsmClient ssmClient = Mockito.mock(SsmClient.class);

        Parameter mockParameter = Parameter.builder()
                .name("/careconnect/prod/stripe-secret-key")
                .value("test-secret")
                .build();

        GetParameterResponse response = GetParameterResponse.builder()
                .parameter(mockParameter)
                .build();

        Mockito.when(ssmClient.getParameter(any(GetParameterRequest.class)))
                .thenReturn(response);

        // Access private method using reflection
        Method method = SsmPropertySourceInitializer.class
                .getDeclaredMethod("loadParametersFromSsm", SsmClient.class);

        method.setAccessible(true);

        Map<String, Object> result =
                (Map<String, Object>) method.invoke(initializer, ssmClient);

        assertFalse(result.isEmpty());
        assertTrue(result.containsKey("stripe.secret-key"));
        assertEquals("test-secret", result.get("stripe.secret-key"));
    }

    // ==========================================
    //  Should Handle Missing Parameter 
    // ==========================================
    @Test
    void shouldHandleMissingParameterGracefully() throws Exception {

        SsmClient ssmClient = Mockito.mock(SsmClient.class);

        Mockito.when(ssmClient.getParameter(any(GetParameterRequest.class)))
                .thenThrow(new RuntimeException("Parameter not found"));

        Method method = SsmPropertySourceInitializer.class
                .getDeclaredMethod("loadParametersFromSsm", SsmClient.class);

        method.setAccessible(true);

        Map<String, Object> result =
                (Map<String, Object>) method.invoke(initializer, ssmClient);

        assertTrue(result.isEmpty());
    }
}
