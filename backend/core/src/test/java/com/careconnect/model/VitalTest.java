package com.careconnect.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class VitalTest {

    // ─── No-arg constructor ───────────────────────────────────────────────────

    @Test
    void noArgConstructor_createsInstance() throws Exception {
        final Vital v = new Vital();

        assertThat(v).isNotNull();
        assertThat(v.getId()).isNull();
        assertThat(v.getPatientId()).isNull();
        assertThat(v.getVitalType()).isNull();
        assertThat(v.getValue()).isNull();
        assertThat(v.getUnit()).isNull();
        assertThat(v.getRecordedAt()).isNotNull(); // @Builder.Default initialises in no-arg ctor
        assertThat(v.getRecordedBy()).isNull();
        assertThat(v.getNotes()).isNull();
        assertThat(v.getIsAbnormal()).isFalse(); // @Builder.Default initialises in no-arg ctor
        assertThat(v.getCreatedAt()).isNotNull();  // @Builder.Default initialises in no-arg ctor
    }

    // ─── Builder defaults ─────────────────────────────────────────────────────

    @Test
    void builder_defaults() throws Exception {
        final Vital v = Vital.builder()
                .patientId(1L)
                .vitalType("HEART_RATE")
                .value("72")
                .build();

        assertThat(v.getRecordedAt()).isNotNull();
        assertThat(v.getIsAbnormal()).isFalse();
        assertThat(v.getCreatedAt()).isNotNull();
    }

    // ─── Builder all fields ───────────────────────────────────────────────────

    @Test
    void builder_allFields() throws Exception {
        final LocalDateTime now = LocalDateTime.now();

        final Vital v = Vital.builder()
                .id(1L)
                .patientId(5L)
                .vitalType("BLOOD_PRESSURE")
                .value("120/80")
                .unit("mmHg")
                .recordedAt(now)
                .recordedBy(10L)
                .notes("Normal reading")
                .isAbnormal(false)
                .createdAt(now)
                .build();

        assertThat(v.getId()).isEqualTo(1L);
        assertThat(v.getPatientId()).isEqualTo(5L);
        assertThat(v.getVitalType()).isEqualTo("BLOOD_PRESSURE");
        assertThat(v.getValue()).isEqualTo("120/80");
        assertThat(v.getUnit()).isEqualTo("mmHg");
        assertThat(v.getRecordedAt()).isEqualTo(now);
        assertThat(v.getRecordedBy()).isEqualTo(10L);
        assertThat(v.getNotes()).isEqualTo("Normal reading");
        assertThat(v.getIsAbnormal()).isFalse();
        assertThat(v.getCreatedAt()).isEqualTo(now);
    }

    // ─── Setters ──────────────────────────────────────────────────────────────

    @Test
    void setters_updateFields() throws Exception {
        final Vital v = new Vital();

        v.setPatientId(7L);
        v.setVitalType("TEMPERATURE");
        v.setValue("98.6");
        v.setUnit("°F");
        v.setIsAbnormal(true);
        v.setNotes("Slight fever");

        assertThat(v.getPatientId()).isEqualTo(7L);
        assertThat(v.getVitalType()).isEqualTo("TEMPERATURE");
        assertThat(v.getValue()).isEqualTo("98.6");
        assertThat(v.getUnit()).isEqualTo("°F");
        assertThat(v.getIsAbnormal()).isTrue();
        assertThat(v.getNotes()).isEqualTo("Slight fever");
    }

    // ─── equals() and hashCode() ──────────────────────────────────────────────

    @Test
    void equals_sameFields_returnsTrue() throws Exception {
        final LocalDateTime now = LocalDateTime.now();
        final Vital v1 = Vital.builder()
                .id(1L).patientId(5L).vitalType("HR").value("72")
                .recordedAt(now).isAbnormal(false).createdAt(now).build();
        final Vital v2 = Vital.builder()
                .id(1L).patientId(5L).vitalType("HR").value("72")
                .recordedAt(now).isAbnormal(false).createdAt(now).build();

        assertThat(v1).isEqualTo(v2);
        assertThat(v1.hashCode()).isEqualTo(v2.hashCode());
    }
}
