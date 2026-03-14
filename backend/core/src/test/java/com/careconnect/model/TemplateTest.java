package com.careconnect.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateTest {

    // ─── No-arg constructor ───────────────────────────────────────────────────

    @Test
    void noArgConstructor_createsInstance() throws Exception {
        final Template t = new Template();

        assertThat(t).isNotNull();
        assertThat(t.getId()).isNull();
        assertThat(t.getName()).isNull();
        assertThat(t.getDescription()).isNull();
        assertThat(t.getFrequency()).isNull();
        assertThat(t.getTaskInterval()).isZero();
        assertThat(t.getDoCount()).isZero();
        assertThat(t.getDaysOfWeek()).isNull();
        assertThat(t.getTimeOfDay()).isNull();
        assertThat(t.getIcon()).isZero();
        assertThat(t.getNotifications()).isNull();
    }

    // ─── Builder all fields ───────────────────────────────────────────────────

    @Test
    void builder_allFields() throws Exception {
        final List<Boolean> days = List.of(true, false, true, false, true, false, false);

        final Template t = Template.builder()
                .id(1L)
                .name("Daily Medication")
                .description("Take medication every day")
                .frequency("daily")
                .taskInterval(1)
                .doCount(30)
                .daysOfWeek(days)
                .timeOfDay("08:00 AM")
                .icon(5)
                .notifications(List.of("FCM", "SMS"))
                .build();

        assertThat(t.getId()).isEqualTo(1L);
        assertThat(t.getName()).isEqualTo("Daily Medication");
        assertThat(t.getDescription()).isEqualTo("Take medication every day");
        assertThat(t.getFrequency()).isEqualTo("daily");
        assertThat(t.getTaskInterval()).isEqualTo(1);
        assertThat(t.getDoCount()).isEqualTo(30);
        assertThat(t.getDaysOfWeek()).hasSize(7);
        assertThat(t.getTimeOfDay()).isEqualTo("08:00 AM");
        assertThat(t.getIcon()).isEqualTo(5);
        assertThat(t.getNotifications()).containsExactly("FCM", "SMS");
    }

    // ─── Setters ──────────────────────────────────────────────────────────────

    @Test
    void setters_updateFields() throws Exception {
        final Template t = new Template();

        t.setId(2L);
        t.setName("Weekly Exercise");
        t.setDescription("Exercise weekly");
        t.setFrequency("weekly");
        t.setTaskInterval(1);
        t.setDoCount(10);
        t.setTimeOfDay("10:00 AM");
        t.setIcon(3);

        assertThat(t.getId()).isEqualTo(2L);
        assertThat(t.getName()).isEqualTo("Weekly Exercise");
        assertThat(t.getDescription()).isEqualTo("Exercise weekly");
        assertThat(t.getFrequency()).isEqualTo("weekly");
        assertThat(t.getTaskInterval()).isEqualTo(1);
        assertThat(t.getDoCount()).isEqualTo(10);
        assertThat(t.getTimeOfDay()).isEqualTo("10:00 AM");
        assertThat(t.getIcon()).isEqualTo(3);
    }

    // ─── equals() and hashCode() ──────────────────────────────────────────────

    @Test
    void equals_sameFields_returnsTrue() throws Exception {
        final Template t1 = Template.builder().id(1L).name("Test").icon(1).build();
        final Template t2 = Template.builder().id(1L).name("Test").icon(1).build();

        assertThat(t1).isEqualTo(t2);
        assertThat(t1.hashCode()).isEqualTo(t2.hashCode());
    }
}
