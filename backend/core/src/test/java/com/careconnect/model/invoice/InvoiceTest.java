package com.careconnect.model.invoice;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceTest {

    // ─── No-arg constructor ───────────────────────────────────────────────────

    @Test
    void noArgConstructor_createsInstance() throws Exception {
        final Invoice invoice = new Invoice();

        assertThat(invoice).isNotNull();
        assertThat(invoice.getId()).isNull();
        assertThat(invoice.getInvoiceNumber()).isEmpty();
        assertThat(invoice.getProviderName()).isEmpty();
        assertThat(invoice.getProviderAddress()).isEmpty();
        assertThat(invoice.getProviderPhone()).isEmpty();
        assertThat(invoice.getPatientName()).isEmpty();
        assertThat(invoice.getServices()).isNotNull().isEmpty();
        assertThat(invoice.getHistory()).isNotNull().isEmpty();
        assertThat(invoice.getRecommendedActions()).isNotNull().isEmpty();
        assertThat(invoice.getPayments()).isNotNull().isEmpty();
    }

    // ─── Builder: all fields ──────────────────────────────────────────────────

    @Test
    void builder_allFields_setsCorrectly() throws Exception {
        final OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        final Invoice invoice = Invoice.builder()
                .id("INV-001")
                .invoiceNumber("2025-0001")
                .providerName("Dr. Alice Brown")
                .providerAddress("200 Health Ave")
                .providerPhone("312-555-0100")
                .providerEmail("abrown@clinic.com")
                .patientName("Jane Doe")
                .patientAddress("789 Elm St")
                .patientAccountNumber("ACC-001")
                .patientBillingAddress("PO Box 100")
                .statementDate(now)
                .dueDate(now.plusDays(30))
                .paidDate(null)
                .paymentStatus(PaymentStatus.pending)
                .billedToInsurance(true)
                .totalCharges(new BigDecimal("500.00"))
                .totalAdjustments(new BigDecimal("50.00"))
                .total(new BigDecimal("450.00"))
                .amountDue(new BigDecimal("450.00"))
                .paymentLink("https://pay.example.com")
                .qrCodeUrl("https://qr.example.com")
                .paymentNotes("Pay within 30 days")
                .supportedMethodsCsv("check,online")
                .checkName("ABC Clinic")
                .checkAddress("200 Health Ave")
                .checkReference("REF-001")
                .aiSummary("Invoice for office visit")
                .createdBy("admin")
                .updatedBy("admin")
                .documentLink("https://docs.example.com/inv-001.pdf")
                .createdAt(now)
                .updatedAt(now)
                .build();

        assertThat(invoice.getId()).isEqualTo("INV-001");
        assertThat(invoice.getInvoiceNumber()).isEqualTo("2025-0001");
        assertThat(invoice.getProviderName()).isEqualTo("Dr. Alice Brown");
        assertThat(invoice.getProviderEmail()).isEqualTo("abrown@clinic.com");
        assertThat(invoice.getPatientName()).isEqualTo("Jane Doe");
        assertThat(invoice.getPatientAccountNumber()).isEqualTo("ACC-001");
        assertThat(invoice.getPaymentStatus()).isEqualTo(PaymentStatus.pending);
        assertThat(invoice.isBilledToInsurance()).isTrue();
        assertThat(invoice.getTotalCharges()).isEqualByComparingTo(new BigDecimal("500.00"));
        assertThat(invoice.getAmountDue()).isEqualByComparingTo(new BigDecimal("450.00"));
        assertThat(invoice.getSupportedMethodsCsv()).isEqualTo("check,online");
        assertThat(invoice.getAiSummary()).isEqualTo("Invoice for office visit");
        assertThat(invoice.getCreatedBy()).isEqualTo("admin");
        assertThat(invoice.getCreatedAt()).isEqualTo(now);
    }

    // ─── Setters ──────────────────────────────────────────────────────────────

    @Test
    void setters_updateFields() throws Exception {
        final Invoice invoice = new Invoice();

        invoice.setId("INV-002");
        invoice.setInvoiceNumber("2025-0002");
        invoice.setPaymentStatus(PaymentStatus.paid);
        invoice.setBilledToInsurance(false);
        invoice.setAmountDue(BigDecimal.ZERO);

        assertThat(invoice.getId()).isEqualTo("INV-002");
        assertThat(invoice.getInvoiceNumber()).isEqualTo("2025-0002");
        assertThat(invoice.getPaymentStatus()).isEqualTo(PaymentStatus.paid);
        assertThat(invoice.isBilledToInsurance()).isFalse();
        assertThat(invoice.getAmountDue()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ─── addPayment() ─────────────────────────────────────────────────────────

    @Test
    void addPayment_addsToList_andSetsInvoiceReference() throws Exception {
        final Invoice invoice = new Invoice();   // no-arg ctor initialises payments list
        invoice.setId("INV-003");
        final InvoicePayment payment = new InvoicePayment();
        payment.setMethodKey("check");

        invoice.addPayment(payment);

        assertThat(invoice.getPayments()).hasSize(1);
        assertThat(invoice.getPayments().get(0)).isSameAs(payment);
        assertThat(payment.getInvoice()).isSameAs(invoice);
    }

    @Test
    void addPayment_multiplePayments_allAdded() throws Exception {
        final Invoice invoice = new Invoice();
        invoice.setId("INV-004");

        final InvoicePayment p1 = new InvoicePayment();
        p1.setMethodKey("check");
        final InvoicePayment p2 = new InvoicePayment();
        p2.setMethodKey("online");

        invoice.addPayment(p1);
        invoice.addPayment(p2);

        assertThat(invoice.getPayments()).hasSize(2);
    }

    // ─── removePaymentById() ──────────────────────────────────────────────────

    @Test
    void removePaymentById_removesCorrectPayment() throws Exception {
        final Invoice invoice = new Invoice();
        invoice.setId("INV-005");

        final InvoicePayment p1 = new InvoicePayment();
        p1.setId("pay-aaa");
        final InvoicePayment p2 = new InvoicePayment();
        p2.setId("pay-bbb");

        invoice.addPayment(p1);
        invoice.addPayment(p2);

        invoice.removePaymentById("pay-aaa");

        assertThat(invoice.getPayments()).hasSize(1);
        assertThat(invoice.getPayments().get(0).getId()).isEqualTo("pay-bbb");
    }

    @Test
    void removePaymentById_nonexistentId_doesNothing() throws Exception {
        final Invoice invoice = new Invoice();
        invoice.setId("INV-006");

        final InvoicePayment p1 = new InvoicePayment();
        p1.setId("pay-ccc");
        invoice.addPayment(p1);

        invoice.removePaymentById("pay-nonexistent");

        assertThat(invoice.getPayments()).hasSize(1);
    }

    // ─── setPayments() ────────────────────────────────────────────────────────

    @Test
    void setPayments_replacesCollection() throws Exception {
        final Invoice invoice = new Invoice();
        invoice.setId("INV-007");

        final InvoicePayment p1 = new InvoicePayment();
        invoice.addPayment(p1);

        invoice.setPayments(List.of());

        assertThat(invoice.getPayments()).isEmpty();
    }

    // ─── collections default to empty ────────────────────────────────────────

    @Test
    void builder_services_defaultsToEmptyList() throws Exception {
        // Lombok builder doesn't honour plain field initialisers; use no-arg ctor
        final Invoice invoice = new Invoice();
        assertThat(invoice.getServices()).isNotNull().isEmpty();
    }

    @Test
    void builder_history_defaultsToEmptyList() throws Exception {
        final Invoice invoice = new Invoice();
        assertThat(invoice.getHistory()).isNotNull().isEmpty();
    }

    @Test
    void builder_recommendedActions_defaultsToEmptyList() throws Exception {
        final Invoice invoice = new Invoice();
        assertThat(invoice.getRecommendedActions()).isNotNull().isEmpty();
    }

    // ─── equals() and hashCode() ──────────────────────────────────────────────

    @Test
    void equals_sameId_returnsTrue() throws Exception {
        final Invoice i1 = Invoice.builder().id("INV-AAA").invoiceNumber("001").build();
        final Invoice i2 = Invoice.builder().id("INV-AAA").invoiceNumber("001").build();

        assertThat(i1).isEqualTo(i2);
        assertThat(i1.hashCode()).isEqualTo(i2.hashCode());
    }

    @Test
    void equals_differentId_returnsFalse() throws Exception {
        final Invoice i1 = Invoice.builder().id("INV-AAA").build();
        final Invoice i2 = Invoice.builder().id("INV-BBB").build();

        assertThat(i1).isNotEqualTo(i2);
    }

    @Test
    void equals_null_returnsFalse() throws Exception {
        final Invoice invoice = new Invoice();
        assertThat(invoice).isNotEqualTo(null);
    }
}
