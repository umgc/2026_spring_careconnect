package com.careconnect.dto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class LeaderboardEntryTest {

    // ─── Constructor ──────────────────────────────────────────────────────────

    @Test
    void constructor_setsAllFields() throws Exception {
        final LeaderboardEntry entry = new LeaderboardEntry(1L, "Smith", "John", 500, 3, "http://img.example.com/avatar.png");

        assertThat(entry.getUserId()).isEqualTo(1L);
        assertThat(entry.getXp()).isEqualTo(500);
        assertThat(entry.getLevel()).isEqualTo(3);
        assertThat(entry.getProfileImageUrl()).isEqualTo("http://img.example.com/avatar.png");
    }

    @Test
    void constructor_nullProfileImageUrl_isNull() throws Exception {
        final LeaderboardEntry entry = new LeaderboardEntry(2L, "Doe", "Jane", 100, 1, null);

        assertThat(entry.getProfileImageUrl()).isNull();
    }

    // ─── getName: concatenates lastName + " " + firstName ─────────────────────

    @Test
    void getName_concatenatesLastNameAndFirstName() throws Exception {
        final LeaderboardEntry entry = new LeaderboardEntry(3L, "Williams", "Mary", 250, 2, null);

        assertThat(entry.getName()).isEqualTo("Williams Mary");
    }

    @Test
    void getName_withEmptyFirstName_concatenatesCorrectly() throws Exception {
        final LeaderboardEntry entry = new LeaderboardEntry(4L, "Brown", "", 0, 0, null);

        assertThat(entry.getName()).isEqualTo("Brown ");
    }

    // ─── XP and level edge cases ──────────────────────────────────────────────

    @Test
    void constructor_zeroXpAndLevel_storesZero() throws Exception {
        final LeaderboardEntry entry = new LeaderboardEntry(5L, "Green", "Tom", 0, 0, null);

        assertThat(entry.getXp()).isZero();
        assertThat(entry.getLevel()).isZero();
    }

    @Test
    void constructor_highXpAndLevel_storesCorrectly() throws Exception {
        final LeaderboardEntry entry = new LeaderboardEntry(6L, "Hall", "Anna", Integer.MAX_VALUE, 100, null);

        assertThat(entry.getXp()).isEqualTo(Integer.MAX_VALUE);
        assertThat(entry.getLevel()).isEqualTo(100);
    }
}
