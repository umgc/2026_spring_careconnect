package com.careconnect.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GlobalExceptionHandler")
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() throws Exception {
        handler = new GlobalExceptionHandler();
    }

    // ── handleRegistrationException ────────────────────────────────────────────

    @Test
    @DisplayName("handleRegistrationException returns 400 with error message")
    void handleRegistrationException_returns400WithMessage() throws Exception {
        final RegistrationException ex = new RegistrationException("email taken");

        final ResponseEntity<?> response = handler.handleRegistrationException(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        @SuppressWarnings("unchecked")
        final Map<String, String> body = (Map<String, String>) response.getBody();
        assertNotNull(body);
        assertEquals("email taken", body.get("error"));
    }

    // ── handleAppException ─────────────────────────────────────────────────────

    @Test
    @DisplayName("handleAppException returns status from exception with error message")
    void handleAppException_returnsExceptionStatus() throws Exception {
        final AppException ex = new AppException(HttpStatus.FORBIDDEN, "access denied");

        final ResponseEntity<?> response = handler.handleAppException(ex);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        @SuppressWarnings("unchecked")
        final Map<String, String> body = (Map<String, String>) response.getBody();
        assertNotNull(body);
        assertEquals("access denied", body.get("error"));
    }

    // ── handleOtherExceptions ──────────────────────────────────────────────────

    @Test
    @DisplayName("handleOtherExceptions returns 500 with generic message")
    void handleOtherExceptions_returns500WithGenericMessage() throws Exception {
        final Exception ex = new Exception("something broke");

        final ResponseEntity<?> response = handler.handleOtherExceptions(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        @SuppressWarnings("unchecked")
        final Map<String, String> body = (Map<String, String>) response.getBody();
        assertNotNull(body);
        assertEquals("An unexpected error occurred", body.get("error"));
    }
}
