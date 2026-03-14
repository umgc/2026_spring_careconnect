package com.careconnect.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class UnauthorizedExceptionTest {

    @Test
    @DisplayName("Constructor with message should set message")
    void constructorWithMessage() {
        UnauthorizedException ex = new UnauthorizedException("Access denied");
        assertEquals("Access denied", ex.getMessage());
        assertNull(ex.getCause());
    }

    @Test
    @DisplayName("Constructor with message and cause should set both")
    void constructorWithMessageAndCause() {
        RuntimeException cause = new RuntimeException("root cause");
        UnauthorizedException ex = new UnauthorizedException("Access denied", cause);
        assertEquals("Access denied", ex.getMessage());
        assertEquals(cause, ex.getCause());
    }

    @Test
    @DisplayName("forPermission should create exception with formatted message")
    void forPermissionShouldCreateFormattedException() {
        UnauthorizedException ex = UnauthorizedException.forPermission("user@test.com", Permission.CREATE_TASKS);
        assertTrue(ex.getMessage().contains("user@test.com"));
        assertTrue(ex.getMessage().contains("CREATE_TASKS"));
        assertTrue(ex.getMessage().contains("Create tasks for patients"));
    }

    @Test
    @DisplayName("forRole should create exception with formatted message")
    void forRoleShouldCreateFormattedException() {
        UnauthorizedException ex = UnauthorizedException.forRole("user@test.com", Role.ADMIN, Role.PATIENT);
        assertTrue(ex.getMessage().contains("user@test.com"));
        assertTrue(ex.getMessage().contains("Administrator"));
        assertTrue(ex.getMessage().contains("Patient"));
    }

    @Test
    @DisplayName("Should be a checked exception extending Exception")
    void shouldBeCheckedException() {
        UnauthorizedException ex = new UnauthorizedException("test");
        assertInstanceOf(Exception.class, ex);
    }
}
