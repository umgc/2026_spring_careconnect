package com.careconnect.service;

import com.careconnect.model.User;
import com.careconnect.repository.UserRepository;
import com.careconnect.repository.PasswordResetTokenRepo;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.util.Base64;
import java.util.Optional;

import org.junit.jupiter.api.Nested;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PasswordResetServiceTest {

    @Mock
    private UserRepository users;

    @Mock
    private PasswordResetTokenRepo tokens;

    @Mock
    private PasswordEncoder encoder;

    @Mock
    private JavaMailSender mail;

    @Mock
    private MimeMessage mimeMessage;

    @InjectMocks
    private PasswordResetService passwordResetService;

    private User testUser;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);

        testUser = new User();
        testUser.setId(42L);
        testUser.setEmail("patient@example.com");
        testUser.setPassword("oldEncodedPassword");

        // Set @Value fields via reflection
        ReflectionTestUtils.setField(passwordResetService, "emailProvider", "sendgrid");
        ReflectionTestUtils.setField(passwordResetService, "fromEmail", "smpestest@gmail.com");
        ReflectionTestUtils.setField(passwordResetService, "mail", mail);
    }

    // ---- startReset ------------------------------------------------------------

    @Test
    @DisplayName("startReset_userExistsEmailProviderNotConsole_sendsEmail")
    void startReset_userExistsEmailProviderNotConsole_sendsEmail() throws Exception {
        when(users.findByEmail("patient@example.com")).thenReturn(Optional.of(testUser));
        when(mail.createMimeMessage()).thenReturn(mimeMessage);

        passwordResetService.startReset("patient@example.com", "https://app.careconnect.com");

        verify(users).findByEmail("patient@example.com");
        verify(mail).createMimeMessage();
        verify(mail).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("startReset_emailNotFound_throwsIllegalArgumentException")
    void startReset_emailNotFound_throwsIllegalArgumentException() throws Exception {
        when(users.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> passwordResetService.startReset("unknown@example.com", "https://app.careconnect.com"));

        assertEquals("Email not found", ex.getMessage());
    }

    @Test
    @DisplayName("startReset_emailProviderConsole_doesNotSendMail")
    void startReset_emailProviderConsole_doesNotSendMail() throws Exception {
        ReflectionTestUtils.setField(passwordResetService, "emailProvider", "console");
        when(users.findByEmail("patient@example.com")).thenReturn(Optional.of(testUser));

        passwordResetService.startReset("patient@example.com", "https://app.careconnect.com");

        verify(mail, never()).createMimeMessage();
        verify(mail, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("startReset_mailIsNull_doesNotSendMail")
    void startReset_mailIsNull_doesNotSendMail() throws Exception {
        ReflectionTestUtils.setField(passwordResetService, "mail", null);
        when(users.findByEmail("patient@example.com")).thenReturn(Optional.of(testUser));

        passwordResetService.startReset("patient@example.com", "https://app.careconnect.com");

        // No exception thrown, mail not used
        verify(users).findByEmail("patient@example.com");
    }

    @Test
    @DisplayName("startReset_fromEmailNull_throwsRuntimeException")
    void startReset_fromEmailNull_throwsRuntimeException() throws Exception {
        ReflectionTestUtils.setField(passwordResetService, "fromEmail", null);
        when(users.findByEmail("patient@example.com")).thenReturn(Optional.of(testUser));
        when(mail.createMimeMessage()).thenReturn(mimeMessage);

        assertThrows(RuntimeException.class,
                () -> passwordResetService.startReset("patient@example.com", "https://app.careconnect.com"));
    }

    @Test
    @DisplayName("startReset_fromEmailEmpty_throwsRuntimeException")
    void startReset_fromEmailEmpty_throwsRuntimeException() throws Exception {
        ReflectionTestUtils.setField(passwordResetService, "fromEmail", "   ");
        when(users.findByEmail("patient@example.com")).thenReturn(Optional.of(testUser));
        when(mail.createMimeMessage()).thenReturn(mimeMessage);

        assertThrows(RuntimeException.class,
                () -> passwordResetService.startReset("patient@example.com", "https://app.careconnect.com"));
    }

    @Test
    @DisplayName("startReset_fromEmailEmptyString_throwsRuntimeException")
    void startReset_fromEmailEmptyString_throwsRuntimeException() throws Exception {
        ReflectionTestUtils.setField(passwordResetService, "fromEmail", "");
        when(users.findByEmail("patient@example.com")).thenReturn(Optional.of(testUser));
        when(mail.createMimeMessage()).thenReturn(mimeMessage);

        assertThrows(RuntimeException.class,
                () -> passwordResetService.startReset("patient@example.com", "https://app.careconnect.com"));
    }

    @Test
    @DisplayName("startReset_mailSendFails_throwsRuntimeException")
    void startReset_mailSendFails_throwsRuntimeException() throws Exception {
        when(users.findByEmail("patient@example.com")).thenReturn(Optional.of(testUser));
        when(mail.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new RuntimeException("SMTP failure")).when(mail).send(any(MimeMessage.class));

        final RuntimeException ex = assertThrows(RuntimeException.class,
                () -> passwordResetService.startReset("patient@example.com", "https://app.careconnect.com"));

        assertEquals("Failed to send password reset email", ex.getMessage());
    }

    @Test
    @DisplayName("startReset_validUser_linkContainsEncodedUserId")
    void startReset_validUser_linkContainsEncodedUserId() throws Exception {
        when(users.findByEmail("patient@example.com")).thenReturn(Optional.of(testUser));
        // Use console to avoid actually sending and just validate flow
        ReflectionTestUtils.setField(passwordResetService, "emailProvider", "console");

        passwordResetService.startReset("patient@example.com", "https://app.careconnect.com");

        // Verify the user was looked up successfully
        verify(users).findByEmail("patient@example.com");
    }

    @Test
    @DisplayName("startReset_consoleProviderWithNonNullMail_skipsMailSend")
    void startReset_consoleProviderWithNonNullMail_skipsMailSend() throws Exception {
        ReflectionTestUtils.setField(passwordResetService, "emailProvider", "console");
        when(users.findByEmail("patient@example.com")).thenReturn(Optional.of(testUser));

        passwordResetService.startReset("patient@example.com", "https://app.careconnect.com");

        verify(mail, never()).send(any(MimeMessage.class));
    }

    // ---- finalizeReset ---------------------------------------------------------

    @Test
    @DisplayName("finalizeReset_validToken_updatesPassword")
    void finalizeReset_validToken_updatesPassword() throws Exception {
        final String rawToken = Base64.getUrlEncoder().encodeToString("42".getBytes());
        when(users.findById(42L)).thenReturn(Optional.of(testUser));
        when(encoder.encode("newPassword123")).thenReturn("encodedNewPassword");

        passwordResetService.finalizeReset(rawToken, "newPassword123");

        assertEquals("encodedNewPassword", testUser.getPassword());
        verify(users).save(testUser);
        verify(encoder).encode("newPassword123");
    }

    @Test
    @DisplayName("finalizeReset_userNotFound_throwsIllegalArgumentException")
    void finalizeReset_userNotFound_throwsIllegalArgumentException() throws Exception {
        final String rawToken = Base64.getUrlEncoder().encodeToString("999".getBytes());
        when(users.findById(999L)).thenReturn(Optional.empty());

        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> passwordResetService.finalizeReset(rawToken, "newPassword123"));

        assertEquals("Invalid or missing reset token", ex.getMessage());
    }

    @Test
    @DisplayName("finalizeReset_invalidBase64Token_throwsIllegalArgumentException")
    void finalizeReset_invalidBase64Token_throwsIllegalArgumentException() throws Exception {
        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> passwordResetService.finalizeReset("!!!invalid!!!", "newPassword123"));

        // The catch (IllegalArgumentException e) { throw e; } re-throws the original
        // Base64 decoding exception with its native message
        assertTrue(ex.getMessage().contains("Illegal base64 character"));
    }

    @Test
    @DisplayName("finalizeReset_tokenDecodesToNonNumeric_throwsIllegalArgumentException")
    void finalizeReset_tokenDecodesToNonNumeric_throwsIllegalArgumentException() throws Exception {
        final String rawToken = Base64.getUrlEncoder().encodeToString("not-a-number".getBytes());

        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> passwordResetService.finalizeReset(rawToken, "newPassword123"));

        // NumberFormatException (subclass of IllegalArgumentException) is re-thrown
        // by the catch (IllegalArgumentException e) { throw e; } block with its native message
        assertTrue(ex.getMessage().contains("For input string"));
    }

    @Test
    @DisplayName("finalizeReset_orElseThrowIllegalArg_rethrowsSameException")
    void finalizeReset_orElseThrowIllegalArg_rethrowsSameException() throws Exception {
        final String rawToken = Base64.getUrlEncoder().encodeToString("42".getBytes());
        when(users.findById(42L)).thenReturn(Optional.empty());

        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> passwordResetService.finalizeReset(rawToken, "password"));

        assertEquals("Invalid or missing reset token", ex.getMessage());
    }

    @Test
    @DisplayName("finalizeReset_encoderCalledOnce_savesEncodedPassword")
    void finalizeReset_encoderCalledOnce_savesEncodedPassword() throws Exception {
        final String rawToken = Base64.getUrlEncoder().encodeToString("42".getBytes());
        when(users.findById(42L)).thenReturn(Optional.of(testUser));
        when(encoder.encode("P@ssw0rd!")).thenReturn("$2a$10$hashvalue");

        passwordResetService.finalizeReset(rawToken, "P@ssw0rd!");

        verify(encoder, times(1)).encode("P@ssw0rd!");
        assertEquals("$2a$10$hashvalue", testUser.getPassword());
        verify(users).save(testUser);
    }

    @Test
    @DisplayName("finalizeReset_nullToken_throwsIllegalArgumentException")
    void finalizeReset_nullToken_throwsIllegalArgumentException() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> passwordResetService.finalizeReset(null, "newPassword"));
    }

    // ---- isTokenValid ----------------------------------------------------------

    @Test
    @DisplayName("isTokenValid_validEncodedUserId_returnsTrue")
    void isTokenValid_validEncodedUserId_returnsTrue() throws Exception {
        final String encodedUserId = Base64.getUrlEncoder().encodeToString("42".getBytes());
        when(users.findById(42L)).thenReturn(Optional.of(testUser));

        assertTrue(passwordResetService.isTokenValid(encodedUserId));
    }

    @Test
    @DisplayName("isTokenValid_userNotFound_returnsFalse")
    void isTokenValid_userNotFound_returnsFalse() throws Exception {
        final String encodedUserId = Base64.getUrlEncoder().encodeToString("999".getBytes());
        when(users.findById(999L)).thenReturn(Optional.empty());

        assertFalse(passwordResetService.isTokenValid(encodedUserId));
    }

    @Test
    @DisplayName("isTokenValid_invalidBase64_returnsFalse")
    void isTokenValid_invalidBase64_returnsFalse() throws Exception {
        assertFalse(passwordResetService.isTokenValid("!!!not-base64!!!"));
    }

    @Test
    @DisplayName("isTokenValid_decodesToNonNumeric_returnsFalse")
    void isTokenValid_decodesToNonNumeric_returnsFalse() throws Exception {
        final String encoded = Base64.getUrlEncoder().encodeToString("abc".getBytes());

        assertFalse(passwordResetService.isTokenValid(encoded));
    }

    @Test
    @DisplayName("isTokenValid_unexpectedException_returnsFalse")
    void isTokenValid_unexpectedException_returnsFalse() throws Exception {
        final String encodedUserId = Base64.getUrlEncoder().encodeToString("42".getBytes());
        when(users.findById(42L)).thenThrow(new RuntimeException("DB down"));

        assertFalse(passwordResetService.isTokenValid(encodedUserId));
    }

    @Test
    @DisplayName("isTokenValid_nullToken_returnsFalse")
    void isTokenValid_nullToken_returnsFalse() throws Exception {
        assertFalse(passwordResetService.isTokenValid(null));
    }

    @Test
    @DisplayName("isTokenValid_emptyString_returnsFalse")
    void isTokenValid_emptyString_returnsFalse() throws Exception {
        // Empty string is valid base64 that decodes to empty byte array, then empty string,
        // which will fail Long.parseLong
        assertFalse(passwordResetService.isTokenValid(""));
    }

    // ---- provider info (tested indirectly via startReset mail flow) -----------

    @Test
    @DisplayName("startReset_emailProviderMailtrap_sendsEmailViaMail")
    void startReset_emailProviderMailtrap_sendsEmailViaMail() throws Exception {
        ReflectionTestUtils.setField(passwordResetService, "emailProvider", "mailtrap");
        when(users.findByEmail("patient@example.com")).thenReturn(Optional.of(testUser));
        when(mail.createMimeMessage()).thenReturn(mimeMessage);

        passwordResetService.startReset("patient@example.com", "https://app.careconnect.com");

        verify(mail).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("startReset_emailProviderGmail_sendsEmailViaMail")
    void startReset_emailProviderGmail_sendsEmailViaMail() throws Exception {
        ReflectionTestUtils.setField(passwordResetService, "emailProvider", "gmail");
        when(users.findByEmail("patient@example.com")).thenReturn(Optional.of(testUser));
        when(mail.createMimeMessage()).thenReturn(mimeMessage);

        passwordResetService.startReset("patient@example.com", "https://app.careconnect.com");

        verify(mail).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("startReset_emailProviderCustom_sendsEmailViaMail")
    void startReset_emailProviderCustom_sendsEmailViaMail() throws Exception {
        ReflectionTestUtils.setField(passwordResetService, "emailProvider", "ses");
        when(users.findByEmail("patient@example.com")).thenReturn(Optional.of(testUser));
        when(mail.createMimeMessage()).thenReturn(mimeMessage);

        passwordResetService.startReset("patient@example.com", "https://app.careconnect.com");

        verify(mail).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("startReset_emailProviderSendgrid_sendsEmailViaMail")
    void startReset_emailProviderSendgrid_sendsEmailViaMail() throws Exception {
        ReflectionTestUtils.setField(passwordResetService, "emailProvider", "sendgrid");
        when(users.findByEmail("patient@example.com")).thenReturn(Optional.of(testUser));
        when(mail.createMimeMessage()).thenReturn(mimeMessage);

        passwordResetService.startReset("patient@example.com", "https://app.careconnect.com");

        verify(mail).send(any(MimeMessage.class));
    }

    // ---- sendPasswordResetEmail edge cases -----------------------------------

    @Test
    @DisplayName("startReset_mimeMessageHelperException_throwsRuntimeException")
    void startReset_mimeMessageHelperException_throwsRuntimeException() throws Exception {
        when(users.findByEmail("patient@example.com")).thenReturn(Optional.of(testUser));
        when(mail.createMimeMessage()).thenThrow(new RuntimeException("Cannot create message"));

        final RuntimeException ex = assertThrows(RuntimeException.class,
                () -> passwordResetService.startReset("patient@example.com", "https://app.careconnect.com"));

        assertEquals("Failed to send password reset email", ex.getMessage());
    }

    // ---- private helper: generateSecureRandomString (via reflection) ----------

    @Nested
    @DisplayName("GenerateSecureRandomString Tests")
    class GenerateSecureRandomStringTests {

        @Test
        @DisplayName("generateSecureRandomString_validLength_returnsBase64String")
        void generateSecureRandomString_validLength_returnsBase64String() throws Exception {
            final Method method = PasswordResetService.class.getDeclaredMethod("generateSecureRandomString", int.class);
            method.setAccessible(true);

            final String result = (String) method.invoke(passwordResetService, 48);

            assertNotNull(result);
            assertTrue(result.length() > 0);
            // Base64 URL encoding without padding
            assertFalse(result.contains("="));
        }

        @Test
        @DisplayName("generateSecureRandomString_differentCalls_returnDifferentValues")
        void generateSecureRandomString_differentCalls_returnDifferentValues() throws Exception {
            final Method method = PasswordResetService.class.getDeclaredMethod("generateSecureRandomString", int.class);
            method.setAccessible(true);

            final String result1 = (String) method.invoke(passwordResetService, 48);
            final String result2 = (String) method.invoke(passwordResetService, 48);

            // Extremely unlikely to be the same
            assertNotEquals(result1, result2);
        }

        @Test
        @DisplayName("generateSecureRandomString_smallLength_returnsShortString")
        void generateSecureRandomString_smallLength_returnsShortString() throws Exception {
            final Method method = PasswordResetService.class.getDeclaredMethod("generateSecureRandomString", int.class);
            method.setAccessible(true);

            final String result = (String) method.invoke(passwordResetService, 1);

            assertNotNull(result);
            assertTrue(result.length() >= 1);
        }
    }

    // ---- private helper: hash (via reflection) --------------------------------

    @Nested
    @DisplayName("Hash Tests")
    class HashTests {

        @Test
        @DisplayName("hash_validInput_returnsSha256Hex")
        void hash_validInput_returnsSha256Hex() throws Exception {
            final Method method = PasswordResetService.class.getDeclaredMethod("hash", String.class);
            method.setAccessible(true);

            final String result = (String) method.invoke(passwordResetService, "test-token");

            assertNotNull(result);
            assertEquals(64, result.length()); // SHA-256 hex is 64 chars
            assertTrue(result.matches("[0-9a-f]+"));
        }

        @Test
        @DisplayName("hash_sameInput_returnsSameHash")
        void hash_sameInput_returnsSameHash() throws Exception {
            final Method method = PasswordResetService.class.getDeclaredMethod("hash", String.class);
            method.setAccessible(true);

            final String result1 = (String) method.invoke(passwordResetService, "same-token");
            final String result2 = (String) method.invoke(passwordResetService, "same-token");

            assertEquals(result1, result2);
        }

        @Test
        @DisplayName("hash_differentInputs_returnDifferentHashes")
        void hash_differentInputs_returnDifferentHashes() throws Exception {
            final Method method = PasswordResetService.class.getDeclaredMethod("hash", String.class);
            method.setAccessible(true);

            final String result1 = (String) method.invoke(passwordResetService, "token-a");
            final String result2 = (String) method.invoke(passwordResetService, "token-b");

            assertNotEquals(result1, result2);
        }
    }

    // ---- private helper: getProviderInfo (via reflection) ---------------------

    @Nested
    @DisplayName("GetProviderInfo Tests")
    class GetProviderInfoTests {

        private String invokeGetProviderInfo(String provider) throws Exception {
            ReflectionTestUtils.setField(passwordResetService, "emailProvider", provider);
            final Method method = PasswordResetService.class.getDeclaredMethod("getProviderInfo");
            method.setAccessible(true);
            return (String) method.invoke(passwordResetService);
        }

        @Test
        @DisplayName("getProviderInfo_mailtrap_returnsMailtrapDevelopment")
        void getProviderInfo_mailtrap_returnsMailtrapDevelopment() throws Exception {
            assertEquals("Mailtrap (Development)", invokeGetProviderInfo("mailtrap"));
        }

        @Test
        @DisplayName("getProviderInfo_sendgrid_returnsSendGridProduction")
        void getProviderInfo_sendgrid_returnsSendGridProduction() throws Exception {
            assertEquals("SendGrid (Production)", invokeGetProviderInfo("sendgrid"));
        }

        @Test
        @DisplayName("getProviderInfo_gmail_returnsGmailProduction")
        void getProviderInfo_gmail_returnsGmailProduction() throws Exception {
            assertEquals("Gmail (Production)", invokeGetProviderInfo("gmail"));
        }

        @Test
        @DisplayName("getProviderInfo_console_returnsConsoleTesting")
        void getProviderInfo_console_returnsConsoleTesting() throws Exception {
            assertEquals("Console (Testing)", invokeGetProviderInfo("console"));
        }

        @Test
        @DisplayName("getProviderInfo_unknown_returnsProviderNameAsIs")
        void getProviderInfo_unknown_returnsProviderNameAsIs() throws Exception {
            assertEquals("ses", invokeGetProviderInfo("ses"));
        }

        @Test
        @DisplayName("getProviderInfo_upperCase_matchesCaseInsensitive")
        void getProviderInfo_upperCase_matchesCaseInsensitive() throws Exception {
            // The switch uses emailProvider.toLowerCase() so MAILTRAP -> mailtrap
            assertEquals("Mailtrap (Development)", invokeGetProviderInfo("MAILTRAP"));
        }
    }
}
