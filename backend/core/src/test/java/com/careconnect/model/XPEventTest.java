package com.careconnect.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class XPEventTest {

    // ─── Constructor ─────────────────────────────────────────────────────────

    @Test
    void constructor_setsFields() throws Exception {
        final XPEvent event = new XPEvent("LOGIN", 50);

        assertThat(event.getEventName()).isEqualTo("LOGIN");
        assertThat(event.getXpPoints()).isEqualTo(50);
    }

    // ─── Setters ──────────────────────────────────────────────────────────────

    @Test
    void setEventName_updatesField() throws Exception {
        final XPEvent event = new XPEvent("OLD", 10);
        event.setEventName("NEW_EVENT");
        assertThat(event.getEventName()).isEqualTo("NEW_EVENT");
    }

    @Test
    void setXpPoints_updatesField() throws Exception {
        final XPEvent event = new XPEvent("EVENT", 10);
        event.setXpPoints(100);
        assertThat(event.getXpPoints()).isEqualTo(100);
    }
}
