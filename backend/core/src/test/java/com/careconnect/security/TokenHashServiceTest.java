package com.careconnect.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TokenHashServiceTest {

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private TokenHashService tokenHashService;

    private String plainToken;
    private String hashedToken;

    @BeforeEach
    void setUp() throws Exception {
        plainToken = "test-refresh-token";
        hashedToken = "hashed-token-value";
    }

    @Test
    void hashToken_ShouldReturnHashedToken_WhenTokenIsValid() throws Exception {
        // Arrange
        when(passwordEncoder.encode(plainToken)).thenReturn(hashedToken);

        // Act
        String result = tokenHashService.hashToken(plainToken);

        // Assert
        assertEquals(hashedToken, result);
        verify(passwordEncoder).encode(plainToken);
    }

    @Test
    void hashToken_ShouldReturnNull_WhenTokenIsNull() throws Exception {
        // Act
        String result = tokenHashService.hashToken(null);

        // Assert
        assertNull(result);
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void hashToken_ShouldReturnNull_WhenTokenIsBlank() throws Exception {
        // Act
        String result = tokenHashService.hashToken("");

        // Assert
        assertNull(result);
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void hashToken_ShouldReturnNull_WhenTokenIsWhitespace() throws Exception {
        // Act
        String result = tokenHashService.hashToken("   ");

        // Assert
        assertNull(result);
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void verifyToken_ShouldReturnTrue_WhenTokensMatch() throws Exception {
        // Arrange
        when(passwordEncoder.matches(plainToken, hashedToken)).thenReturn(true);

        // Act
        boolean result = tokenHashService.verifyToken(plainToken, hashedToken);

        // Assert
        assertTrue(result);
        verify(passwordEncoder).matches(plainToken, hashedToken);
    }

    @Test
    void verifyToken_ShouldReturnFalse_WhenTokensDoNotMatch() throws Exception {
        // Arrange
        when(passwordEncoder.matches(plainToken, hashedToken)).thenReturn(false);

        // Act
        boolean result = tokenHashService.verifyToken(plainToken, hashedToken);

        // Assert
        assertFalse(result);
        verify(passwordEncoder).matches(plainToken, hashedToken);
    }

    @Test
    void verifyToken_ShouldReturnFalse_WhenPlainTokenIsNull() throws Exception {
        // Act
        boolean result = tokenHashService.verifyToken(null, hashedToken);

        // Assert
        assertFalse(result);
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    void verifyToken_ShouldReturnFalse_WhenHashedTokenIsNull() throws Exception {
        // Act
        boolean result = tokenHashService.verifyToken(plainToken, null);

        // Assert
        assertFalse(result);
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }
}