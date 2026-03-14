package com.careconnect.exception;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class AppExceptionTest {

    // ─── Constructor ──────────────────────────────────────────────────────────

    @Test
    void constructor_setsStatusAndMessage() throws Exception {
        final AppException exception = new AppException(HttpStatus.NOT_FOUND, "Resource not found");

        assertThat(exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exception.getMessage()).isEqualTo("Resource not found");
    }

    @Test
    void constructor_internalServerError_setsCorrectly() throws Exception {
        final AppException exception = new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "Server error");

        assertThat(exception.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(exception.getMessage()).isEqualTo("Server error");
    }

    @Test
    void constructor_unauthorized_setsCorrectly() throws Exception {
        final AppException exception = new AppException(HttpStatus.UNAUTHORIZED, "Access denied");

        assertThat(exception.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exception.getMessage()).isEqualTo("Access denied");
    }

    // ─── Is a RuntimeException ────────────────────────────────────────────────

    @Test
    void appException_isRuntimeException() throws Exception {
        final AppException exception = new AppException(HttpStatus.BAD_REQUEST, "Bad request");

        assertThat(exception).isInstanceOf(RuntimeException.class);
    }

    @Test
    void appException_canBeThrown() throws Exception {
        assertThatThrownBy(() -> {
            throw new AppException(HttpStatus.FORBIDDEN, "Forbidden");
        })
                .isInstanceOf(AppException.class)
                .hasMessage("Forbidden");
    }
}
