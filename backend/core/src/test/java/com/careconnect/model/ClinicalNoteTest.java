package com.careconnect.model;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ClinicalNoteTest {

    // ─── No-arg constructor ───────────────────────────────────────────────────

    @Test
    void noArgConstructor_createsInstance() throws Exception {
        final ClinicalNote note = new ClinicalNote();

        assertThat(note).isNotNull();
        assertThat(note.getId()).isNull();
        assertThat(note.getPatientId()).isNull();
        assertThat(note.getCaregiverId()).isNull();
        assertThat(note.getNoteType()).isNull();
        assertThat(note.getContent()).isNull();
        assertThat(note.getSubject()).isNull();
        assertThat(note.getUpdatedAt()).isNull();
    }

    // ─── Builder defaults ─────────────────────────────────────────────────────

    @Test
    void builder_isActive_defaultsToTrue() throws Exception {
        final ClinicalNote note = ClinicalNote.builder().patientId(1L).build();
        assertThat(note.getIsActive()).isTrue();
    }

    @Test
    void builder_createdAt_defaultsToNow() throws Exception {
        final ClinicalNote note = ClinicalNote.builder().patientId(1L).build();
        assertThat(note.getCreatedAt()).isNotNull();
    }

    // ─── Builder all fields ───────────────────────────────────────────────────

    @Test
    void builder_allFields() throws Exception {
        final LocalDateTime now = LocalDateTime.now();

        final ClinicalNote note = ClinicalNote.builder()
                .id(1L)
                .patientId(10L)
                .caregiverId(20L)
                .noteType("ASSESSMENT")
                .content("Patient appears stable.")
                .subject("Routine check")
                .isActive(true)
                .createdAt(now)
                .updatedAt(now)
                .build();

        assertThat(note.getId()).isEqualTo(1L);
        assertThat(note.getPatientId()).isEqualTo(10L);
        assertThat(note.getCaregiverId()).isEqualTo(20L);
        assertThat(note.getNoteType()).isEqualTo("ASSESSMENT");
        assertThat(note.getContent()).isEqualTo("Patient appears stable.");
        assertThat(note.getSubject()).isEqualTo("Routine check");
        assertThat(note.getIsActive()).isTrue();
        assertThat(note.getCreatedAt()).isEqualTo(now);
        assertThat(note.getUpdatedAt()).isEqualTo(now);
    }

    // ─── Setters ──────────────────────────────────────────────────────────────

    @Test
    void setters_updateFields() throws Exception {
        final ClinicalNote note = new ClinicalNote();
        final LocalDateTime now = LocalDateTime.now();

        note.setId(2L);
        note.setPatientId(30L);
        note.setCaregiverId(40L);
        note.setNoteType("PLAN");
        note.setContent("Adjusted medication.");
        note.setSubject("Medication Update");
        note.setIsActive(false);
        note.setCreatedAt(now);
        note.setUpdatedAt(now);

        assertThat(note.getId()).isEqualTo(2L);
        assertThat(note.getPatientId()).isEqualTo(30L);
        assertThat(note.getCaregiverId()).isEqualTo(40L);
        assertThat(note.getNoteType()).isEqualTo("PLAN");
        assertThat(note.getContent()).isEqualTo("Adjusted medication.");
        assertThat(note.getSubject()).isEqualTo("Medication Update");
        assertThat(note.getIsActive()).isFalse();
    }

    // ─── @PreUpdate: preUpdate() ──────────────────────────────────────────────

    @Test
    void preUpdate_setsUpdatedAt() throws Exception {
        final ClinicalNote note = new ClinicalNote();

        final Method m = ClinicalNote.class.getDeclaredMethod("preUpdate");
        m.setAccessible(true);
        m.invoke(note);

        assertThat(note.getUpdatedAt()).isNotNull();
    }

    // ─── equals() and hashCode() ──────────────────────────────────────────────

    @Test
    void equals_sameFields_returnsTrue() throws Exception {
        final LocalDateTime ts = LocalDateTime.now();
        final ClinicalNote n1 = ClinicalNote.builder().id(1L).patientId(10L).createdAt(ts).build();
        final ClinicalNote n2 = ClinicalNote.builder().id(1L).patientId(10L).createdAt(ts).build();

        assertThat(n1).isEqualTo(n2);
        assertThat(n1.hashCode()).isEqualTo(n2.hashCode());
    }
}
