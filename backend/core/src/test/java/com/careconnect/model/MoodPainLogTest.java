package com.careconnect.model;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class MoodPainLogTest {

    // ─── No-arg constructor ───────────────────────────────────────────────────

    @Test
    void noArgConstructor_createsInstance() throws Exception {
        final MoodPainLog log = new MoodPainLog();

        assertThat(log).isNotNull();
        assertThat(log.getId()).isNull();
        assertThat(log.getPatient()).isNull();
        assertThat(log.getMoodValue()).isNull();
        assertThat(log.getPainValue()).isNull();
    }

    // ─── Builder all fields ───────────────────────────────────────────────────

    @Test
    void builder_allFields() throws Exception {
        final Patient patient = new Patient();
        final LocalDateTime now = LocalDateTime.now();

        final MoodPainLog log = MoodPainLog.builder()
                .id(1L)
                .patient(patient)
                .moodValue(7)
                .painValue(3)
                .note("Feeling okay")
                .timestamp(now)
                .createdAt(now)
                .updatedAt(now)
                .build();

        assertThat(log.getId()).isEqualTo(1L);
        assertThat(log.getPatient()).isSameAs(patient);
        assertThat(log.getMoodValue()).isEqualTo(7);
        assertThat(log.getPainValue()).isEqualTo(3);
        assertThat(log.getNote()).isEqualTo("Feeling okay");
        assertThat(log.getTimestamp()).isEqualTo(now);
        assertThat(log.getCreatedAt()).isEqualTo(now);
        assertThat(log.getUpdatedAt()).isEqualTo(now);
    }

    // ─── Setters ──────────────────────────────────────────────────────────────

    @Test
    void setters_updateFields() throws Exception {
        final MoodPainLog log = new MoodPainLog();
        final Patient patient = new Patient();
        final LocalDateTime now = LocalDateTime.now();

        log.setId(2L);
        log.setPatient(patient);
        log.setMoodValue(5);
        log.setPainValue(8);
        log.setNote("High pain");
        log.setTimestamp(now);

        assertThat(log.getId()).isEqualTo(2L);
        assertThat(log.getMoodValue()).isEqualTo(5);
        assertThat(log.getPainValue()).isEqualTo(8);
        assertThat(log.getNote()).isEqualTo("High pain");
        assertThat(log.getTimestamp()).isEqualTo(now);
    }

    // ─── @PrePersist: onCreate() ──────────────────────────────────────────────

    @Test
    void onCreate_setsTimestamps() throws Exception {
        final MoodPainLog log = new MoodPainLog();

        final Method m = MoodPainLog.class.getDeclaredMethod("onCreate");
        m.setAccessible(true);
        m.invoke(log);

        assertThat(log.getCreatedAt()).isNotNull();
        assertThat(log.getUpdatedAt()).isNotNull();
    }

    // ─── @PreUpdate: onUpdate() ───────────────────────────────────────────────

    @Test
    void onUpdate_setsUpdatedAt() throws Exception {
        final MoodPainLog log = new MoodPainLog();

        final Method m = MoodPainLog.class.getDeclaredMethod("onUpdate");
        m.setAccessible(true);
        m.invoke(log);

        assertThat(log.getUpdatedAt()).isNotNull();
    }
}
