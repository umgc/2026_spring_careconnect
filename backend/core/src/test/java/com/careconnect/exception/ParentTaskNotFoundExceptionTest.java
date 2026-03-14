package com.careconnect.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ParentTaskNotFoundException")
class ParentTaskNotFoundExceptionTest {

    @Test
    @DisplayName("constructor formats message with parentTaskId")
    void constructor_formatsMessage() throws Exception {
        final ParentTaskNotFoundException ex = new ParentTaskNotFoundException(42L);
        assertEquals("Parent task not found: id=42", ex.getMessage());
        assertInstanceOf(NotFoundException.class, ex);
    }
}
