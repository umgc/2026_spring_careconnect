package com.careconnect.exception;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class OAuthExceptionTest {

    // ─── Two-arg constructor ──────────────────────────────────────────────────

    @Test
    void constructor_messageAndErrorType_setsCorrectly() throws Exception {
        final OAuthException exception = new OAuthException("OAuth failed", "INVALID_TOKEN");

        assertThat(exception.getMessage()).isEqualTo("OAuth failed");
        assertThat(exception.getErrorType()).isEqualTo("INVALID_TOKEN");
        assertThat(exception.getCause()).isNull();
    }

    @Test
    void constructor_differentErrorType_setsCorrectly() throws Exception {
        final OAuthException exception = new OAuthException("Access denied", "ACCESS_DENIED");

        assertThat(exception.getMessage()).isEqualTo("Access denied");
        assertThat(exception.getErrorType()).isEqualTo("ACCESS_DENIED");
    }

    // ─── Three-arg constructor (with cause) ───────────────────────────────────

    @Test
    void constructor_withCause_setsAllFields() throws Exception {
        final Throwable cause = new RuntimeException("underlying cause");
        final OAuthException exception = new OAuthException("OAuth error with cause", "SERVER_ERROR", cause);

        assertThat(exception.getMessage()).isEqualTo("OAuth error with cause");
        assertThat(exception.getErrorType()).isEqualTo("SERVER_ERROR");
        assertThat(exception.getCause()).isEqualTo(cause);
    }

    @Test
    void constructor_withCause_differentValues() throws Exception {
        final Throwable cause = new IllegalStateException("state error");
        final OAuthException exception = new OAuthException("Token refresh failed", "TOKEN_EXPIRED", cause);

        assertThat(exception.getMessage()).isEqualTo("Token refresh failed");
        assertThat(exception.getErrorType()).isEqualTo("TOKEN_EXPIRED");
        assertThat(exception.getCause()).isSameAs(cause);
    }

    // ─── Is a RuntimeException ────────────────────────────────────────────────

    @Test
    void oAuthException_isRuntimeException() throws Exception {
        final OAuthException exception = new OAuthException("error", "TYPE");

        assertThat(exception).isInstanceOf(RuntimeException.class);
    }

    @Test
    void oAuthException_canBeThrown() throws Exception {
        assertThatThrownBy(() -> {
            throw new OAuthException("thrown", "THROWN_TYPE");
        })
                .isInstanceOf(OAuthException.class)
                .hasMessage("thrown");
    }
}
