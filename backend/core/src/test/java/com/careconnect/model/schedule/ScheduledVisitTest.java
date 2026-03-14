package com.careconnect.model.schedule;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduledVisitTest {

    // ─── No-arg constructor ───────────────────────────────────────────────────

    @Test
    void noArgConstructor_appliesFieldDefaults() throws Exception {
        final ScheduledVisit sv = new ScheduledVisit();

        assertThat(sv).isNotNull();
        assertThat(sv.getId()).isNull();
        assertThat(sv.getCaregiverId()).isNull();
        assertThat(sv.getPatientId()).isNull();
        assertThat(sv.getServiceType()).isNull();
        assertThat(sv.getScheduledDate()).isNull();
        assertThat(sv.getScheduledTime()).isNull();
        // plain field initializers – applied in no-arg ctor
        assertThat(sv.getDurationMinutes()).isEqualTo(60);
        assertThat(sv.getPriority()).isEqualTo("Normal");
        assertThat(sv.getStatus()).isEqualTo("Scheduled");
    }

    // ─── All-arg constructor ──────────────────────────────────────────────────

    @Test
    void allArgConstructor_setsAllFields() throws Exception {
        final LocalDate date = LocalDate.of(2026, 3, 15);
        final LocalTime time = LocalTime.of(9, 0);
        final LocalDateTime now = LocalDateTime.now();

        final ScheduledVisit sv = new ScheduledVisit(
                1L, 10L, 20L, "PERSONAL_CARE", date, time, 90,
                "High", "Bring wheelchair", "Scheduled",
                now, now, false, null, "admin", "admin");

        assertThat(sv.getId()).isEqualTo(1L);
        assertThat(sv.getCaregiverId()).isEqualTo(10L);
        assertThat(sv.getPatientId()).isEqualTo(20L);
        assertThat(sv.getServiceType()).isEqualTo("PERSONAL_CARE");
        assertThat(sv.getScheduledDate()).isEqualTo(date);
        assertThat(sv.getScheduledTime()).isEqualTo(time);
        assertThat(sv.getDurationMinutes()).isEqualTo(90);
        assertThat(sv.getPriority()).isEqualTo("High");
        assertThat(sv.getNotes()).isEqualTo("Bring wheelchair");
        assertThat(sv.getStatus()).isEqualTo("Scheduled");
        assertThat(sv.getCreatedAt()).isEqualTo(now);
        assertThat(sv.getUpdatedAt()).isEqualTo(now);
        assertThat(sv.getCreatedBy()).isEqualTo("admin");
        assertThat(sv.getUpdatedBy()).isEqualTo("admin");
    }

    // ─── onCreate() ───────────────────────────────────────────────────────────

    @Test
    void onCreate_setsTimestampsWhenNull() throws Exception {
        final ScheduledVisit sv = new ScheduledVisit();

        final Method m = ScheduledVisit.class.getDeclaredMethod("onCreate");
        m.setAccessible(true);
        m.invoke(sv);

        assertThat(sv.getCreatedAt()).isNotNull();
        assertThat(sv.getUpdatedAt()).isNotNull();
    }

    @Test
    void onCreate_doesNotOverwriteExistingTimestamps() throws Exception {
        final ScheduledVisit sv = new ScheduledVisit();
        final LocalDateTime existing = LocalDateTime.now().minusDays(1);
        sv.setCreatedAt(existing);
        sv.setUpdatedAt(existing);

        final Method m = ScheduledVisit.class.getDeclaredMethod("onCreate");
        m.setAccessible(true);
        m.invoke(sv);

        assertThat(sv.getCreatedAt()).isEqualTo(existing);
        assertThat(sv.getUpdatedAt()).isEqualTo(existing);
    }

    // ─── onUpdate() ───────────────────────────────────────────────────────────

    @Test
    void onUpdate_refreshesUpdatedAt() throws Exception {
        final ScheduledVisit sv = new ScheduledVisit();
        sv.setUpdatedAt(LocalDateTime.now().minusDays(1));
        final LocalDateTime before = sv.getUpdatedAt();

        final Method m = ScheduledVisit.class.getDeclaredMethod("onUpdate");
        m.setAccessible(true);
        m.invoke(sv);

        assertThat(sv.getUpdatedAt()).isAfter(before);
    }

    // ─── Status-transition methods ────────────────────────────────────────────

    @Test
    void markInProgress_setsStatus() throws Exception {
        final ScheduledVisit sv = new ScheduledVisit();
        sv.markInProgress();
        assertThat(sv.getStatus()).isEqualTo("In Progress");
    }

    @Test
    void markCompleted_setsStatus() throws Exception {
        final ScheduledVisit sv = new ScheduledVisit();
        sv.markCompleted();
        assertThat(sv.getStatus()).isEqualTo("Completed");
    }

    @Test
    void markCancelled_setsStatus() throws Exception {
        final ScheduledVisit sv = new ScheduledVisit();
        sv.markCancelled();
        assertThat(sv.getStatus()).isEqualTo("Cancelled");
    }

    @Test
    void markNoShow_setsStatus() throws Exception {
        final ScheduledVisit sv = new ScheduledVisit();
        sv.markNoShow();
        assertThat(sv.getStatus()).isEqualTo("No Show");
    }

    // ─── Status predicate methods ─────────────────────────────────────────────

    @Test
    void isScheduled_whenStatusIsScheduled_returnsTrue() throws Exception {
        final ScheduledVisit sv = new ScheduledVisit();
        // default status is "Scheduled"
        assertThat(sv.isScheduled()).isTrue();
    }

    @Test
    void isScheduled_whenStatusIsNotScheduled_returnsFalse() throws Exception {
        final ScheduledVisit sv = new ScheduledVisit();
        sv.markInProgress();
        assertThat(sv.isScheduled()).isFalse();
    }

    @Test
    void isCompleted_whenStatusIsCompleted_returnsTrue() throws Exception {
        final ScheduledVisit sv = new ScheduledVisit();
        sv.markCompleted();
        assertThat(sv.isCompleted()).isTrue();
    }

    @Test
    void isCompleted_whenStatusIsNotCompleted_returnsFalse() throws Exception {
        final ScheduledVisit sv = new ScheduledVisit();
        assertThat(sv.isCompleted()).isFalse();
    }

    @Test
    void isCancelled_whenStatusIsCancelled_returnsTrue() throws Exception {
        final ScheduledVisit sv = new ScheduledVisit();
        sv.markCancelled();
        assertThat(sv.isCancelled()).isTrue();
    }

    @Test
    void isCancelled_whenStatusIsNotCancelled_returnsFalse() throws Exception {
        final ScheduledVisit sv = new ScheduledVisit();
        assertThat(sv.isCancelled()).isFalse();
    }

    // ─── Setters ──────────────────────────────────────────────────────────────

    @Test
    void setters_updateFields() throws Exception {
        final ScheduledVisit sv = new ScheduledVisit();
        final LocalDate date = LocalDate.of(2026, 4, 1);
        final LocalTime time = LocalTime.of(14, 30);

        sv.setId(5L);
        sv.setCaregiverId(11L);
        sv.setPatientId(22L);
        sv.setServiceType("COMPANION_CARE");
        sv.setScheduledDate(date);
        sv.setScheduledTime(time);
        sv.setDurationMinutes(45);
        sv.setPriority("Low");
        sv.setNotes("Bring walker");

        assertThat(sv.getId()).isEqualTo(5L);
        assertThat(sv.getCaregiverId()).isEqualTo(11L);
        assertThat(sv.getPatientId()).isEqualTo(22L);
        assertThat(sv.getServiceType()).isEqualTo("COMPANION_CARE");
        assertThat(sv.getScheduledDate()).isEqualTo(date);
        assertThat(sv.getScheduledTime()).isEqualTo(time);
        assertThat(sv.getDurationMinutes()).isEqualTo(45);
        assertThat(sv.getPriority()).isEqualTo("Low");
        assertThat(sv.getNotes()).isEqualTo("Bring walker");
    }
}
