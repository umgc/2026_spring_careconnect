package com.careconnect.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PatientNotetakerKeywordTest {

    // ─── No-arg constructor ───────────────────────────────────────────────────

    @Test
    void noArgConstructor_createsInstance() throws Exception {
        final PatientNotetakerKeyword keyword = new PatientNotetakerKeyword();

        assertThat(keyword).isNotNull();
        assertThat(keyword.getKeyword()).isNull();
        assertThat(keyword.getEventType()).isNull();
    }

    // ─── All-arg constructor ──────────────────────────────────────────────────

    @Test
    void allArgConstructor_setsAllFields() throws Exception {
        final PatientNotetakerKeyword keyword = new PatientNotetakerKeyword(
                "chest pain", PatientNotetakerKeyword.EventType.ALERT);

        assertThat(keyword.getKeyword()).isEqualTo("chest pain");
        assertThat(keyword.getEventType()).isEqualTo(PatientNotetakerKeyword.EventType.ALERT);
    }

    // ─── Builder ──────────────────────────────────────────────────────────────

    @Test
    void builder_setsFields() throws Exception {
        final PatientNotetakerKeyword keyword = PatientNotetakerKeyword.builder()
                .keyword("schedule follow-up")
                .eventType(PatientNotetakerKeyword.EventType.TASK)
                .build();

        assertThat(keyword.getKeyword()).isEqualTo("schedule follow-up");
        assertThat(keyword.getEventType()).isEqualTo(PatientNotetakerKeyword.EventType.TASK);
    }

    // ─── Setters ──────────────────────────────────────────────────────────────

    @Test
    void setters_updateFields() throws Exception {
        final PatientNotetakerKeyword keyword = new PatientNotetakerKeyword();

        keyword.setKeyword("shortness of breath");
        keyword.setEventType(PatientNotetakerKeyword.EventType.ALERT);

        assertThat(keyword.getKeyword()).isEqualTo("shortness of breath");
        assertThat(keyword.getEventType()).isEqualTo(PatientNotetakerKeyword.EventType.ALERT);
    }

    // ─── EventType enum ───────────────────────────────────────────────────────

    @Test
    void eventTypeEnum_containsAllValues() throws Exception {
        assertThat(PatientNotetakerKeyword.EventType.values()).containsExactly(
                PatientNotetakerKeyword.EventType.ALERT,
                PatientNotetakerKeyword.EventType.TASK
        );
    }
}
