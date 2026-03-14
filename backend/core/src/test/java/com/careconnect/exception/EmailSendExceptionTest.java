package com.careconnect.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("EmailSendException")
class EmailSendExceptionTest {

    @Test
    @DisplayName("can be instantiated")
    void constructor_createsInstance() throws Exception {
        assertNotNull(new EmailSendException());
    }
}
