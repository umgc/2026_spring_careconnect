package com.careconnect.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TaskNotFoundException")
class TaskNotFoundExceptionTest {

    @Test
    @DisplayName("constructor with Long formats message with taskId")
    void longConstructor_formatsMessage() throws Exception {
        final TaskNotFoundException ex = new TaskNotFoundException(7L);
        assertEquals("Task not found: id=7", ex.getMessage());
        assertInstanceOf(NotFoundException.class, ex);
    }

    @Test
    @DisplayName("constructor with String sets custom message")
    void stringConstructor_setsMessage() throws Exception {
        final TaskNotFoundException ex = new TaskNotFoundException("gone");
        assertEquals("gone", ex.getMessage());
    }
}
