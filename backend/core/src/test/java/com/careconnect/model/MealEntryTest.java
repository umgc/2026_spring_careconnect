package com.careconnect.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class MealEntryTest {

    // ─── No-arg constructor ───────────────────────────────────────────────────

    @Test
    void noArgConstructor_createsInstance() throws Exception {
        final MealEntry entry = new MealEntry();

        assertThat(entry).isNotNull();
        assertThat(entry.getId()).isNull();
        assertThat(entry.getPatient()).isNull();
        assertThat(entry.getCaregiver()).isNull();
        assertThat(entry.getCalories()).isNull();
        assertThat(entry.getTakenAt()).isNull();
    }

    // ─── Builder all fields ───────────────────────────────────────────────────

    @Test
    void builder_allFields() throws Exception {
        final Patient patient = new Patient();
        final Caregiver caregiver = new Caregiver();
        final Instant now = Instant.now();

        final MealEntry entry = MealEntry.builder()
                .id(1L)
                .patient(patient)
                .caregiver(caregiver)
                .calories(500)
                .takenAt(now)
                .build();

        assertThat(entry.getId()).isEqualTo(1L);
        assertThat(entry.getPatient()).isSameAs(patient);
        assertThat(entry.getCaregiver()).isSameAs(caregiver);
        assertThat(entry.getCalories()).isEqualTo(500);
        assertThat(entry.getTakenAt()).isEqualTo(now);
    }

    // ─── Setters ──────────────────────────────────────────────────────────────

    @Test
    void setters_updateFields() throws Exception {
        final MealEntry entry = new MealEntry();
        final Patient patient = new Patient();
        final Caregiver caregiver = new Caregiver();
        final Instant now = Instant.now();

        entry.setId(2L);
        entry.setPatient(patient);
        entry.setCaregiver(caregiver);
        entry.setCalories(300);
        entry.setTakenAt(now);

        assertThat(entry.getId()).isEqualTo(2L);
        assertThat(entry.getPatient()).isSameAs(patient);
        assertThat(entry.getCaregiver()).isSameAs(caregiver);
        assertThat(entry.getCalories()).isEqualTo(300);
        assertThat(entry.getTakenAt()).isEqualTo(now);
    }

    // ─── equals() and hashCode() ──────────────────────────────────────────────

    @Test
    void equals_sameFields_returnsTrue() throws Exception {
        final MealEntry e1 = MealEntry.builder().id(1L).calories(400).build();
        final MealEntry e2 = MealEntry.builder().id(1L).calories(400).build();

        assertThat(e1).isEqualTo(e2);
        assertThat(e1.hashCode()).isEqualTo(e2.hashCode());
    }
}
