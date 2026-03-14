package com.careconnect.model;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link com.careconnect.model.CheckIn} (the JPA entity in the root model package,
 * not the checkins sub-package).
 */
class CheckInTest {

    // ─── No-arg constructor / Builder defaults ────────────────────────────────

    @Test
    void noArgConstructor_createsInstance() throws Exception {
        final CheckIn checkIn = new CheckIn();

        assertThat(checkIn).isNotNull();
        assertThat(checkIn.getId()).isNull();
        assertThat(checkIn.getPatient()).isNull();
    }

    @Test
    void builder_defaults_createdAtNotNull() throws Exception {
        final CheckIn checkIn = CheckIn.builder()
                .patient(new Patient())
                .build();

        assertThat(checkIn.getCreatedAt()).isNotNull();
        assertThat(checkIn.getAnswers()).isNotNull().isEmpty();
        assertThat(checkIn.getSelectedQuestions()).isNotNull().isEmpty();
    }

    // ─── Builder all fields ───────────────────────────────────────────────────

    @Test
    void builder_allFields() throws Exception {
        final Patient patient = new Patient();
        final OffsetDateTime now = OffsetDateTime.now();

        final CheckIn checkIn = CheckIn.builder()
                .id(1L)
                .patient(patient)
                .createdAt(now)
                .submittedAt(now)
                .answers(new ArrayList<>())
                .selectedQuestions(new HashSet<>())
                .build();

        assertThat(checkIn.getId()).isEqualTo(1L);
        assertThat(checkIn.getPatient()).isSameAs(patient);
        assertThat(checkIn.getCreatedAt()).isEqualTo(now);
        assertThat(checkIn.getSubmittedAt()).isEqualTo(now);
    }

    // ─── addSelectedQuestion / removeSelectedQuestion ─────────────────────────

    @Test
    void addSelectedQuestion_addsAndSetsReference() throws Exception {
        final CheckIn checkIn = CheckIn.builder().build();
        final CheckInQuestion cq = new CheckInQuestion();

        checkIn.addSelectedQuestion(cq);

        assertThat(checkIn.getSelectedQuestions()).contains(cq);
        assertThat(cq.getCheckIn()).isSameAs(checkIn);
    }

    @Test
    void removeSelectedQuestion_removesAndNullsReference() throws Exception {
        final CheckIn checkIn = CheckIn.builder().build();
        final CheckInQuestion cq = new CheckInQuestion();
        checkIn.addSelectedQuestion(cq);

        checkIn.removeSelectedQuestion(cq);

        assertThat(checkIn.getSelectedQuestions()).doesNotContain(cq);
        assertThat(cq.getCheckIn()).isNull();
    }
}
