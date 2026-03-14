package com.careconnect.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PatientCaregiverRelationshipTest {

    // ─── No-arg constructor ───────────────────────────────────────────────────

    @Test
    void noArgConstructor_createsInstance() throws Exception {
        final PatientCaregiverRelationship rel = new PatientCaregiverRelationship();

        assertThat(rel).isNotNull();
        assertThat(rel.getId()).isNull();
        assertThat(rel.getPatientId()).isNull();
        assertThat(rel.getCaregiverUserId()).isNull();
        assertThat(rel.getRelationshipType()).isNull();
    }

    // ─── All-arg constructor ──────────────────────────────────────────────────

    @Test
    void allArgConstructor_setsAllFields() throws Exception {
        final PatientCaregiverRelationship rel = new PatientCaregiverRelationship(1L, 10L, 20L, "PRIMARY");

        assertThat(rel.getId()).isEqualTo(1L);
        assertThat(rel.getPatientId()).isEqualTo(10L);
        assertThat(rel.getCaregiverUserId()).isEqualTo(20L);
        assertThat(rel.getRelationshipType()).isEqualTo("PRIMARY");
    }

    // ─── Builder ──────────────────────────────────────────────────────────────

    @Test
    void builder_setsFields() throws Exception {
        final PatientCaregiverRelationship rel = PatientCaregiverRelationship.builder()
                .id(2L)
                .patientId(30L)
                .caregiverUserId(40L)
                .relationshipType("SECONDARY")
                .build();

        assertThat(rel.getId()).isEqualTo(2L);
        assertThat(rel.getPatientId()).isEqualTo(30L);
        assertThat(rel.getCaregiverUserId()).isEqualTo(40L);
        assertThat(rel.getRelationshipType()).isEqualTo("SECONDARY");
    }

    // ─── Setters ──────────────────────────────────────────────────────────────

    @Test
    void setters_updateFields() throws Exception {
        final PatientCaregiverRelationship rel = new PatientCaregiverRelationship();

        rel.setId(3L);
        rel.setPatientId(50L);
        rel.setCaregiverUserId(60L);
        rel.setRelationshipType("PRIMARY");

        assertThat(rel.getId()).isEqualTo(3L);
        assertThat(rel.getPatientId()).isEqualTo(50L);
        assertThat(rel.getCaregiverUserId()).isEqualTo(60L);
        assertThat(rel.getRelationshipType()).isEqualTo("PRIMARY");
    }

    // ─── equals() and hashCode() ──────────────────────────────────────────────

    @Test
    void equals_sameFields_returnsTrue() throws Exception {
        final PatientCaregiverRelationship r1 = PatientCaregiverRelationship.builder().id(1L).patientId(10L).build();
        final PatientCaregiverRelationship r2 = PatientCaregiverRelationship.builder().id(1L).patientId(10L).build();

        assertThat(r1).isEqualTo(r2);
        assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
    }

    @Test
    void equals_differentFields_returnsFalse() throws Exception {
        final PatientCaregiverRelationship r1 = PatientCaregiverRelationship.builder().id(1L).build();
        final PatientCaregiverRelationship r2 = PatientCaregiverRelationship.builder().id(2L).build();

        assertThat(r1).isNotEqualTo(r2);
    }
}
