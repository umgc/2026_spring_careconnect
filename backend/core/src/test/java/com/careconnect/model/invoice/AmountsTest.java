package com.careconnect.model.invoice;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AmountsTest {

    // ─── Builder ──────────────────────────────────────────────────────────────

    @Test
    void builder_allFields_setsCorrectly() throws Exception {
        final Amounts amounts = Amounts.builder()
                .totalCharges(500.0)
                .totalAdjustments(50.0)
                .total(450.0)
                .amountDue(450.0)
                .build();

        assertThat(amounts.getTotalCharges()).isEqualTo(500.0);
        assertThat(amounts.getTotalAdjustments()).isEqualTo(50.0);
        assertThat(amounts.getTotal()).isEqualTo(450.0);
        assertThat(amounts.getAmountDue()).isEqualTo(450.0);
    }

    @Test
    void builder_defaults_nullFields() throws Exception {
        final Amounts amounts = Amounts.builder().build();

        assertThat(amounts.getTotalCharges()).isNull();
        assertThat(amounts.getTotalAdjustments()).isNull();
        assertThat(amounts.getTotal()).isNull();
        assertThat(amounts.getAmountDue()).isNull();
    }

    // ─── Setters ──────────────────────────────────────────────────────────────

    @Test
    void setters_updateFields() throws Exception {
        final Amounts amounts = Amounts.builder().build();

        amounts.setTotalCharges(1000.0);
        amounts.setTotalAdjustments(100.0);
        amounts.setTotal(900.0);
        amounts.setAmountDue(900.0);

        assertThat(amounts.getTotalCharges()).isEqualTo(1000.0);
        assertThat(amounts.getTotalAdjustments()).isEqualTo(100.0);
        assertThat(amounts.getTotal()).isEqualTo(900.0);
        assertThat(amounts.getAmountDue()).isEqualTo(900.0);
    }

    // ─── equals() and hashCode() ──────────────────────────────────────────────

    @Test
    void equals_sameFields_returnsTrue() throws Exception {
        final Amounts a1 = Amounts.builder().totalCharges(500.0).total(500.0).build();
        final Amounts a2 = Amounts.builder().totalCharges(500.0).total(500.0).build();

        assertThat(a1).isEqualTo(a2);
        assertThat(a1.hashCode()).isEqualTo(a2.hashCode());
    }

    @Test
    void equals_differentFields_returnsFalse() throws Exception {
        final Amounts a1 = Amounts.builder().totalCharges(500.0).build();
        final Amounts a2 = Amounts.builder().totalCharges(999.0).build();

        assertThat(a1).isNotEqualTo(a2);
    }

    @Test
    void toString_containsFieldValues() throws Exception {
        final Amounts amounts = Amounts.builder().totalCharges(500.0).amountDue(450.0).build();

        assertThat(amounts.toString()).contains("500.0");
        assertThat(amounts.toString()).contains("450.0");
    }
}
