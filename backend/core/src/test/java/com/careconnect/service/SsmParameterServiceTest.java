package com.careconnect.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;
import software.amazon.awssdk.services.ssm.model.Parameter;
import software.amazon.awssdk.services.ssm.model.ParameterNotFoundException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SsmParameterServiceTest {

    @Mock
    private SsmClient ssmClient;

    private SsmParameterService ssmParameterService;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        ssmParameterService = new SsmParameterService(ssmClient);
    }

    // ── getParameter(String, boolean) ──

    @Test
    @DisplayName("getParameter_parameterExists_returnsValue")
    void getParameter_parameterExists_returnsValue() throws Exception {
        final GetParameterResponse response = GetParameterResponse.builder()
                .parameter(Parameter.builder().value("secret-value").build())
                .build();
        when(ssmClient.getParameter(any(GetParameterRequest.class))).thenReturn(response);

        final String result = ssmParameterService.getParameter("/careconnect/prod/api-key", true);

        assertEquals("secret-value", result);

        final ArgumentCaptor<GetParameterRequest> captor = ArgumentCaptor.forClass(GetParameterRequest.class);
        verify(ssmClient).getParameter(captor.capture());
        assertEquals("/careconnect/prod/api-key", captor.getValue().name());
        assertTrue(captor.getValue().withDecryption());
    }

    @Test
    @DisplayName("getParameter_withoutDecryption_sendsWithDecryptionFalse")
    void getParameter_withoutDecryption_sendsWithDecryptionFalse() throws Exception {
        final GetParameterResponse response = GetParameterResponse.builder()
                .parameter(Parameter.builder().value("plain-value").build())
                .build();
        when(ssmClient.getParameter(any(GetParameterRequest.class))).thenReturn(response);

        final String result = ssmParameterService.getParameter("/careconnect/prod/setting", false);

        assertEquals("plain-value", result);

        final ArgumentCaptor<GetParameterRequest> captor = ArgumentCaptor.forClass(GetParameterRequest.class);
        verify(ssmClient).getParameter(captor.capture());
        assertFalse(captor.getValue().withDecryption());
    }

    @Test
    @DisplayName("getParameter_parameterNotFound_returnsNull")
    void getParameter_parameterNotFound_returnsNull() throws Exception {
        when(ssmClient.getParameter(any(GetParameterRequest.class)))
                .thenThrow(ParameterNotFoundException.builder().message("Not found").build());

        final String result = ssmParameterService.getParameter("/careconnect/prod/missing-key", true);

        assertNull(result);
    }

    @Test
    @DisplayName("getParameter_generalException_returnsNull")
    void getParameter_generalException_returnsNull() throws Exception {
        when(ssmClient.getParameter(any(GetParameterRequest.class)))
                .thenThrow(new RuntimeException("Connection error"));

        final String result = ssmParameterService.getParameter("/careconnect/prod/api-key", true);

        assertNull(result);
    }

    @Test
    @DisplayName("getParameter_cachedValue_returnsCachedWithoutCallingSSM")
    void getParameter_cachedValue_returnsCachedWithoutCallingSSM() throws Exception {
        final GetParameterResponse response = GetParameterResponse.builder()
                .parameter(Parameter.builder().value("cached-value").build())
                .build();
        when(ssmClient.getParameter(any(GetParameterRequest.class))).thenReturn(response);

        // First call - should go to SSM
        final String result1 = ssmParameterService.getParameter("/careconnect/prod/api-key", true);
        assertEquals("cached-value", result1);

        // Second call - should use cache
        final String result2 = ssmParameterService.getParameter("/careconnect/prod/api-key", true);
        assertEquals("cached-value", result2);

        // SSM should only be called once
        verify(ssmClient, times(1)).getParameter(any(GetParameterRequest.class));
    }

    @Test
    @DisplayName("getParameter_differentDecryptionFlags_cachedSeparately")
    void getParameter_differentDecryptionFlags_cachedSeparately() throws Exception {
        final GetParameterResponse response1 = GetParameterResponse.builder()
                .parameter(Parameter.builder().value("decrypted-value").build())
                .build();
        final GetParameterResponse response2 = GetParameterResponse.builder()
                .parameter(Parameter.builder().value("plain-value").build())
                .build();

        when(ssmClient.getParameter(any(GetParameterRequest.class)))
                .thenReturn(response1)
                .thenReturn(response2);

        // Call with decryption true
        final String result1 = ssmParameterService.getParameter("/param", true);
        assertEquals("decrypted-value", result1);

        // Call with decryption false - different cache key
        final String result2 = ssmParameterService.getParameter("/param", false);
        assertEquals("plain-value", result2);

        // SSM should be called twice (different cache keys)
        verify(ssmClient, times(2)).getParameter(any(GetParameterRequest.class));
    }

    // ── getParameter(String) ──

    @Test
    @DisplayName("getParameter_singleArgOverload_callsWithDecryptionTrue")
    void getParameter_singleArgOverload_callsWithDecryptionTrue() throws Exception {
        final GetParameterResponse response = GetParameterResponse.builder()
                .parameter(Parameter.builder().value("auto-decrypted").build())
                .build();
        when(ssmClient.getParameter(any(GetParameterRequest.class))).thenReturn(response);

        final String result = ssmParameterService.getParameter("/careconnect/prod/secret");

        assertEquals("auto-decrypted", result);

        final ArgumentCaptor<GetParameterRequest> captor = ArgumentCaptor.forClass(GetParameterRequest.class);
        verify(ssmClient).getParameter(captor.capture());
        assertTrue(captor.getValue().withDecryption());
    }

    @Test
    @DisplayName("getParameter_singleArgNotFound_returnsNull")
    void getParameter_singleArgNotFound_returnsNull() throws Exception {
        when(ssmClient.getParameter(any(GetParameterRequest.class)))
                .thenThrow(ParameterNotFoundException.builder().message("Not found").build());

        final String result = ssmParameterService.getParameter("/missing");

        assertNull(result);
    }

    // ── getParameterOrDefault ──

    @Test
    @DisplayName("getParameterOrDefault_parameterExists_returnsParameterValue")
    void getParameterOrDefault_parameterExists_returnsParameterValue() throws Exception {
        final GetParameterResponse response = GetParameterResponse.builder()
                .parameter(Parameter.builder().value("real-value").build())
                .build();
        when(ssmClient.getParameter(any(GetParameterRequest.class))).thenReturn(response);

        final String result = ssmParameterService.getParameterOrDefault("/param", "default-value");

        assertEquals("real-value", result);
    }

    @Test
    @DisplayName("getParameterOrDefault_parameterNotFound_returnsDefaultValue")
    void getParameterOrDefault_parameterNotFound_returnsDefaultValue() throws Exception {
        when(ssmClient.getParameter(any(GetParameterRequest.class)))
                .thenThrow(ParameterNotFoundException.builder().message("Not found").build());

        final String result = ssmParameterService.getParameterOrDefault("/missing-param", "fallback");

        assertEquals("fallback", result);
    }

    @Test
    @DisplayName("getParameterOrDefault_generalException_returnsDefaultValue")
    void getParameterOrDefault_generalException_returnsDefaultValue() throws Exception {
        when(ssmClient.getParameter(any(GetParameterRequest.class)))
                .thenThrow(new RuntimeException("Connection error"));

        final String result = ssmParameterService.getParameterOrDefault("/error-param", "safe-default");

        assertEquals("safe-default", result);
    }

    // ── clearCache ──

    @Test
    @DisplayName("clearCache_afterCaching_forcesNextCallToSSM")
    void clearCache_afterCaching_forcesNextCallToSSM() throws Exception {
        final GetParameterResponse response = GetParameterResponse.builder()
                .parameter(Parameter.builder().value("value1").build())
                .build();
        when(ssmClient.getParameter(any(GetParameterRequest.class))).thenReturn(response);

        // First call - goes to SSM
        ssmParameterService.getParameter("/param", true);

        // Clear cache
        ssmParameterService.clearCache();

        // Second call after cache clear - should go to SSM again
        final GetParameterResponse response2 = GetParameterResponse.builder()
                .parameter(Parameter.builder().value("value2").build())
                .build();
        when(ssmClient.getParameter(any(GetParameterRequest.class))).thenReturn(response2);

        final String result = ssmParameterService.getParameter("/param", true);
        assertEquals("value2", result);

        // SSM should be called twice (once before cache clear, once after)
        verify(ssmClient, times(2)).getParameter(any(GetParameterRequest.class));
    }

    @Test
    @DisplayName("clearCache_emptyCache_doesNotThrow")
    void clearCache_emptyCache_doesNotThrow() throws Exception {
        assertDoesNotThrow(() -> ssmParameterService.clearCache());
    }

    // ── Constructor ──

    @Test
    @DisplayName("constructor_validSsmClient_createsServiceSuccessfully")
    void constructor_validSsmClient_createsServiceSuccessfully() throws Exception {
        final SsmParameterService service = new SsmParameterService(ssmClient);
        assertNotNull(service);
    }
}
