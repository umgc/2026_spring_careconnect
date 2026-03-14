package com.careconnect.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentTest {

    // ─── No-arg constructor ───────────────────────────────────────────────────

    @Test
    void noArgConstructor_createsInstance() throws Exception {
        final Payment payment = new Payment();

        assertThat(payment).isNotNull();
        assertThat(payment.getId()).isNull();
        assertThat(payment.getSubscription()).isNull();
        assertThat(payment.getUser()).isNull();
        assertThat(payment.getAmountCents()).isNull();
        assertThat(payment.getStatus()).isNull();
        assertThat(payment.getAttemptedAt()).isNull();
    }

    // ─── Builder all fields ───────────────────────────────────────────────────

    @Test
    void builder_allFields() throws Exception {
        final Subscription subscription = new Subscription();
        final User user = new User();
        final Instant now = Instant.now();

        final Payment payment = Payment.builder()
                .id(1L)
                .subscription(subscription)
                .user(user)
                .amountCents(999)
                .status("SUCCEEDED")
                .attemptedAt(now)
                .stripeSessionId("sess_abc")
                .stripePaymentIntentId("pi_abc")
                .stripeInvoiceId("inv_abc")
                .build();

        assertThat(payment.getId()).isEqualTo(1L);
        assertThat(payment.getSubscription()).isSameAs(subscription);
        assertThat(payment.getUser()).isSameAs(user);
        assertThat(payment.getAmountCents()).isEqualTo(999);
        assertThat(payment.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(payment.getAttemptedAt()).isEqualTo(now);
        assertThat(payment.getStripeSessionId()).isEqualTo("sess_abc");
        assertThat(payment.getStripePaymentIntentId()).isEqualTo("pi_abc");
        assertThat(payment.getStripeInvoiceId()).isEqualTo("inv_abc");
    }

    // ─── Explicit setters ────────────────────────────────────────────────────

    @Test
    void setters_updateFields() throws Exception {
        final Payment payment = new Payment();
        final User user = new User();

        payment.setAmountCents(499);
        payment.setStripeSessionId("sess_xyz");
        payment.setStripePaymentIntentId("pi_xyz");
        payment.setUser(user);
        payment.setStatus("FAILED");

        assertThat(payment.getAmountCents()).isEqualTo(499);
        assertThat(payment.getStripeSessionId()).isEqualTo("sess_xyz");
        assertThat(payment.getStripePaymentIntentId()).isEqualTo("pi_xyz");
        assertThat(payment.getUser()).isSameAs(user);
        assertThat(payment.getStatus()).isEqualTo("FAILED");
    }

    // ─── equals() and hashCode() ──────────────────────────────────────────────

    @Test
    void equals_sameFields_returnsTrue() throws Exception {
        final Payment p1 = Payment.builder().id(1L).status("SUCCEEDED").build();
        final Payment p2 = Payment.builder().id(1L).status("SUCCEEDED").build();

        assertThat(p1).isEqualTo(p2);
        assertThat(p1.hashCode()).isEqualTo(p2.hashCode());
    }
}
