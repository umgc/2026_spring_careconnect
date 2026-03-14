package com.careconnect.dto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class InboxMessageDtoTest {

    // ─── No-arg constructor ───────────────────────────────────────────────────

    @Test
    void noArgConstructor_fieldsAreNull() throws Exception {
        final InboxMessageDto dto = new InboxMessageDto();

        assertThat(dto.getMessageId()).isNull();
        assertThat(dto.getPeerId()).isNull();
        assertThat(dto.getPeerName()).isNull();
        assertThat(dto.getPeerEmail()).isNull();
        assertThat(dto.getContent()).isNull();
        assertThat(dto.getTimestamp()).isNull();
    }

    // ─── All-args constructor ─────────────────────────────────────────────────

    @Test
    void allArgsConstructor_setsAllFields() throws Exception {
        final LocalDateTime ts = LocalDateTime.of(2026, 3, 15, 9, 0);

        final InboxMessageDto dto = new InboxMessageDto(1L, 2L, "Alice Smith", "alice@example.com", "CAREGIVER", "Hello!", ts, false);

        assertThat(dto.getMessageId()).isEqualTo(1L);
        assertThat(dto.getPeerId()).isEqualTo(2L);
        assertThat(dto.getPeerName()).isEqualTo("Alice Smith");
        assertThat(dto.getPeerEmail()).isEqualTo("alice@example.com");
        assertThat(dto.getContent()).isEqualTo("Hello!");
        assertThat(dto.getTimestamp()).isEqualTo(ts);
    }

    // ─── Setters and getters ──────────────────────────────────────────────────

    @Test
    void setMessageId_getMessageId_roundTrips() throws Exception {
        final InboxMessageDto dto = new InboxMessageDto();
        dto.setMessageId(42L);
        assertThat(dto.getMessageId()).isEqualTo(42L);
    }

    @Test
    void setPeerId_getPeerId_roundTrips() throws Exception {
        final InboxMessageDto dto = new InboxMessageDto();
        dto.setPeerId(10L);
        assertThat(dto.getPeerId()).isEqualTo(10L);
    }

    @Test
    void setPeerName_getPeerName_roundTrips() throws Exception {
        final InboxMessageDto dto = new InboxMessageDto();
        dto.setPeerName("Bob Jones");
        assertThat(dto.getPeerName()).isEqualTo("Bob Jones");
    }

    @Test
    void setPeerEmail_getPeerEmail_roundTrips() throws Exception {
        final InboxMessageDto dto = new InboxMessageDto();
        dto.setPeerEmail("bob@example.com");
        assertThat(dto.getPeerEmail()).isEqualTo("bob@example.com");
    }

    @Test
    void setContent_getContent_roundTrips() throws Exception {
        final InboxMessageDto dto = new InboxMessageDto();
        dto.setContent("Meeting at 3pm");
        assertThat(dto.getContent()).isEqualTo("Meeting at 3pm");
    }

    @Test
    void setTimestamp_getTimestamp_roundTrips() throws Exception {
        final InboxMessageDto dto = new InboxMessageDto();
        final LocalDateTime ts = LocalDateTime.of(2026, 4, 1, 12, 30);
        dto.setTimestamp(ts);
        assertThat(dto.getTimestamp()).isEqualTo(ts);
    }
}
