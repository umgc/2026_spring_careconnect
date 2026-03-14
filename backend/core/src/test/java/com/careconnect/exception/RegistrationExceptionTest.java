package com.careconnect.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RegistrationException")
class RegistrationExceptionTest {

    @Test
    @DisplayName("constructor sets message and extends RuntimeException")
    void messageConstructor_setsMessage() throws Exception {
        final RegistrationException ex = new RegistrationException("dup email");
        assertEquals("dup email", ex.getMessage());
        assertInstanceOf(RuntimeException.class, ex);
    }
}
