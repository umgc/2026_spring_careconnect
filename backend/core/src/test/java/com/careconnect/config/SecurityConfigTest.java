package com.careconnect.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SecurityConfigTest {

    private SecurityConfig securityConfig;

    @BeforeEach
    void setUp() {
        securityConfig = new SecurityConfig();
    }

    @Test
    void passwordEncoder_IsCreated() {
        assertNotNull(securityConfig.passwordEncoder());
    }

    @Test
    void passwordEncoder_ReturnsBCrypt() {
        var encoder = securityConfig.passwordEncoder();
        assertInstanceOf(
                org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder.class,
                encoder
        );
    }

    @Test
    void passwordEncoder_CanEncodeAndMatch() {
        var encoder = securityConfig.passwordEncoder();
        String raw = "testPassword123";
        String encoded = encoder.encode(raw);

        assertNotEquals(raw, encoded);
        assertTrue(encoder.matches(raw, encoded));
    }

    @Test
    void passwordEncoder_RejectsMismatch() {
        var encoder = securityConfig.passwordEncoder();
        String encoded = encoder.encode("correctPassword");

        assertFalse(encoder.matches("wrongPassword", encoded));
    }

    @Test
    void passwordEncoder_ProducesDifferentHashesForSameInput() {
        var encoder = securityConfig.passwordEncoder();
        String raw = "samePassword";
        String hash1 = encoder.encode(raw);
        String hash2 = encoder.encode(raw);

        // BCrypt uses random salt, so hashes should differ
        assertNotEquals(hash1, hash2);
        // But both should still match the original
        assertTrue(encoder.matches(raw, hash1));
        assertTrue(encoder.matches(raw, hash2));
    }
}