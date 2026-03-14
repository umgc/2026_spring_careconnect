package com.careconnect.model.invoice;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CheckPayableToTest {

    // ─── Builder ──────────────────────────────────────────────────────────────

    @Test
    void builder_allFields_setsCorrectly() throws Exception {
        final CheckPayableTo checkPayableTo = CheckPayableTo.builder()
                .name("Care Connect Medical")
                .address("123 Main St, Springfield, IL")
                .reference("INV-2025-001")
                .build();

        assertThat(checkPayableTo.getName()).isEqualTo("Care Connect Medical");
        assertThat(checkPayableTo.getAddress()).isEqualTo("123 Main St, Springfield, IL");
        assertThat(checkPayableTo.getReference()).isEqualTo("INV-2025-001");
    }

    @Test
    void builder_defaults_nullFields() throws Exception {
        final CheckPayableTo checkPayableTo = CheckPayableTo.builder().build();

        assertThat(checkPayableTo.getName()).isNull();
        assertThat(checkPayableTo.getAddress()).isNull();
        assertThat(checkPayableTo.getReference()).isNull();
    }

    // ─── Setters ──────────────────────────────────────────────────────────────

    @Test
    void setters_updateFields() throws Exception {
        final CheckPayableTo checkPayableTo = CheckPayableTo.builder().build();

        checkPayableTo.setName("Updated Name");
        checkPayableTo.setAddress("456 Oak Ave");
        checkPayableTo.setReference("REF-2025-999");

        assertThat(checkPayableTo.getName()).isEqualTo("Updated Name");
        assertThat(checkPayableTo.getAddress()).isEqualTo("456 Oak Ave");
        assertThat(checkPayableTo.getReference()).isEqualTo("REF-2025-999");
    }

    // ─── equals() and hashCode() ──────────────────────────────────────────────

    @Test
    void equals_sameFields_returnsTrue() throws Exception {
        final CheckPayableTo c1 = CheckPayableTo.builder().name("Clinic A").reference("REF-1").build();
        final CheckPayableTo c2 = CheckPayableTo.builder().name("Clinic A").reference("REF-1").build();

        assertThat(c1).isEqualTo(c2);
        assertThat(c1.hashCode()).isEqualTo(c2.hashCode());
    }

    @Test
    void equals_differentFields_returnsFalse() throws Exception {
        final CheckPayableTo c1 = CheckPayableTo.builder().name("Clinic A").build();
        final CheckPayableTo c2 = CheckPayableTo.builder().name("Clinic B").build();

        assertThat(c1).isNotEqualTo(c2);
    }

    @Test
    void toString_containsFieldValues() throws Exception {
        final CheckPayableTo c = CheckPayableTo.builder().name("Care Connect").reference("INV-001").build();

        assertThat(c.toString()).contains("Care Connect");
        assertThat(c.toString()).contains("INV-001");
    }
}
