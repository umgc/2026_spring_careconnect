package com.careconnect.dto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ResetPasswordRequestTest {

    // ─── Default constructor ──────────────────────────────────────────────────

    @Test
    void defaultConstructor_createsInstance() throws Exception {
        final ResetPasswordRequest request = new ResetPasswordRequest();

        assertThat(request).isNotNull();
        assertThat(request.getUsername()).isNull();
        assertThat(request.getResetToken()).isNull();
        assertThat(request.getNewPassword()).isNull();
    }

    // ─── Setters and Getters ──────────────────────────────────────────────────

    @Test
    void setUsername_getUsername_roundTrips() throws Exception {
        final ResetPasswordRequest request = new ResetPasswordRequest();
        request.setUsername("user@example.com");
        assertThat(request.getUsername()).isEqualTo("user@example.com");
    }

    @Test
    void setResetToken_getResetToken_roundTrips() throws Exception {
        final ResetPasswordRequest request = new ResetPasswordRequest();
        request.setResetToken("abc-reset-token-123");
        assertThat(request.getResetToken()).isEqualTo("abc-reset-token-123");
    }

    @Test
    void setNewPassword_getNewPassword_roundTrips() throws Exception {
        final ResetPasswordRequest request = new ResetPasswordRequest();
        request.setNewPassword("Secur3P@ssword!");
        assertThat(request.getNewPassword()).isEqualTo("Secur3P@ssword!");
    }

    // ─── All fields set together ──────────────────────────────────────────────

    @Test
    void allSetters_allFieldsUpdated() throws Exception {
        final ResetPasswordRequest request = new ResetPasswordRequest();

        request.setUsername("admin@careconnect.com");
        request.setResetToken("token-xyz-789");
        request.setNewPassword("N3wPassw0rd#");

        assertThat(request.getUsername()).isEqualTo("admin@careconnect.com");
        assertThat(request.getResetToken()).isEqualTo("token-xyz-789");
        assertThat(request.getNewPassword()).isEqualTo("N3wPassw0rd#");
    }
}
