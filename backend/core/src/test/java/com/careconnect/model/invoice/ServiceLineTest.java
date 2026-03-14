package com.careconnect.model.invoice;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceLineTest {

    // ─── No-arg constructor ───────────────────────────────────────────────────

    @Test
    void noArgConstructor_createsInstance() throws Exception {
        final ServiceLine line = new ServiceLine();

        assertThat(line).isNotNull();
        assertThat(line.getId()).isNull();
        assertThat(line.getInvoice()).isNull();
        assertThat(line.getDescription()).isNull();
        assertThat(line.getServiceCode()).isNull();
        assertThat(line.getServiceDate()).isNull();
        assertThat(line.getCharge()).isNull();
        assertThat(line.getPatientBalance()).isNull();
        assertThat(line.getInsuranceAdjustments()).isNull();
    }

    // ─── All-arg constructor ──────────────────────────────────────────────────

    @Test
    void allArgConstructor_setsAllFields() throws Exception {
        final Invoice invoice = Invoice.builder().id("INV-300").build();
        final OffsetDateTime serviceDate = OffsetDateTime.of(2025, 3, 15, 9, 0, 0, 0, ZoneOffset.UTC);

        final ServiceLine line = new ServiceLine(
                1L, invoice, "Office Visit", "99213", serviceDate,
                new BigDecimal("200.00"), new BigDecimal("50.00"), new BigDecimal("150.00")
        );

        assertThat(line.getId()).isEqualTo(1L);
        assertThat(line.getInvoice()).isSameAs(invoice);
        assertThat(line.getDescription()).isEqualTo("Office Visit");
        assertThat(line.getServiceCode()).isEqualTo("99213");
        assertThat(line.getServiceDate()).isEqualTo(serviceDate);
        assertThat(line.getCharge()).isEqualByComparingTo(new BigDecimal("200.00"));
        assertThat(line.getPatientBalance()).isEqualByComparingTo(new BigDecimal("50.00"));
        assertThat(line.getInsuranceAdjustments()).isEqualByComparingTo(new BigDecimal("150.00"));
    }

    // ─── Setters ──────────────────────────────────────────────────────────────

    @Test
    void setters_updateFields() throws Exception {
        final ServiceLine line = new ServiceLine();
        final Invoice invoice = Invoice.builder().id("INV-400").build();
        final OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        line.setId(5L);
        line.setInvoice(invoice);
        line.setDescription("Lab Work");
        line.setServiceCode("85025");
        line.setServiceDate(now);
        line.setCharge(new BigDecimal("75.00"));
        line.setPatientBalance(new BigDecimal("20.00"));
        line.setInsuranceAdjustments(new BigDecimal("55.00"));

        assertThat(line.getId()).isEqualTo(5L);
        assertThat(line.getInvoice()).isSameAs(invoice);
        assertThat(line.getDescription()).isEqualTo("Lab Work");
        assertThat(line.getServiceCode()).isEqualTo("85025");
        assertThat(line.getServiceDate()).isEqualTo(now);
        assertThat(line.getCharge()).isEqualByComparingTo(new BigDecimal("75.00"));
        assertThat(line.getPatientBalance()).isEqualByComparingTo(new BigDecimal("20.00"));
        assertThat(line.getInsuranceAdjustments()).isEqualByComparingTo(new BigDecimal("55.00"));
    }

    // ─── equals() and hashCode() ──────────────────────────────────────────────

    @Test
    void equals_sameFields_returnsTrue() throws Exception {
        final ServiceLine l1 = new ServiceLine(1L, null, "Office Visit", "99213", null,
                new BigDecimal("200.00"), null, null);
        final ServiceLine l2 = new ServiceLine(1L, null, "Office Visit", "99213", null,
                new BigDecimal("200.00"), null, null);

        assertThat(l1).isEqualTo(l2);
        assertThat(l1.hashCode()).isEqualTo(l2.hashCode());
    }

    @Test
    void equals_differentFields_returnsFalse() throws Exception {
        final ServiceLine l1 = new ServiceLine(1L, null, "Office Visit", "99213", null, null, null, null);
        final ServiceLine l2 = new ServiceLine(2L, null, "Lab Work", "85025", null, null, null, null);

        assertThat(l1).isNotEqualTo(l2);
    }

    @Test
    void equals_null_returnsFalse() throws Exception {
        final ServiceLine line = new ServiceLine();
        assertThat(line).isNotEqualTo(null);
    }
}
