package com.careconnect.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link EmailTestService}.
 *
 * <p>Covers all branches including provider-specific configuration checks,
 * console vs SMTP test email sending, validation logic, and all email type tests.
 */
class EmailTestServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private EmailTestService emailTestService;

    private MimeMessage mimeMessage;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        // Set default @Value fields
        ReflectionTestUtils.setField(emailTestService, "emailProvider", "console");
        ReflectionTestUtils.setField(emailTestService, "fromEmail", "noreply@careconnect.local");
        ReflectionTestUtils.setField(emailTestService, "mailHost", "smtp.mailtrap.io");
        ReflectionTestUtils.setField(emailTestService, "mailPort", "587");
        ReflectionTestUtils.setField(emailTestService, "mailUsername", "testuser");
        ReflectionTestUtils.setField(emailTestService, "sendgridApiKey", "");
        ReflectionTestUtils.setField(emailTestService, "resendApiKey", "");
        ReflectionTestUtils.setField(emailTestService, "mailgunApiKey", "");
        ReflectionTestUtils.setField(emailTestService, "mailgunDomain", "");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // testEmailConfiguration
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("testEmailConfiguration")
    class TestEmailConfiguration {

        @Test
        @DisplayName("testEmailConfiguration_consoleProvider_returnsSuccessResult")
        void testEmailConfiguration_consoleProvider_returnsSuccessResult() throws Exception {
            final Map<String, Object> result = emailTestService.testEmailConfiguration("test@example.com");

            assertThat(result.get("testEmail")).isEqualTo("test@example.com");
            assertThat(result.get("emailProvider")).isEqualTo("console");
            assertThat(result.get("fromEmail")).isEqualTo("noreply@careconnect.local");
            assertThat(result.get("success")).isEqualTo(true);
            assertThat(result.get("message")).isEqualTo("Test email sent successfully");
            assertThat(result.get("timestamp")).isNotNull();
            assertThat(result.get("configuration")).isNotNull();
        }

        @Test
        @DisplayName("testEmailConfiguration_smtpProviderSuccess_returnsSuccessResult")
        void testEmailConfiguration_smtpProviderSuccess_returnsSuccessResult() throws Exception {
            ReflectionTestUtils.setField(emailTestService, "emailProvider", "smtp");

            final Map<String, Object> result = emailTestService.testEmailConfiguration("test@example.com");

            assertThat(result.get("success")).isEqualTo(true);
            assertThat(result.get("message")).isEqualTo("Test email sent successfully");
        }

        @Test
        @DisplayName("testEmailConfiguration_smtpProviderThrowsMessagingException_returnsFailureResult")
        void testEmailConfiguration_smtpProviderThrowsMessagingException_returnsFailureResult() throws Exception {
            ReflectionTestUtils.setField(emailTestService, "emailProvider", "smtp");
            doThrow(new RuntimeException("SMTP connection refused"))
                    .when(mailSender).send(any(MimeMessage.class));

            final Map<String, Object> result = emailTestService.testEmailConfiguration("test@example.com");

            assertThat(result.get("success")).isEqualTo(false);
            assertThat((String) result.get("message")).contains("Test email failed");
            assertThat(result.get("error")).isNotNull();
        }

        @Test
        @DisplayName("testEmailConfiguration_nullMailSenderConsole_returnsSuccess")
        void testEmailConfiguration_nullMailSenderConsole_returnsSuccess() throws Exception {
            ReflectionTestUtils.setField(emailTestService, "mailSender", null);

            final Map<String, Object> result = emailTestService.testEmailConfiguration("test@example.com");

            assertThat(result.get("success")).isEqualTo(true);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // getEmailConfiguration
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getEmailConfiguration")
    class GetEmailConfiguration {

        @Test
        @DisplayName("getEmailConfiguration_consoleProvider_returnsValidConfig")
        void getEmailConfiguration_consoleProvider_returnsValidConfig() throws Exception {
            final Map<String, Object> config = emailTestService.getEmailConfiguration();

            assertThat(config.get("provider")).isEqualTo("console");
            assertThat(config.get("fromEmail")).isEqualTo("noreply@careconnect.local");
            assertThat(config.get("mailHost")).isEqualTo("smtp.mailtrap.io");
            assertThat(config.get("mailPort")).isEqualTo("587");
            assertThat(config.get("mailUsername")).isEqualTo("testuser");
            assertThat(config.get("mailSenderAvailable")).isEqualTo(true);
            assertThat(config.get("configurationValid")).isEqualTo(true);
        }

        @Test
        @DisplayName("getEmailConfiguration_sendgridProviderWithKey_returnsConfigured")
        void getEmailConfiguration_sendgridProviderWithKey_returnsConfigured() throws Exception {
            ReflectionTestUtils.setField(emailTestService, "emailProvider", "sendgrid");
            ReflectionTestUtils.setField(emailTestService, "sendgridApiKey", "SG.testkey");

            final Map<String, Object> config = emailTestService.getEmailConfiguration();

            assertThat(config.get("configurationValid")).isEqualTo(true);
            assertThat(config.get("sendgridConfigured")).isEqualTo(true);
        }

        @Test
        @DisplayName("getEmailConfiguration_sendgridProviderEmptyKey_returnsNotConfigured")
        void getEmailConfiguration_sendgridProviderEmptyKey_returnsNotConfigured() throws Exception {
            ReflectionTestUtils.setField(emailTestService, "emailProvider", "sendgrid");
            ReflectionTestUtils.setField(emailTestService, "sendgridApiKey", "");

            final Map<String, Object> config = emailTestService.getEmailConfiguration();

            assertThat(config.get("configurationValid")).isEqualTo(false);
            assertThat(config.get("sendgridConfigured")).isEqualTo(false);
        }

        @Test
        @DisplayName("getEmailConfiguration_sendgridProviderNullKey_returnsNotConfigured")
        void getEmailConfiguration_sendgridProviderNullKey_returnsNotConfigured() throws Exception {
            ReflectionTestUtils.setField(emailTestService, "emailProvider", "sendgrid");
            ReflectionTestUtils.setField(emailTestService, "sendgridApiKey", null);

            final Map<String, Object> config = emailTestService.getEmailConfiguration();

            assertThat(config.get("configurationValid")).isEqualTo(false);
            assertThat(config.get("sendgridConfigured")).isEqualTo(false);
        }

        @Test
        @DisplayName("getEmailConfiguration_resendProviderWithKey_returnsConfigured")
        void getEmailConfiguration_resendProviderWithKey_returnsConfigured() throws Exception {
            ReflectionTestUtils.setField(emailTestService, "emailProvider", "resend");
            ReflectionTestUtils.setField(emailTestService, "resendApiKey", "re_testkey");

            final Map<String, Object> config = emailTestService.getEmailConfiguration();

            assertThat(config.get("configurationValid")).isEqualTo(true);
            assertThat(config.get("resendConfigured")).isEqualTo(true);
        }

        @Test
        @DisplayName("getEmailConfiguration_resendProviderEmptyKey_returnsNotConfigured")
        void getEmailConfiguration_resendProviderEmptyKey_returnsNotConfigured() throws Exception {
            ReflectionTestUtils.setField(emailTestService, "emailProvider", "resend");
            ReflectionTestUtils.setField(emailTestService, "resendApiKey", "");

            final Map<String, Object> config = emailTestService.getEmailConfiguration();

            assertThat(config.get("configurationValid")).isEqualTo(false);
            assertThat(config.get("resendConfigured")).isEqualTo(false);
        }

        @Test
        @DisplayName("getEmailConfiguration_resendProviderNullKey_returnsNotConfigured")
        void getEmailConfiguration_resendProviderNullKey_returnsNotConfigured() throws Exception {
            ReflectionTestUtils.setField(emailTestService, "emailProvider", "resend");
            ReflectionTestUtils.setField(emailTestService, "resendApiKey", null);

            final Map<String, Object> config = emailTestService.getEmailConfiguration();

            assertThat(config.get("configurationValid")).isEqualTo(false);
            assertThat(config.get("resendConfigured")).isEqualTo(false);
        }

        @Test
        @DisplayName("getEmailConfiguration_mailgunProviderWithKeysAndDomain_returnsConfigured")
        void getEmailConfiguration_mailgunProviderWithKeysAndDomain_returnsConfigured() throws Exception {
            ReflectionTestUtils.setField(emailTestService, "emailProvider", "mailgun");
            ReflectionTestUtils.setField(emailTestService, "mailgunApiKey", "key-abc123");
            ReflectionTestUtils.setField(emailTestService, "mailgunDomain", "mg.example.com");

            final Map<String, Object> config = emailTestService.getEmailConfiguration();

            assertThat(config.get("configurationValid")).isEqualTo(true);
            assertThat(config.get("mailgunConfigured")).isEqualTo(true);
        }

        @Test
        @DisplayName("getEmailConfiguration_mailgunProviderEmptyKey_returnsNotConfigured")
        void getEmailConfiguration_mailgunProviderEmptyKey_returnsNotConfigured() throws Exception {
            ReflectionTestUtils.setField(emailTestService, "emailProvider", "mailgun");
            ReflectionTestUtils.setField(emailTestService, "mailgunApiKey", "");
            ReflectionTestUtils.setField(emailTestService, "mailgunDomain", "mg.example.com");

            final Map<String, Object> config = emailTestService.getEmailConfiguration();

            assertThat(config.get("configurationValid")).isEqualTo(false);
            assertThat(config.get("mailgunConfigured")).isEqualTo(false);
        }

        @Test
        @DisplayName("getEmailConfiguration_mailgunProviderNullKey_returnsNotConfigured")
        void getEmailConfiguration_mailgunProviderNullKey_returnsNotConfigured() throws Exception {
            ReflectionTestUtils.setField(emailTestService, "emailProvider", "mailgun");
            ReflectionTestUtils.setField(emailTestService, "mailgunApiKey", null);
            ReflectionTestUtils.setField(emailTestService, "mailgunDomain", "mg.example.com");

            final Map<String, Object> config = emailTestService.getEmailConfiguration();

            assertThat(config.get("configurationValid")).isEqualTo(false);
            assertThat(config.get("mailgunConfigured")).isEqualTo(false);
        }

        @Test
        @DisplayName("getEmailConfiguration_mailgunProviderEmptyDomain_returnsNotConfigured")
        void getEmailConfiguration_mailgunProviderEmptyDomain_returnsNotConfigured() throws Exception {
            ReflectionTestUtils.setField(emailTestService, "emailProvider", "mailgun");
            ReflectionTestUtils.setField(emailTestService, "mailgunApiKey", "key-abc123");
            ReflectionTestUtils.setField(emailTestService, "mailgunDomain", "");

            final Map<String, Object> config = emailTestService.getEmailConfiguration();

            assertThat(config.get("configurationValid")).isEqualTo(false);
            assertThat(config.get("mailgunConfigured")).isEqualTo(false);
        }

        @Test
        @DisplayName("getEmailConfiguration_mailgunProviderNullDomain_returnsNotConfigured")
        void getEmailConfiguration_mailgunProviderNullDomain_returnsNotConfigured() throws Exception {
            ReflectionTestUtils.setField(emailTestService, "emailProvider", "mailgun");
            ReflectionTestUtils.setField(emailTestService, "mailgunApiKey", "key-abc123");
            ReflectionTestUtils.setField(emailTestService, "mailgunDomain", null);

            final Map<String, Object> config = emailTestService.getEmailConfiguration();

            assertThat(config.get("configurationValid")).isEqualTo(false);
            assertThat(config.get("mailgunConfigured")).isEqualTo(false);
        }

        @Test
        @DisplayName("getEmailConfiguration_smtpProviderWithMailSender_returnsValidConfig")
        void getEmailConfiguration_smtpProviderWithMailSender_returnsValidConfig() throws Exception {
            ReflectionTestUtils.setField(emailTestService, "emailProvider", "smtp");

            final Map<String, Object> config = emailTestService.getEmailConfiguration();

            assertThat(config.get("configurationValid")).isEqualTo(true);
        }

        @Test
        @DisplayName("getEmailConfiguration_smtpProviderNullMailSender_returnsInvalidConfig")
        void getEmailConfiguration_smtpProviderNullMailSender_returnsInvalidConfig() throws Exception {
            ReflectionTestUtils.setField(emailTestService, "emailProvider", "smtp");
            ReflectionTestUtils.setField(emailTestService, "mailSender", null);

            final Map<String, Object> config = emailTestService.getEmailConfiguration();

            assertThat(config.get("configurationValid")).isEqualTo(false);
        }

        @Test
        @DisplayName("getEmailConfiguration_smtpProviderEmptyHost_returnsInvalidConfig")
        void getEmailConfiguration_smtpProviderEmptyHost_returnsInvalidConfig() throws Exception {
            ReflectionTestUtils.setField(emailTestService, "emailProvider", "smtp");
            ReflectionTestUtils.setField(emailTestService, "mailHost", "");

            final Map<String, Object> config = emailTestService.getEmailConfiguration();

            assertThat(config.get("configurationValid")).isEqualTo(false);
        }

        @Test
        @DisplayName("getEmailConfiguration_smtpProviderEmptyPort_returnsInvalidConfig")
        void getEmailConfiguration_smtpProviderEmptyPort_returnsInvalidConfig() throws Exception {
            ReflectionTestUtils.setField(emailTestService, "emailProvider", "smtp");
            ReflectionTestUtils.setField(emailTestService, "mailPort", "");

            final Map<String, Object> config = emailTestService.getEmailConfiguration();

            assertThat(config.get("configurationValid")).isEqualTo(false);
        }

        @Test
        @DisplayName("getEmailConfiguration_nullMailSender_showsNotAvailable")
        void getEmailConfiguration_nullMailSender_showsNotAvailable() throws Exception {
            ReflectionTestUtils.setField(emailTestService, "mailSender", null);

            final Map<String, Object> config = emailTestService.getEmailConfiguration();

            assertThat(config.get("mailSenderAvailable")).isEqualTo(false);
        }

        @Test
        @DisplayName("getEmailConfiguration_unknownProvider_noProviderSpecificKeys")
        void getEmailConfiguration_unknownProvider_noProviderSpecificKeys() throws Exception {
            ReflectionTestUtils.setField(emailTestService, "emailProvider", "customProvider");

            final Map<String, Object> config = emailTestService.getEmailConfiguration();

            assertThat(config).doesNotContainKey("sendgridConfigured");
            assertThat(config).doesNotContainKey("resendConfigured");
            assertThat(config).doesNotContainKey("mailgunConfigured");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // sendTestEmail
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("sendTestEmail")
    class SendTestEmail {

        @Test
        @DisplayName("sendTestEmail_consoleProvider_returnsTrue")
        void sendTestEmail_consoleProvider_returnsTrue() throws MessagingException {
            final boolean result = emailTestService.sendTestEmail("test@example.com");

            assertThat(result).isTrue();
            verify(mailSender, never()).send(any(MimeMessage.class));
        }

        @Test
        @DisplayName("sendTestEmail_nullMailSender_returnsTrue")
        void sendTestEmail_nullMailSender_returnsTrue() throws MessagingException {
            ReflectionTestUtils.setField(emailTestService, "emailProvider", "smtp");
            ReflectionTestUtils.setField(emailTestService, "mailSender", null);

            final boolean result = emailTestService.sendTestEmail("test@example.com");

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("sendTestEmail_smtpProvider_sendsAndReturnsTrue")
        void sendTestEmail_smtpProvider_sendsAndReturnsTrue() throws MessagingException {
            ReflectionTestUtils.setField(emailTestService, "emailProvider", "smtp");

            final boolean result = emailTestService.sendTestEmail("test@example.com");

            assertThat(result).isTrue();
            verify(mailSender).createMimeMessage();
            verify(mailSender).send(any(MimeMessage.class));
        }

        @Test
        @DisplayName("sendTestEmail_smtpProviderThrowsMessagingException_rethrowsException")
        void sendTestEmail_smtpProviderThrowsMessagingException_rethrowsException() throws Exception {
            ReflectionTestUtils.setField(emailTestService, "emailProvider", "smtp");
            doThrow(new RuntimeException("SMTP error"))
                    .when(mailSender).send(any(MimeMessage.class));

            assertThatThrownBy(() -> emailTestService.sendTestEmail("test@example.com"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("SMTP error");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // validateEmailConfiguration
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("validateEmailConfiguration")
    class ValidateEmailConfiguration {

        @Test
        @DisplayName("validateEmailConfiguration_consoleProviderValidConfig_overallValid")
        void validateEmailConfiguration_consoleProviderValidConfig_overallValid() throws Exception {
            final Map<String, Object> validation = emailTestService.validateEmailConfiguration();

            assertThat(validation.get("providerSet")).isEqualTo(true);
            assertThat(validation.get("provider")).isEqualTo("console");
            assertThat(validation.get("fromEmailValid")).isEqualTo(true);
            assertThat(validation.get("fromEmail")).isEqualTo("noreply@careconnect.local");
            assertThat(validation.get("mailSenderExists")).isEqualTo(true);
            assertThat(validation.get("smtpConfigValid")).isEqualTo(true);
            assertThat(validation.get("overallValid")).isEqualTo(true);
            assertThat(validation).doesNotContainKey("recommendations");
            assertThat(validation.get("timestamp")).isNotNull();
        }

        @Test
        @DisplayName("validateEmailConfiguration_smtpProviderWithMailSender_overallValid")
        void validateEmailConfiguration_smtpProviderWithMailSender_overallValid() throws Exception {
            ReflectionTestUtils.setField(emailTestService, "emailProvider", "smtp");

            final Map<String, Object> validation = emailTestService.validateEmailConfiguration();

            assertThat(validation.get("overallValid")).isEqualTo(true);
        }

        @Test
        @DisplayName("validateEmailConfiguration_smtpProviderNullMailSender_overallInvalid")
        void validateEmailConfiguration_smtpProviderNullMailSender_overallInvalid() throws Exception {
            ReflectionTestUtils.setField(emailTestService, "emailProvider", "smtp");
            ReflectionTestUtils.setField(emailTestService, "mailSender", null);

            final Map<String, Object> validation = emailTestService.validateEmailConfiguration();

            assertThat(validation.get("overallValid")).isEqualTo(false);
            assertThat(validation.get("recommendations")).isNotNull();
            assertThat((String) validation.get("recommendations"))
                    .contains("Configure JavaMailSender");
        }

        @Test
        @DisplayName("validateEmailConfiguration_nullProvider_overallInvalid")
        void validateEmailConfiguration_nullProvider_overallInvalid() throws Exception {
            ReflectionTestUtils.setField(emailTestService, "emailProvider", null);

            final Map<String, Object> validation = emailTestService.validateEmailConfiguration();

            assertThat(validation.get("providerSet")).isEqualTo(false);
            assertThat(validation.get("overallValid")).isEqualTo(false);
            assertThat(validation.get("recommendations")).isNotNull();
            assertThat((String) validation.get("recommendations"))
                    .contains("Set EMAIL_PROVIDER");
        }

        @Test
        @DisplayName("validateEmailConfiguration_emptyProvider_overallInvalid")
        void validateEmailConfiguration_emptyProvider_overallInvalid() throws Exception {
            ReflectionTestUtils.setField(emailTestService, "emailProvider", "");

            final Map<String, Object> validation = emailTestService.validateEmailConfiguration();

            assertThat(validation.get("providerSet")).isEqualTo(false);
            assertThat(validation.get("overallValid")).isEqualTo(false);
        }

        @Test
        @DisplayName("validateEmailConfiguration_blankProvider_overallInvalid")
        void validateEmailConfiguration_blankProvider_overallInvalid() throws Exception {
            ReflectionTestUtils.setField(emailTestService, "emailProvider", "   ");

            final Map<String, Object> validation = emailTestService.validateEmailConfiguration();

            assertThat(validation.get("providerSet")).isEqualTo(false);
        }

        @Test
        @DisplayName("validateEmailConfiguration_nullFromEmail_invalidFromEmail")
        void validateEmailConfiguration_nullFromEmail_invalidFromEmail() throws Exception {
            ReflectionTestUtils.setField(emailTestService, "fromEmail", null);

            final Map<String, Object> validation = emailTestService.validateEmailConfiguration();

            assertThat(validation.get("fromEmailValid")).isEqualTo(false);
            assertThat(validation.get("overallValid")).isEqualTo(false);
            assertThat((String) validation.get("recommendations"))
                    .contains("Set FROM_EMAIL");
        }

        @Test
        @DisplayName("validateEmailConfiguration_emptyFromEmail_invalidFromEmail")
        void validateEmailConfiguration_emptyFromEmail_invalidFromEmail() throws Exception {
            ReflectionTestUtils.setField(emailTestService, "fromEmail", "");

            final Map<String, Object> validation = emailTestService.validateEmailConfiguration();

            assertThat(validation.get("fromEmailValid")).isEqualTo(false);
        }

        @Test
        @DisplayName("validateEmailConfiguration_fromEmailNoAt_invalidFromEmail")
        void validateEmailConfiguration_fromEmailNoAt_invalidFromEmail() throws Exception {
            ReflectionTestUtils.setField(emailTestService, "fromEmail", "invalidemail");

            final Map<String, Object> validation = emailTestService.validateEmailConfiguration();

            assertThat(validation.get("fromEmailValid")).isEqualTo(false);
        }

        @Test
        @DisplayName("validateEmailConfiguration_blankFromEmail_invalidFromEmail")
        void validateEmailConfiguration_blankFromEmail_invalidFromEmail() throws Exception {
            ReflectionTestUtils.setField(emailTestService, "fromEmail", "   ");

            final Map<String, Object> validation = emailTestService.validateEmailConfiguration();

            assertThat(validation.get("fromEmailValid")).isEqualTo(false);
        }

        @Test
        @DisplayName("validateEmailConfiguration_emptyMailHost_smtpConfigInvalid")
        void validateEmailConfiguration_emptyMailHost_smtpConfigInvalid() throws Exception {
            ReflectionTestUtils.setField(emailTestService, "emailProvider", "smtp");
            ReflectionTestUtils.setField(emailTestService, "mailHost", "");

            final Map<String, Object> validation = emailTestService.validateEmailConfiguration();

            assertThat(validation.get("smtpConfigValid")).isEqualTo(false);
            assertThat(validation.get("overallValid")).isEqualTo(false);
            assertThat((String) validation.get("recommendations"))
                    .contains("Set MAIL_HOST");
        }

        @Test
        @DisplayName("validateEmailConfiguration_emptyMailPort_smtpConfigInvalid")
        void validateEmailConfiguration_emptyMailPort_smtpConfigInvalid() throws Exception {
            ReflectionTestUtils.setField(emailTestService, "emailProvider", "smtp");
            ReflectionTestUtils.setField(emailTestService, "mailPort", "");

            final Map<String, Object> validation = emailTestService.validateEmailConfiguration();

            assertThat(validation.get("smtpConfigValid")).isEqualTo(false);
            assertThat(validation.get("overallValid")).isEqualTo(false);
            assertThat((String) validation.get("recommendations"))
                    .contains("Set MAIL_PORT");
        }

        @Test
        @DisplayName("validateEmailConfiguration_allInvalid_returnsAllRecommendations")
        void validateEmailConfiguration_allInvalid_returnsAllRecommendations() throws Exception {
            ReflectionTestUtils.setField(emailTestService, "emailProvider", null);
            ReflectionTestUtils.setField(emailTestService, "fromEmail", null);
            ReflectionTestUtils.setField(emailTestService, "mailSender", null);
            ReflectionTestUtils.setField(emailTestService, "mailHost", "");
            ReflectionTestUtils.setField(emailTestService, "mailPort", "");

            final Map<String, Object> validation = emailTestService.validateEmailConfiguration();

            assertThat(validation.get("overallValid")).isEqualTo(false);
            final String recommendations = (String) validation.get("recommendations");
            assertThat(recommendations).contains("Set EMAIL_PROVIDER");
            assertThat(recommendations).contains("Set FROM_EMAIL");
            assertThat(recommendations).contains("Configure JavaMailSender");
            assertThat(recommendations).contains("Set MAIL_HOST");
            assertThat(recommendations).contains("Set MAIL_PORT");
        }

        @Test
        @DisplayName("validateEmailConfiguration_consoleProviderNullMailSender_stillValid")
        void validateEmailConfiguration_consoleProviderNullMailSender_stillValid() throws Exception {
            ReflectionTestUtils.setField(emailTestService, "mailSender", null);

            final Map<String, Object> validation = emailTestService.validateEmailConfiguration();

            // Console provider does not require mailSender
            assertThat(validation.get("overallValid")).isEqualTo(true);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // testAllEmailTypes
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("testAllEmailTypes")
    class TestAllEmailTypes {

        @Test
        @DisplayName("testAllEmailTypes_consoleProvider_allSuccess")
        void testAllEmailTypes_consoleProvider_allSuccess() throws Exception {
            final Map<String, Object> results = emailTestService.testAllEmailTypes("test@example.com");

            assertThat(results.get("testEmail")).isEqualTo("test@example.com");
            assertThat(results.get("timestamp")).isNotNull();
            assertThat(results.get("verificationEmail")).isEqualTo("SUCCESS");
            assertThat(results.get("passwordResetEmail")).isEqualTo("SUCCESS");
            assertThat(results.get("passwordSetupEmail")).isEqualTo("SUCCESS");
        }

        @Test
        @DisplayName("testAllEmailTypes_smtpProvider_allSuccess")
        void testAllEmailTypes_smtpProvider_allSuccess() throws Exception {
            ReflectionTestUtils.setField(emailTestService, "emailProvider", "smtp");

            final Map<String, Object> results = emailTestService.testAllEmailTypes("test@example.com");

            assertThat(results.get("verificationEmail")).isEqualTo("SUCCESS");
            assertThat(results.get("passwordResetEmail")).isEqualTo("SUCCESS");
            assertThat(results.get("passwordSetupEmail")).isEqualTo("SUCCESS");
            verify(mailSender, times(3)).send(any(MimeMessage.class));
        }

        @Test
        @DisplayName("testAllEmailTypes_smtpProviderMailSendFails_allFailed")
        void testAllEmailTypes_smtpProviderMailSendFails_allFailed() throws Exception {
            ReflectionTestUtils.setField(emailTestService, "emailProvider", "smtp");
            doThrow(new RuntimeException("SMTP down"))
                    .when(mailSender).send(any(MimeMessage.class));

            final Map<String, Object> results = emailTestService.testAllEmailTypes("test@example.com");

            assertThat((String) results.get("verificationEmail")).startsWith("FAILED:");
            assertThat((String) results.get("passwordResetEmail")).startsWith("FAILED:");
            assertThat((String) results.get("passwordSetupEmail")).startsWith("FAILED:");
        }

        @Test
        @DisplayName("testAllEmailTypes_nullMailSender_allSuccessViaConsoleFallback")
        void testAllEmailTypes_nullMailSender_allSuccessViaConsoleFallback() throws Exception {
            ReflectionTestUtils.setField(emailTestService, "mailSender", null);

            final Map<String, Object> results = emailTestService.testAllEmailTypes("test@example.com");

            assertThat(results.get("verificationEmail")).isEqualTo("SUCCESS");
            assertThat(results.get("passwordResetEmail")).isEqualTo("SUCCESS");
            assertThat(results.get("passwordSetupEmail")).isEqualTo("SUCCESS");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // testSimpleEmail
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("testSimpleEmail")
    class TestSimpleEmail {

        @Test
        @DisplayName("testSimpleEmail_consoleProvider_returnsSuccessResult")
        void testSimpleEmail_consoleProvider_returnsSuccessResult() throws Exception {
            final Map<String, Object> result = emailTestService.testSimpleEmail("test@example.com");

            assertThat(result.get("recipientEmail")).isEqualTo("test@example.com");
            assertThat(result.get("emailProvider")).isEqualTo("console");
            assertThat(result.get("fromEmail")).isEqualTo("noreply@careconnect.local");
            assertThat(result.get("success")).isEqualTo(true);
            assertThat(result.get("message")).isEqualTo("Simple test email sent successfully");
            assertThat(result.get("timestamp")).isNotNull();
        }

        @Test
        @DisplayName("testSimpleEmail_smtpProviderSuccess_returnsSuccessResult")
        void testSimpleEmail_smtpProviderSuccess_returnsSuccessResult() throws Exception {
            ReflectionTestUtils.setField(emailTestService, "emailProvider", "smtp");

            final Map<String, Object> result = emailTestService.testSimpleEmail("test@example.com");

            assertThat(result.get("success")).isEqualTo(true);
            assertThat(result.get("message")).isEqualTo("Simple test email sent successfully");
        }

        @Test
        @DisplayName("testSimpleEmail_smtpProviderThrows_returnsFailureResult")
        void testSimpleEmail_smtpProviderThrows_returnsFailureResult() throws Exception {
            ReflectionTestUtils.setField(emailTestService, "emailProvider", "smtp");
            doThrow(new RuntimeException("SMTP connection refused"))
                    .when(mailSender).send(any(MimeMessage.class));

            final Map<String, Object> result = emailTestService.testSimpleEmail("test@example.com");

            assertThat(result.get("success")).isEqualTo(false);
            assertThat((String) result.get("message")).contains("Simple test email failed");
            assertThat(result.get("error")).isNotNull();
        }

        @Test
        @DisplayName("testSimpleEmail_nullMailSender_returnsSuccessViaConsoleFallback")
        void testSimpleEmail_nullMailSender_returnsSuccessViaConsoleFallback() throws Exception {
            ReflectionTestUtils.setField(emailTestService, "mailSender", null);

            final Map<String, Object> result = emailTestService.testSimpleEmail("test@example.com");

            assertThat(result.get("success")).isEqualTo(true);
        }
    }
}
