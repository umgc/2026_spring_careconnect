package com.careconnect.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduledNotificationTest {

    // ─── No-arg constructor ───────────────────────────────────────────────────

    @Test
    void noArgConstructor_createsInstance() throws Exception {
        final ScheduledNotification n = new ScheduledNotification();

        assertThat(n).isNotNull();
        assertThat(n.getId()).isNull();
        assertThat(n.getReceiverId()).isNull();
        assertThat(n.getTitle()).isNull();
        assertThat(n.getBody()).isNull();
        assertThat(n.getStatus()).isEqualTo("PENDING");  // @Builder.Default initialises in no-arg ctor
        assertThat(n.getCreatedAt()).isNotNull();         // @Builder.Default initialises in no-arg ctor
        assertThat(n.getUpdatedAt()).isNotNull();         // @Builder.Default initialises in no-arg ctor
        assertThat(n.getTask()).isNull();
    }

    // ─── Builder defaults ─────────────────────────────────────────────────────

    @Test
    void builder_defaults() throws Exception {
        final ScheduledNotification n = ScheduledNotification.builder()
                .receiverId(1L)
                .title("Test")
                .body("Body text")
                .scheduledTime(LocalDateTime.now())
                .task(new Task())
                .build();

        assertThat(n.getStatus()).isEqualTo("PENDING");
        assertThat(n.getCreatedAt()).isNotNull();
        assertThat(n.getUpdatedAt()).isNotNull();
    }

    // ─── Builder all fields ───────────────────────────────────────────────────

    @Test
    void builder_allFields() throws Exception {
        final LocalDateTime now = LocalDateTime.now();
        final Task task = new Task();

        final ScheduledNotification n = ScheduledNotification.builder()
                .id(1L)
                .receiverId(10L)
                .title("Medication Reminder")
                .body("Take your medication now")
                .notificationType("REMINDER")
                .scheduledTime(now)
                .sentTime(now.plusSeconds(30))
                .status("SENT")
                .messageId("msg-123")
                .errorMessage(null)
                .createdAt(now)
                .updatedAt(now)
                .task(task)
                .build();

        assertThat(n.getId()).isEqualTo(1L);
        assertThat(n.getReceiverId()).isEqualTo(10L);
        assertThat(n.getTitle()).isEqualTo("Medication Reminder");
        assertThat(n.getBody()).isEqualTo("Take your medication now");
        assertThat(n.getNotificationType()).isEqualTo("REMINDER");
        assertThat(n.getScheduledTime()).isEqualTo(now);
        assertThat(n.getSentTime()).isEqualTo(now.plusSeconds(30));
        assertThat(n.getStatus()).isEqualTo("SENT");
        assertThat(n.getMessageId()).isEqualTo("msg-123");
        assertThat(n.getTask()).isSameAs(task);
    }

    // ─── setLastUpdate() ──────────────────────────────────────────────────────

    @Test
    void setLastUpdate_refreshesUpdatedAt() throws Exception {
        final ScheduledNotification n = new ScheduledNotification();
        n.setUpdatedAt(LocalDateTime.now().minusDays(1));
        final LocalDateTime before = n.getUpdatedAt();

        n.setLastUpdate();

        assertThat(n.getUpdatedAt()).isAfter(before);
    }

    // ─── equals() and hashCode() ──────────────────────────────────────────────

    @Test
    void equals_sameFields_returnsTrue() throws Exception {
        final LocalDateTime now = LocalDateTime.now();
        final ScheduledNotification n1 = ScheduledNotification.builder()
                .id(1L).title("Test").status("PENDING").createdAt(now).updatedAt(now).build();
        final ScheduledNotification n2 = ScheduledNotification.builder()
                .id(1L).title("Test").status("PENDING").createdAt(now).updatedAt(now).build();

        assertThat(n1).isEqualTo(n2);
        assertThat(n1.hashCode()).isEqualTo(n2.hashCode());
    }
}
