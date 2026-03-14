package com.careconnect.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PatientNotFoundException")
class PatientNotFoundExceptionTest {

    @Test
    @DisplayName("constructor with Long formats message with patientId")
    void longConstructor_formatsMessage() throws Exception {
        final PatientNotFoundException ex = new PatientNotFoundException(99L);
        assertEquals("Patient not found: id=99", ex.getMessage());
        assertInstanceOf(NotFoundException.class, ex);
    }

    @Test
    @DisplayName("constructor with String sets custom message")
    void stringConstructor_setsMessage() throws Exception {
        final PatientNotFoundException ex = new PatientNotFoundException("custom msg");
        assertEquals("custom msg", ex.getMessage());
    }
}
