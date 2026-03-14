package com.careconnect.model.invoice;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PatientInfoTest {

    // ─── Builder ──────────────────────────────────────────────────────────────

    @Test
    void builder_allFields_setsCorrectly() throws Exception {
        final PatientInfo patient = PatientInfo.builder()
                .name("Jane Doe")
                .address("789 Elm St, Boston, MA")
                .accountNumber("ACC-001")
                .billingAddress("PO Box 100, Boston, MA")
                .build();

        assertThat(patient.getName()).isEqualTo("Jane Doe");
        assertThat(patient.getAddress()).isEqualTo("789 Elm St, Boston, MA");
        assertThat(patient.getAccountNumber()).isEqualTo("ACC-001");
        assertThat(patient.getBillingAddress()).isEqualTo("PO Box 100, Boston, MA");
    }

    @Test
    void builder_defaults_nullFields() throws Exception {
        final PatientInfo patient = PatientInfo.builder().build();

        assertThat(patient.getName()).isNull();
        assertThat(patient.getAddress()).isNull();
        assertThat(patient.getAccountNumber()).isNull();
        assertThat(patient.getBillingAddress()).isNull();
    }

    // ─── Setters ──────────────────────────────────────────────────────────────

    @Test
    void setters_updateFields() throws Exception {
        final PatientInfo patient = PatientInfo.builder().build();

        patient.setName("John Smith");
        patient.setAddress("100 Oak Rd");
        patient.setAccountNumber("ACC-999");
        patient.setBillingAddress("100 Oak Rd");

        assertThat(patient.getName()).isEqualTo("John Smith");
        assertThat(patient.getAddress()).isEqualTo("100 Oak Rd");
        assertThat(patient.getAccountNumber()).isEqualTo("ACC-999");
        assertThat(patient.getBillingAddress()).isEqualTo("100 Oak Rd");
    }

    // ─── equals() and hashCode() ──────────────────────────────────────────────

    @Test
    void equals_sameFields_returnsTrue() throws Exception {
        final PatientInfo p1 = PatientInfo.builder().name("Jane Doe").accountNumber("ACC-001").build();
        final PatientInfo p2 = PatientInfo.builder().name("Jane Doe").accountNumber("ACC-001").build();

        assertThat(p1).isEqualTo(p2);
        assertThat(p1.hashCode()).isEqualTo(p2.hashCode());
    }

    @Test
    void equals_differentFields_returnsFalse() throws Exception {
        final PatientInfo p1 = PatientInfo.builder().name("Jane Doe").build();
        final PatientInfo p2 = PatientInfo.builder().name("John Smith").build();

        assertThat(p1).isNotEqualTo(p2);
    }

    @Test
    void toString_containsFieldValues() throws Exception {
        final PatientInfo patient = PatientInfo.builder().name("Jane Doe").accountNumber("ACC-001").build();

        assertThat(patient.toString()).contains("Jane Doe");
        assertThat(patient.toString()).contains("ACC-001");
    }
}
