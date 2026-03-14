package com.careconnect.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QuestionTest {

    // ─── No-arg constructor ───────────────────────────────────────────────────

    @Test
    void noArgConstructor_createsInstance() throws Exception {
        final Question q = new Question();

        assertThat(q).isNotNull();
        assertThat(q.getId()).isNull();
        assertThat(q.getPrompt()).isNull();
        assertThat(q.getType()).isNull();
        assertThat(q.isRequired()).isFalse();
        assertThat(q.isActive()).isTrue();
        assertThat(q.getOrdinal()).isZero();
        assertThat(q.getUsedInCheckIns()).isNull();
    }

    // ─── Builder all fields ───────────────────────────────────────────────────

    @Test
    void builder_allFields() throws Exception {
        final Question q = Question.builder()
                .id(1L)
                .prompt("How are you feeling?")
                .type(QuestionType.TEXT)
                .required(true)
                .active(true)
                .ordinal(5)
                .build();

        assertThat(q.getId()).isEqualTo(1L);
        assertThat(q.getPrompt()).isEqualTo("How are you feeling?");
        assertThat(q.getType()).isEqualTo(QuestionType.TEXT);
        assertThat(q.isRequired()).isTrue();
        assertThat(q.isActive()).isTrue();
        assertThat(q.getOrdinal()).isEqualTo(5);
    }

    // ─── Setters ──────────────────────────────────────────────────────────────

    @Test
    void setters_updateFields() throws Exception {
        final Question q = new Question();

        q.setId(2L);
        q.setPrompt("Do you have pain?");
        q.setType(QuestionType.YES_NO);
        q.setRequired(true);
        q.setActive(false);
        q.setOrdinal(3);

        assertThat(q.getId()).isEqualTo(2L);
        assertThat(q.getPrompt()).isEqualTo("Do you have pain?");
        assertThat(q.getType()).isEqualTo(QuestionType.YES_NO);
        assertThat(q.isRequired()).isTrue();
        assertThat(q.isActive()).isFalse();
        assertThat(q.getOrdinal()).isEqualTo(3);
    }

    // ─── equals() and hashCode() ──────────────────────────────────────────────

    @Test
    void equals_sameFields_returnsTrue() throws Exception {
        final Question q1 = Question.builder().id(1L).prompt("Test?").type(QuestionType.TEXT).build();
        final Question q2 = Question.builder().id(1L).prompt("Test?").type(QuestionType.TEXT).build();

        assertThat(q1).isEqualTo(q2);
        assertThat(q1.hashCode()).isEqualTo(q2.hashCode());
    }
}
