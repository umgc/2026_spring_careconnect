package com.careconnect.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class MoodTest {

    // ─── Default constructor ──────────────────────────────────────────────────

    @Test
    void defaultConstructor_createsInstance() throws Exception {
        final Mood mood = new Mood();
        assertThat(mood).isNotNull();
        assertThat(mood.getId()).isNull();
    }

    // ─── Parameterized constructor ────────────────────────────────────────────

    @Test
    void parameterizedConstructor_setsFields() throws Exception {
        final Mood mood = new Mood(42L, 4, "Happy");

        assertThat(mood.getUserId()).isEqualTo(42L);
        assertThat(mood.getScore()).isEqualTo(4);
        assertThat(mood.getLabel()).isEqualTo("Happy");
        assertThat(mood.getCreatedAt()).isNotNull();
    }

    // ─── Setters / Getters ────────────────────────────────────────────────────

    @Test
    void settersAndGetters_updateFields() throws Exception {
        final Mood mood = new Mood();
        final LocalDateTime now = LocalDateTime.now();

        mood.setUserId(99L);
        mood.setScore(3);
        mood.setLabel("Neutral");
        mood.setCreatedAt(now);

        assertThat(mood.getUserId()).isEqualTo(99L);
        assertThat(mood.getScore()).isEqualTo(3);
        assertThat(mood.getLabel()).isEqualTo("Neutral");
        assertThat(mood.getCreatedAt()).isEqualTo(now);
    }
}
