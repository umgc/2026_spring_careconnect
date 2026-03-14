package com.careconnect.dto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TokenDtoTest {

    // ─── Constructor ──────────────────────────────────────────────────────────

    @Test
    void constructor_setsToken() throws Exception {
        final TokenDto dto = new TokenDto("jwt-token-abc");

        assertThat(dto.getToken()).isEqualTo("jwt-token-abc");
    }

    @Test
    void constructor_nullToken_setsNullToken() throws Exception {
        final TokenDto dto = new TokenDto(null);

        assertThat(dto.getToken()).isNull();
    }

    // ─── Setter and Getter ────────────────────────────────────────────────────

    @Test
    void setToken_getToken_roundTrips() throws Exception {
        final TokenDto dto = new TokenDto("initial-token");
        dto.setToken("updated-token");
        assertThat(dto.getToken()).isEqualTo("updated-token");
    }

    @Test
    void setToken_null_returnsNull() throws Exception {
        final TokenDto dto = new TokenDto("some-token");
        dto.setToken(null);
        assertThat(dto.getToken()).isNull();
    }
}
