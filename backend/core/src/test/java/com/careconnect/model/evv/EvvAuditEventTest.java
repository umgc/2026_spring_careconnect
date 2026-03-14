package com.careconnect.model.evv;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EvvAuditEventTest {

    // ─── No-arg constructor ───────────────────────────────────────────────────

    @Test
    void noArgConstructor_createsInstance() throws Exception {
        final EvvAuditEvent event = new EvvAuditEvent();

        assertThat(event).isNotNull();
        assertThat(event.getId()).isNull();
        assertThat(event.getEvvRecord()).isNull();
        assertThat(event.getEventType()).isNull();
        assertThat(event.getEventTime()).isNull();
        assertThat(event.getActorUserId()).isNull();
        assertThat(event.getDeviceInfo()).isNull();
        assertThat(event.getDetails()).isNull();
    }

    // ─── Builder all fields ───────────────────────────────────────────────────

    @Test
    void builder_allFields() throws Exception {
        final EvvRecord record = new EvvRecord();
        final OffsetDateTime now = OffsetDateTime.now();
        final Map<String, Object> deviceInfo = new HashMap<>();
        deviceInfo.put("platform", "iOS");
        final Map<String, Object> details = new HashMap<>();
        details.put("reason", "check-in");

        final EvvAuditEvent event = EvvAuditEvent.builder()
                .id(1L)
                .evvRecord(record)
                .eventType("CHECK_IN")
                .eventTime(now)
                .actorUserId(5L)
                .deviceInfo(deviceInfo)
                .details(details)
                .build();

        assertThat(event.getId()).isEqualTo(1L);
        assertThat(event.getEvvRecord()).isSameAs(record);
        assertThat(event.getEventType()).isEqualTo("CHECK_IN");
        assertThat(event.getEventTime()).isEqualTo(now);
        assertThat(event.getActorUserId()).isEqualTo(5L);
        assertThat(event.getDeviceInfo()).containsEntry("platform", "iOS");
        assertThat(event.getDetails()).containsEntry("reason", "check-in");
    }

    // ─── Setters ──────────────────────────────────────────────────────────────

    @Test
    void setters_updateFields() throws Exception {
        final EvvAuditEvent event = new EvvAuditEvent();
        final OffsetDateTime now = OffsetDateTime.now();

        event.setEventType("CHECK_OUT");
        event.setEventTime(now);
        event.setActorUserId(10L);

        assertThat(event.getEventType()).isEqualTo("CHECK_OUT");
        assertThat(event.getEventTime()).isEqualTo(now);
        assertThat(event.getActorUserId()).isEqualTo(10L);
    }

    // ─── onCreate() ───────────────────────────────────────────────────────────

    @Test
    void onCreate_setsEventTimeWhenNull() throws Exception {
        final EvvAuditEvent event = new EvvAuditEvent();
        assertThat(event.getEventTime()).isNull();

        final Method m = EvvAuditEvent.class.getDeclaredMethod("onCreate");
        m.setAccessible(true);
        m.invoke(event);

        assertThat(event.getEventTime()).isNotNull();
    }

    @Test
    void onCreate_doesNotOverwriteExistingEventTime() throws Exception {
        final EvvAuditEvent event = new EvvAuditEvent();
        final OffsetDateTime original = OffsetDateTime.now().minusDays(1);
        event.setEventTime(original);

        final Method m = EvvAuditEvent.class.getDeclaredMethod("onCreate");
        m.setAccessible(true);
        m.invoke(event);

        assertThat(event.getEventTime()).isEqualTo(original);
    }
}
