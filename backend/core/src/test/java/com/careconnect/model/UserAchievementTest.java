package com.careconnect.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class UserAchievementTest {

    // ─── Default constructor ──────────────────────────────────────────────────

    @Test
    void defaultConstructor_createsInstance() throws Exception {
        final UserAchievement ua = new UserAchievement();

        assertThat(ua).isNotNull();
        assertThat(ua.getId()).isNull();
        assertThat(ua.getUserId()).isNull();
        assertThat(ua.getAchievement()).isNull();
        assertThat(ua.getEarnedAt()).isNotNull(); // defaults to now
    }

    // ─── All-arg constructor ──────────────────────────────────────────────────

    @Test
    void allArgConstructor_setsAllFields() throws Exception {
        final Achievement achievement = new Achievement(5L, "First Login", "Earned on first login", null);
        final LocalDateTime now = LocalDateTime.now();

        final UserAchievement ua = new UserAchievement(1L, 10L, achievement, now);

        assertThat(ua.getId()).isEqualTo(1L);
        assertThat(ua.getUserId()).isEqualTo(10L);
        assertThat(ua.getAchievement()).isSameAs(achievement);
        assertThat(ua.getEarnedAt()).isEqualTo(now);
    }

    // ─── Setters ──────────────────────────────────────────────────────────────

    @Test
    void setters_updateFields() throws Exception {
        final UserAchievement ua = new UserAchievement();
        final Achievement achievement = new Achievement(3L, "Streak", "Login streak", null);
        final LocalDateTime now = LocalDateTime.now();

        ua.setUserId(20L);
        ua.setAchievement(achievement);
        ua.setEarnedAt(now);

        assertThat(ua.getUserId()).isEqualTo(20L);
        assertThat(ua.getAchievement()).isSameAs(achievement);
        assertThat(ua.getEarnedAt()).isEqualTo(now);
    }
}
