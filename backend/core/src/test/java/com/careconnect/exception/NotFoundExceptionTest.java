package com.careconnect.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("NotFoundException")
class NotFoundExceptionTest {

    @Test
    @DisplayName("constructor sets message and extends RuntimeException")
    void messageConstructor_setsMessage() throws Exception {
        final NotFoundException ex = new NotFoundException("not here");
        assertEquals("not here", ex.getMessage());
        assertInstanceOf(RuntimeException.class, ex);
    }
}
