package com.careconnect.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class XPProgressTest {

    // ─── Default constructor ──────────────────────────────────────────────────

    @Test
    void defaultConstructor_createsInstance() throws Exception {
        final XPProgress progress = new XPProgress();

        assertThat(progress).isNotNull();
        assertThat(progress.getId()).isNull();
        assertThat(progress.getXp()).isZero();
        assertThat(progress.getLevel()).isZero();
        assertThat(progress.getUserId()).isNull();
        assertThat(progress.getUpdatedAt()).isNotNull(); // defaults to now
    }

    // ─── All-arg constructor ──────────────────────────────────────────────────

    @Test
    void allArgConstructor_setsAllFields() throws Exception {
        final LocalDateTime now = LocalDateTime.now();
        final XPProgress progress = new XPProgress(1L, 500, 3, 42L, now);

        assertThat(progress.getId()).isEqualTo(1L);
        assertThat(progress.getXp()).isEqualTo(500);
        assertThat(progress.getLevel()).isEqualTo(3);
        assertThat(progress.getUserId()).isEqualTo(42L);
        assertThat(progress.getUpdatedAt()).isEqualTo(now);
    }

    // ─── Setters ──────────────────────────────────────────────────────────────

    @Test
    void setters_updateFields() throws Exception {
        final XPProgress progress = new XPProgress();
        final LocalDateTime now = LocalDateTime.now();

        progress.setUserId(99L);
        progress.setXp(1000);
        progress.setLevel(5);
        progress.setUpdatedAt(now);

        assertThat(progress.getUserId()).isEqualTo(99L);
        assertThat(progress.getXp()).isEqualTo(1000);
        assertThat(progress.getLevel()).isEqualTo(5);
        assertThat(progress.getUpdatedAt()).isEqualTo(now);
    }
}
