package com.careconnect.model;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class MedicationTest {

    // ─── No-arg constructor ───────────────────────────────────────────────────

    @Test
    void noArgConstructor_createsInstance() throws Exception {
        final Medication med = new Medication();

        assertThat(med).isNotNull();
        assertThat(med.getId()).isNull();
        assertThat(med.getPatient()).isNull();
        assertThat(med.getMedicationName()).isNull();
    }

    // ─── Builder defaults ─────────────────────────────────────────────────────

    @Test
    void builder_isActive_defaultsToTrue() throws Exception {
        final Medication med = Medication.builder()
                .patient(new Patient())
                .medicationName("Aspirin")
                .build();

        assertThat(med.getIsActive()).isTrue();
    }

    @Test
    void builder_approvalStatus_defaultsToPending() throws Exception {
        final Medication med = Medication.builder()
                .patient(new Patient())
                .medicationName("Aspirin")
                .build();

        assertThat(med.getApprovalStatus()).isEqualTo("PENDING");
    }

    // ─── Builder all fields ───────────────────────────────────────────────────

    @Test
    void builder_allFields() throws Exception {
        final Patient patient = new Patient();
        final Instant now = Instant.now();

        final Medication med = Medication.builder()
                .id(1L)
                .patient(patient)
                .medicationName("Metformin")
                .dosage("500mg")
                .frequency("twice daily")
                .route("oral")
                .medicationType(Medication.MedicationType.PRESCRIPTION)
                .prescribedBy("Dr. Adams")
                .prescribedDate("2025-01-01")
                .startDate("2025-01-02")
                .endDate("2025-06-01")
                .notes("Take with food")
                .isActive(true)
                .approvalStatus("APPROVED")
                .createdAt(now)
                .updatedAt(now)
                .build();

        assertThat(med.getId()).isEqualTo(1L);
        assertThat(med.getPatient()).isSameAs(patient);
        assertThat(med.getMedicationName()).isEqualTo("Metformin");
        assertThat(med.getDosage()).isEqualTo("500mg");
        assertThat(med.getFrequency()).isEqualTo("twice daily");
        assertThat(med.getRoute()).isEqualTo("oral");
        assertThat(med.getMedicationType()).isEqualTo(Medication.MedicationType.PRESCRIPTION);
        assertThat(med.getPrescribedBy()).isEqualTo("Dr. Adams");
        assertThat(med.getApprovalStatus()).isEqualTo("APPROVED");
        assertThat(med.getNotes()).isEqualTo("Take with food");
        assertThat(med.getIsActive()).isTrue();
        assertThat(med.getCreatedAt()).isEqualTo(now);
    }

    // ─── @PrePersist: onCreate() ──────────────────────────────────────────────

    @Test
    void onCreate_setsTimestamps() throws Exception {
        final Medication med = new Medication();

        final Method m = Medication.class.getDeclaredMethod("onCreate");
        m.setAccessible(true);
        m.invoke(med);

        assertThat(med.getCreatedAt()).isNotNull();
        assertThat(med.getUpdatedAt()).isNotNull();
    }

    @Test
    void onCreate_isActiveNull_setsToTrue() throws Exception {
        final Medication med = new Medication();
        med.setIsActive(null);   // reset the @Builder.Default true so we can test the null→true branch
        assertThat(med.getIsActive()).isNull();

        final Method m = Medication.class.getDeclaredMethod("onCreate");
        m.setAccessible(true);
        m.invoke(med);

        assertThat(med.getIsActive()).isTrue();
    }

    @Test
    void onCreate_isActiveNotNull_doesNotOverride() throws Exception {
        final Medication med = new Medication();
        med.setIsActive(false);

        final Method m = Medication.class.getDeclaredMethod("onCreate");
        m.setAccessible(true);
        m.invoke(med);

        assertThat(med.getIsActive()).isFalse();
    }

    @Test
    void onCreate_approvalStatusNull_setsToPending() throws Exception {
        final Medication med = new Medication();

        final Method m = Medication.class.getDeclaredMethod("onCreate");
        m.setAccessible(true);
        m.invoke(med);

        assertThat(med.getApprovalStatus()).isEqualTo("PENDING");
    }

    @Test
    void onCreate_approvalStatusNotNull_doesNotOverride() throws Exception {
        final Medication med = new Medication();
        med.setApprovalStatus("APPROVED");

        final Method m = Medication.class.getDeclaredMethod("onCreate");
        m.setAccessible(true);
        m.invoke(med);

        assertThat(med.getApprovalStatus()).isEqualTo("APPROVED");
    }

    // ─── @PreUpdate: onUpdate() ───────────────────────────────────────────────

    @Test
    void onUpdate_setsUpdatedAt() throws Exception {
        final Medication med = new Medication();

        final Method m = Medication.class.getDeclaredMethod("onUpdate");
        m.setAccessible(true);
        m.invoke(med);

        assertThat(med.getUpdatedAt()).isNotNull();
    }

    // ─── MedicationType enum ──────────────────────────────────────────────────

    @Test
    void medicationTypeEnum_getDisplayName() throws Exception {
        assertThat(Medication.MedicationType.PRESCRIPTION.getDisplayName()).isEqualTo("Prescription");
        assertThat(Medication.MedicationType.OVER_THE_COUNTER.getDisplayName()).isEqualTo("Over-the-counter");
        assertThat(Medication.MedicationType.SUPPLEMENT.getDisplayName()).isEqualTo("Supplement/Vitamin");
        assertThat(Medication.MedicationType.HERBAL.getDisplayName()).isEqualTo("Herbal/Natural");
        assertThat(Medication.MedicationType.EMERGENCY.getDisplayName()).isEqualTo("Emergency Medication");
    }

    @Test
    void medicationTypeEnum_containsAllValues() throws Exception {
        assertThat(Medication.MedicationType.values()).hasSize(5);
    }
}
