package com.careconnect.service.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;

@ExtendWith(MockitoExtension.class)
class SecurityAuditServiceTest {

    @InjectMocks
    private SecurityAuditService securityAuditService;

    @Test
    void logSanitizationAction_doesNotThrow() throws Exception {
        assertThatCode(() -> securityAuditService.logSanitizationAction(
                1L, "conv-1", "INPUT_SANITIZED", "Removed script tags"))
                .doesNotThrowAnyException();
    }

    @Test
    void logSanitizationAction_withNullArgs_doesNotThrow() throws Exception {
        assertThatCode(() -> securityAuditService.logSanitizationAction(null, null, null, null))
                .doesNotThrowAnyException();
    }

    @Test
    void logSecurityViolation_doesNotThrow() throws Exception {
        assertThatCode(() -> securityAuditService.logSecurityViolation(
                2L, "conv-2", "SQL_INJECTION_ATTEMPT", "25 chars"))
                .doesNotThrowAnyException();
    }

    @Test
    void logSecurityViolation_withNullArgs_doesNotThrow() throws Exception {
        assertThatCode(() -> securityAuditService.logSecurityViolation(null, null, null, null))
                .doesNotThrowAnyException();
    }

    @Test
    void logGovernanceAction_doesNotThrow() throws Exception {
        assertThatCode(() -> securityAuditService.logGovernanceAction(
                3L, "conv-3", "REQUEST_VALIDATED", "Message length: 42"))
                .doesNotThrowAnyException();
    }

    @Test
    void logGovernanceAction_withNullArgs_doesNotThrow() throws Exception {
        assertThatCode(() -> securityAuditService.logGovernanceAction(null, null, null, null))
                .doesNotThrowAnyException();
    }

    @Test
    void logConfigurationValidationError_doesNotThrow() throws Exception {
        assertThatCode(() -> securityAuditService.logConfigurationValidationError(
                "AI-Service", "API_KEY", "Missing key"))
                .doesNotThrowAnyException();
    }

    @Test
    void logConfigurationValidationError_withNullArgs_doesNotThrow() throws Exception {
        assertThatCode(() -> securityAuditService.logConfigurationValidationError(null, null, null))
                .doesNotThrowAnyException();
    }
}
