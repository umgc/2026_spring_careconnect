package com.careconnect.model;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PatientNoteTest {

    // ─── No-arg constructor ───────────────────────────────────────────────────

    @Test
    void noArgConstructor_createsInstance() throws Exception {
        final PatientNote note = new PatientNote();

        assertThat(note).isNotNull();
        assertThat(note.getId()).isNull();
        assertThat(note.getPatientId()).isNull();
        assertThat(note.getNote()).isNull();
        assertThat(note.getAiSummary()).isNull();
        assertThat(note.getCreatedAt()).isNull();
        assertThat(note.getUpdatedAt()).isNull();
    }

    // ─── Builder all fields ───────────────────────────────────────────────────

    @Test
    void builder_allFields() throws Exception {
        final LocalDateTime now = LocalDateTime.now();

        final PatientNote note = PatientNote.builder()
                .id(1L)
                .patientId(10L)
                .note("Patient reports feeling better.")
                .aiSummary("Improvement noted.")
                .createdAt(now)
                .updatedAt(now)
                .build();

        assertThat(note.getId()).isEqualTo(1L);
        assertThat(note.getPatientId()).isEqualTo(10L);
        assertThat(note.getNote()).isEqualTo("Patient reports feeling better.");
        assertThat(note.getAiSummary()).isEqualTo("Improvement noted.");
        assertThat(note.getCreatedAt()).isEqualTo(now);
        assertThat(note.getUpdatedAt()).isEqualTo(now);
    }

    // ─── Setters ──────────────────────────────────────────────────────────────

    @Test
    void setters_updateFields() throws Exception {
        final PatientNote note = new PatientNote();
        final LocalDateTime now = LocalDateTime.now();

        note.setId(2L);
        note.setPatientId(20L);
        note.setNote("New note content.");
        note.setAiSummary("New summary.");
        note.setCreatedAt(now);
        note.setUpdatedAt(now);

        assertThat(note.getId()).isEqualTo(2L);
        assertThat(note.getPatientId()).isEqualTo(20L);
        assertThat(note.getNote()).isEqualTo("New note content.");
        assertThat(note.getAiSummary()).isEqualTo("New summary.");
    }

    // ─── @PrePersist: onCreate() ──────────────────────────────────────────────

    @Test
    void onCreate_setsTimestamps() throws Exception {
        final PatientNote note = new PatientNote();

        final Method m = PatientNote.class.getDeclaredMethod("onCreate");
        m.setAccessible(true);
        m.invoke(note);

        assertThat(note.getCreatedAt()).isNotNull();
        assertThat(note.getUpdatedAt()).isNotNull();
    }

    // ─── @PreUpdate: onUpdate() ───────────────────────────────────────────────

    @Test
    void onUpdate_setsUpdatedAt() throws Exception {
        final PatientNote note = new PatientNote();

        final Method m = PatientNote.class.getDeclaredMethod("onUpdate");
        m.setAccessible(true);
        m.invoke(note);

        assertThat(note.getUpdatedAt()).isNotNull();
    }

    // ─── equals() and hashCode() ──────────────────────────────────────────────

    @Test
    void equals_sameFields_returnsTrue() throws Exception {
        final LocalDateTime now = LocalDateTime.now();
        final PatientNote n1 = PatientNote.builder().id(1L).patientId(10L).note("hello").createdAt(now).updatedAt(now).build();
        final PatientNote n2 = PatientNote.builder().id(1L).patientId(10L).note("hello").createdAt(now).updatedAt(now).build();

        assertThat(n1).isEqualTo(n2);
        assertThat(n1.hashCode()).isEqualTo(n2.hashCode());
    }
}
