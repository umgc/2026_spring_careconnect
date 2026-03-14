package com.careconnect.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SubscriptionTest {

    // ─── No-arg constructor ───────────────────────────────────────────────────

    @Test
    void noArgConstructor_createsInstance() throws Exception {
        final Subscription s = new Subscription();

        assertThat(s).isNotNull();
        assertThat(s.getId()).isNull();
        assertThat(s.getStripeSubscriptionId()).isNull();
        assertThat(s.getStripeCustomerId()).isNull();
        assertThat(s.getPriceId()).isNull();
        assertThat(s.getUser()).isNull();
        assertThat(s.getPlan()).isNull();
        assertThat(s.getStatus()).isNull();
        assertThat(s.getStartedAt()).isNull();
        assertThat(s.getCurrentPeriodEnd()).isNull();
    }

    // ─── Setters and getters ──────────────────────────────────────────────────

    @Test
    void setters_updateFields() throws Exception {
        final Subscription s = new Subscription();
        final User user = new User();
        final Plan plan = new Plan();
        final Instant now = Instant.now();

        s.setId(1L);
        s.setStripeSubscriptionId("sub_abc123");
        s.setStripeCustomerId("cus_abc123");
        s.setPriceId("price_abc123");
        s.setUser(user);
        s.setPlan(plan);
        s.setStatus("ACTIVE");
        s.setStartedAt(now);
        s.setCurrentPeriodEnd(now.plusSeconds(3600));

        assertThat(s.getId()).isEqualTo(1L);
        assertThat(s.getStripeSubscriptionId()).isEqualTo("sub_abc123");
        assertThat(s.getStripeCustomerId()).isEqualTo("cus_abc123");
        assertThat(s.getPriceId()).isEqualTo("price_abc123");
        assertThat(s.getUser()).isSameAs(user);
        assertThat(s.getPlan()).isSameAs(plan);
        assertThat(s.getStatus()).isEqualTo("ACTIVE");
        assertThat(s.getStartedAt()).isEqualTo(now);
        assertThat(s.getCurrentPeriodEnd()).isEqualTo(now.plusSeconds(3600));
    }
}
