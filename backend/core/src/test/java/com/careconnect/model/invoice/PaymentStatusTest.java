package com.careconnect.model.invoice;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentStatusTest {

    // ─── All enum values present ───────────────────────────────────────────────

    @Test
    void enumValues_allPresent() throws Exception {
        final PaymentStatus[] values = PaymentStatus.values();

        assertThat(values).containsExactlyInAnyOrder(
                PaymentStatus.pending,
                PaymentStatus.overdue,
                PaymentStatus.pendingInsurance,
                PaymentStatus.sent,
                PaymentStatus.paid,
                PaymentStatus.partialPayment,
                PaymentStatus.rejectedInsurance
        );
    }

    // ─── valueOf() ────────────────────────────────────────────────────────────

    @Test
    void valueOf_pending_returnsPending() throws Exception {
        assertThat(PaymentStatus.valueOf("pending")).isEqualTo(PaymentStatus.pending);
    }

    @Test
    void valueOf_overdue_returnsOverdue() throws Exception {
        assertThat(PaymentStatus.valueOf("overdue")).isEqualTo(PaymentStatus.overdue);
    }

    @Test
    void valueOf_pendingInsurance_returnsPendingInsurance() throws Exception {
        assertThat(PaymentStatus.valueOf("pendingInsurance")).isEqualTo(PaymentStatus.pendingInsurance);
    }

    @Test
    void valueOf_sent_returnsSent() throws Exception {
        assertThat(PaymentStatus.valueOf("sent")).isEqualTo(PaymentStatus.sent);
    }

    @Test
    void valueOf_paid_returnsPaid() throws Exception {
        assertThat(PaymentStatus.valueOf("paid")).isEqualTo(PaymentStatus.paid);
    }

    @Test
    void valueOf_partialPayment_returnsPartialPayment() throws Exception {
        assertThat(PaymentStatus.valueOf("partialPayment")).isEqualTo(PaymentStatus.partialPayment);
    }

    @Test
    void valueOf_rejectedInsurance_returnsRejectedInsurance() throws Exception {
        assertThat(PaymentStatus.valueOf("rejectedInsurance")).isEqualTo(PaymentStatus.rejectedInsurance);
    }

    // ─── name() and ordinal() ─────────────────────────────────────────────────

    @Test
    void name_returnsCorrectString() throws Exception {
        assertThat(PaymentStatus.pending.name()).isEqualTo("pending");
        assertThat(PaymentStatus.paid.name()).isEqualTo("paid");
        assertThat(PaymentStatus.overdue.name()).isEqualTo("overdue");
    }

    @Test
    void ordinal_isStable() throws Exception {
        assertThat(PaymentStatus.pending.ordinal()).isEqualTo(0);
        assertThat(PaymentStatus.overdue.ordinal()).isEqualTo(1);
        assertThat(PaymentStatus.pendingInsurance.ordinal()).isEqualTo(2);
        assertThat(PaymentStatus.sent.ordinal()).isEqualTo(3);
        assertThat(PaymentStatus.paid.ordinal()).isEqualTo(4);
        assertThat(PaymentStatus.partialPayment.ordinal()).isEqualTo(5);
        assertThat(PaymentStatus.rejectedInsurance.ordinal()).isEqualTo(6);
    }

    // ─── Count ────────────────────────────────────────────────────────────────

    @Test
    void enumCount_isSeven() throws Exception {
        assertThat(PaymentStatus.values()).hasSize(7);
    }
}
