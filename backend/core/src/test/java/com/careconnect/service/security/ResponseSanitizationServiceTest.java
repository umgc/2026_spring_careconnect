package com.careconnect.service.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ResponseSanitizationServiceTest {

    @Mock
    private SecurityAuditService securityAuditService;

    @InjectMocks
    private ResponseSanitizationService responseSanitizationService;

    @Test
    void sanitizeAIResponse_null_returnsEmpty() throws Exception {
        final ResponseSanitizationService.SanitizationResult result =
                responseSanitizationService.sanitizeAIResponse(null, 1L, "conv-1", null);
        assertThat(result.getSanitizedContent()).isEmpty();
        assertThat(result.getSanitizedItems()).isEmpty();
    }

    @Test
    void sanitizeAIResponse_blank_returnsEmpty() throws Exception {
        final ResponseSanitizationService.SanitizationResult result =
                responseSanitizationService.sanitizeAIResponse("  ", 1L, "conv-1", null);
        assertThat(result.getSanitizedContent()).isEmpty();
        assertThat(result.getSanitizedItems()).isEmpty();
    }

    @Test
    void sanitizeAIResponse_cleanResponse_returnsUnmodified() throws Exception {
        final String response = "Your appointment is scheduled for tomorrow at 10 AM.";
        final ResponseSanitizationService.SanitizationResult result =
                responseSanitizationService.sanitizeAIResponse(response, 1L, "conv-1", null);
        assertThat(result.getSanitizedContent()).isEqualTo(response.trim());
        assertThat(result.getSanitizedItems()).isEmpty();
    }

    @Test
    void sanitizeAIResponse_withSystemInfo_sanitizesAndLogs() throws Exception {
        final String response = "The api key: abc123 is in the configuration.";
        final ResponseSanitizationService.SanitizationResult result =
                responseSanitizationService.sanitizeAIResponse(response, 2L, "conv-2", null);
        assertThat(result.getSanitizedItems()).contains("System information");
        assertThat(result.getSanitizedContent()).contains("[Sensitive information removed]");
        verify(securityAuditService).logSanitizationAction(2L, "conv-2",
                "RESPONSE_SYSTEM_INFO_REMOVED", "Removed system information from AI response");
    }

    @Test
    void sanitizeAIResponse_withLocalhostRef_sanitizes() throws Exception {
        final String response = "The server is running at localhost for development.";
        final ResponseSanitizationService.SanitizationResult result =
                responseSanitizationService.sanitizeAIResponse(response, 3L, "conv-3", null);
        assertThat(result.getSanitizedItems()).contains("System information");
        assertThat(result.getSanitizedContent()).contains("[Server reference removed]");
    }

    @Test
    void sanitizeAIResponse_withSensitiveData_sanitizesAndLogs() throws Exception {
        final String response = "Your password: secretXYZ is here.";
        final ResponseSanitizationService.SanitizationResult result =
                responseSanitizationService.sanitizeAIResponse(response, 4L, "conv-4", null);
        assertThat(result.getSanitizedItems()).contains("Sensitive personal data");
        assertThat(result.getSanitizedContent()).contains("[Sensitive data removed]");
        verify(securityAuditService).logSanitizationAction(4L, "conv-4",
                "RESPONSE_SENSITIVE_DATA_REMOVED", "Removed sensitive data from AI response");
    }

    @Test
    void sanitizeAIResponse_withSsn_replacesNumber() throws Exception {
        final String response = "The patient SSN is 123-45-6789 on file.";
        final ResponseSanitizationService.SanitizationResult result =
                responseSanitizationService.sanitizeAIResponse(response, 5L, "conv-5", null);
        assertThat(result.getSanitizedItems()).contains("Sensitive personal data");
        assertThat(result.getSanitizedContent()).contains("[SSN removed]");
    }

    @Test
    void sanitizeAIResponse_withCreditCard_replacesNumber() throws Exception {
        final String response = "Credit card 4111111111111111 was processed.";
        final ResponseSanitizationService.SanitizationResult result =
                responseSanitizationService.sanitizeAIResponse(response, 6L, "conv-6", null);
        assertThat(result.getSanitizedItems()).contains("Sensitive personal data");
        assertThat(result.getSanitizedContent()).contains("[Credit card number removed]");
    }

    @Test
    void sanitizeAIResponse_withPatientId_allowsMedicalContext() throws Exception {
        final String response = "Patient has been prescribed medication for hypertension.";
        final ResponseSanitizationService.SanitizationResult result =
                responseSanitizationService.sanitizeAIResponse(response, 7L, "conv-7", 100L);
        // Medical info allowed when patientId is present
        assertThat(result.getSanitizedContent()).isEqualTo(response.trim());
        assertThat(result.getSanitizedItems()).isEmpty();
    }

    @Test
    void sanitizeAIResponse_withoutPatientId_restrictsMedicalInfo() throws Exception {
        final String response = "The diagnosis: hypertension is confirmed.";
        final ResponseSanitizationService.SanitizationResult result =
                responseSanitizationService.sanitizeAIResponse(response, 8L, "conv-8", null);
        assertThat(result.getSanitizedContent()).contains("[Medical information restricted]");
        assertThat(result.getSanitizedItems()).contains("Medical information without patient context");
    }

    @Test
    void sanitizeAIResponse_scriptContent_removed() throws Exception {
        final String response = "Click here <script>alert('xss')</script> to proceed.";
        final ResponseSanitizationService.SanitizationResult result =
                responseSanitizationService.sanitizeAIResponse(response, 9L, "conv-9", null);
        assertThat(result.getSanitizedContent()).contains("[Script content removed]");
    }

    // ----- SanitizationResult inner class -----

    @Test
    void sanitizationResult_getters() throws Exception {
        final ResponseSanitizationService.SanitizationResult result =
                new ResponseSanitizationService.SanitizationResult("content", List.of("item1"));
        assertThat(result.getSanitizedContent()).isEqualTo("content");
        assertThat(result.getSanitizedItems()).containsExactly("item1");
    }

    @Test
    void sanitizationResult_emptyItems() throws Exception {
        final ResponseSanitizationService.SanitizationResult result =
                new ResponseSanitizationService.SanitizationResult("clean response", List.of());
        assertThat(result.getSanitizedItems()).isEmpty();
    }
}
