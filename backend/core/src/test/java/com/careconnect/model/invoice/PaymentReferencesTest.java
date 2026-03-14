package com.careconnect.model.invoice;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentReferencesTest {

    // ─── Builder ──────────────────────────────────────────────────────────────

    @Test
    void builder_allFields_setsCorrectly() throws Exception {
        final List<String> methods = Arrays.asList("check", "credit_card", "online");

        final PaymentReferences refs = PaymentReferences.builder()
                .paymentLink("https://pay.example.com/inv-001")
                .qrCodeUrl("https://qr.example.com/inv-001")
                .notes("Please pay within 30 days")
                .supportedMethods(methods)
                .build();

        assertThat(refs.getPaymentLink()).isEqualTo("https://pay.example.com/inv-001");
        assertThat(refs.getQrCodeUrl()).isEqualTo("https://qr.example.com/inv-001");
        assertThat(refs.getNotes()).isEqualTo("Please pay within 30 days");
        assertThat(refs.getSupportedMethods()).containsExactly("check", "credit_card", "online");
    }

    @Test
    void builder_defaults_nullFields() throws Exception {
        final PaymentReferences refs = PaymentReferences.builder().build();

        assertThat(refs.getPaymentLink()).isNull();
        assertThat(refs.getQrCodeUrl()).isNull();
        assertThat(refs.getNotes()).isNull();
        assertThat(refs.getSupportedMethods()).isNull();
    }

    // ─── All-arg constructor ──────────────────────────────────────────────────

    @Test
    void allArgConstructor_setsAllFields() throws Exception {
        final List<String> methods = List.of("telephone");

        final PaymentReferences refs = new PaymentReferences(
                "https://pay.example.com",
                "https://qr.example.com",
                "Call to pay",
                methods
        );

        assertThat(refs.getPaymentLink()).isEqualTo("https://pay.example.com");
        assertThat(refs.getQrCodeUrl()).isEqualTo("https://qr.example.com");
        assertThat(refs.getNotes()).isEqualTo("Call to pay");
        assertThat(refs.getSupportedMethods()).containsExactly("telephone");
    }

    // ─── Setters ──────────────────────────────────────────────────────────────

    @Test
    void setters_updateFields() throws Exception {
        final PaymentReferences refs = PaymentReferences.builder().build();

        refs.setPaymentLink("https://updated-pay.example.com");
        refs.setQrCodeUrl("https://updated-qr.example.com");
        refs.setNotes("Updated notes");
        refs.setSupportedMethods(List.of("check"));

        assertThat(refs.getPaymentLink()).isEqualTo("https://updated-pay.example.com");
        assertThat(refs.getQrCodeUrl()).isEqualTo("https://updated-qr.example.com");
        assertThat(refs.getNotes()).isEqualTo("Updated notes");
        assertThat(refs.getSupportedMethods()).containsExactly("check");
    }

    // ─── equals() and hashCode() ──────────────────────────────────────────────

    @Test
    void equals_sameFields_returnsTrue() throws Exception {
        final PaymentReferences r1 = PaymentReferences.builder().paymentLink("https://pay.example.com").build();
        final PaymentReferences r2 = PaymentReferences.builder().paymentLink("https://pay.example.com").build();

        assertThat(r1).isEqualTo(r2);
        assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
    }

    @Test
    void equals_differentFields_returnsFalse() throws Exception {
        final PaymentReferences r1 = PaymentReferences.builder().paymentLink("https://pay-a.example.com").build();
        final PaymentReferences r2 = PaymentReferences.builder().paymentLink("https://pay-b.example.com").build();

        assertThat(r1).isNotEqualTo(r2);
    }
}
