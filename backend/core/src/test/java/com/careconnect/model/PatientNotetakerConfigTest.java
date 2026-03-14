package com.careconnect.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PatientNotetakerConfigTest {

    // ─── No-arg constructor ───────────────────────────────────────────────────

    @Test
    void noArgConstructor_createsInstance() throws Exception {
        final PatientNotetakerConfig config = new PatientNotetakerConfig();

        assertThat(config).isNotNull();
        assertThat(config.getId()).isNull();
        assertThat(config.getPatientId()).isNull();
        assertThat(config.getIsEnabled()).isNull();
        assertThat(config.getPermitCaregiverAccess()).isNull();
        assertThat(config.getTriggerKeywords()).isNull();
        assertThat(config.getUpdatedAt()).isNull();
    }

    // ─── Builder all fields ───────────────────────────────────────────────────

    @Test
    void builder_allFields() throws Exception {
        final LocalDateTime now = LocalDateTime.now();
        final PatientNotetakerKeyword keyword = new PatientNotetakerKeyword("chest pain", PatientNotetakerKeyword.EventType.ALERT);

        final PatientNotetakerConfig config = PatientNotetakerConfig.builder()
                .id(1L)
                .patientId(10L)
                .isEnabled(true)
                .permitCaregiverAccess(false)
                .triggerKeywords(List.of(keyword))
                .updatedAt(now)
                .build();

        assertThat(config.getId()).isEqualTo(1L);
        assertThat(config.getPatientId()).isEqualTo(10L);
        assertThat(config.getIsEnabled()).isTrue();
        assertThat(config.getPermitCaregiverAccess()).isFalse();
        assertThat(config.getTriggerKeywords()).hasSize(1);
        assertThat(config.getUpdatedAt()).isEqualTo(now);
    }

    // ─── Setters ──────────────────────────────────────────────────────────────

    @Test
    void setters_updateFields() throws Exception {
        final PatientNotetakerConfig config = new PatientNotetakerConfig();
        final LocalDateTime now = LocalDateTime.now();

        config.setId(2L);
        config.setPatientId(20L);
        config.setIsEnabled(false);
        config.setPermitCaregiverAccess(true);
        config.setTriggerKeywords(List.of());
        config.setUpdatedAt(now);

        assertThat(config.getId()).isEqualTo(2L);
        assertThat(config.getPatientId()).isEqualTo(20L);
        assertThat(config.getIsEnabled()).isFalse();
        assertThat(config.getPermitCaregiverAccess()).isTrue();
        assertThat(config.getTriggerKeywords()).isEmpty();
        assertThat(config.getUpdatedAt()).isEqualTo(now);
    }

    // ─── equals() and hashCode() ──────────────────────────────────────────────

    @Test
    void equals_sameFields_returnsTrue() throws Exception {
        final PatientNotetakerConfig c1 = PatientNotetakerConfig.builder().id(1L).patientId(10L).build();
        final PatientNotetakerConfig c2 = PatientNotetakerConfig.builder().id(1L).patientId(10L).build();

        assertThat(c1).isEqualTo(c2);
        assertThat(c1.hashCode()).isEqualTo(c2.hashCode());
    }
}
