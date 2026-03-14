package com.careconnect.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlanTest {

    // ─── No-arg constructor ───────────────────────────────────────────────────

    @Test
    void noArgConstructor_createsInstance() throws Exception {
        final Plan plan = new Plan();

        assertThat(plan).isNotNull();
        assertThat(plan.getId()).isNull();
        assertThat(plan.getCode()).isNull();
        assertThat(plan.getName()).isNull();
        assertThat(plan.getPriceCents()).isNull();
        assertThat(plan.getBillingPeriod()).isNull();
    }

    // ─── Builder defaults ─────────────────────────────────────────────────────

    @Test
    void builder_isActive_defaultsToTrue() throws Exception {
        final Plan plan = Plan.builder().code("BASIC").build();
        assertThat(plan.getIsActive()).isTrue();
    }

    // ─── All-arg constructor ──────────────────────────────────────────────────

    @Test
    void allArgConstructor_setsAllFields() throws Exception {
        final Plan plan = new Plan(1L, "PRO", "Pro Plan", 999, "MONTHLY", false);

        assertThat(plan.getId()).isEqualTo(1L);
        assertThat(plan.getCode()).isEqualTo("PRO");
        assertThat(plan.getName()).isEqualTo("Pro Plan");
        assertThat(plan.getPriceCents()).isEqualTo(999);
        assertThat(plan.getBillingPeriod()).isEqualTo("MONTHLY");
        assertThat(plan.getIsActive()).isFalse();
    }

    // ─── Setters ──────────────────────────────────────────────────────────────

    @Test
    void setters_updateFields() throws Exception {
        final Plan plan = new Plan();

        plan.setId(2L);
        plan.setCode("ENTERPRISE");
        plan.setName("Enterprise Plan");
        plan.setPriceCents(4999);
        plan.setBillingPeriod("YEARLY");
        plan.setIsActive(true);

        assertThat(plan.getId()).isEqualTo(2L);
        assertThat(plan.getCode()).isEqualTo("ENTERPRISE");
        assertThat(plan.getName()).isEqualTo("Enterprise Plan");
        assertThat(plan.getPriceCents()).isEqualTo(4999);
        assertThat(plan.getBillingPeriod()).isEqualTo("YEARLY");
        assertThat(plan.getIsActive()).isTrue();
    }

    // ─── equals() and hashCode() ──────────────────────────────────────────────

    @Test
    void equals_sameFields_returnsTrue() throws Exception {
        final Plan p1 = Plan.builder().id(1L).code("BASIC").build();
        final Plan p2 = Plan.builder().id(1L).code("BASIC").build();

        assertThat(p1).isEqualTo(p2);
        assertThat(p1.hashCode()).isEqualTo(p2.hashCode());
    }

    @Test
    void equals_differentFields_returnsFalse() throws Exception {
        final Plan p1 = Plan.builder().id(1L).build();
        final Plan p2 = Plan.builder().id(2L).build();

        assertThat(p1).isNotEqualTo(p2);
    }
}
