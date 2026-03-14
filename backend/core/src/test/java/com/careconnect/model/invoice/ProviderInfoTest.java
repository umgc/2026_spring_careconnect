package com.careconnect.model.invoice;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderInfoTest {

    // ─── Builder ──────────────────────────────────────────────────────────────

    @Test
    void builder_allFields_setsCorrectly() throws Exception {
        final ProviderInfo provider = ProviderInfo.builder()
                .name("Dr. Alice Brown")
                .address("200 Health Ave, Chicago, IL")
                .phone("312-555-0100")
                .email("abrown@clinic.com")
                .build();

        assertThat(provider.getName()).isEqualTo("Dr. Alice Brown");
        assertThat(provider.getAddress()).isEqualTo("200 Health Ave, Chicago, IL");
        assertThat(provider.getPhone()).isEqualTo("312-555-0100");
        assertThat(provider.getEmail()).isEqualTo("abrown@clinic.com");
    }

    @Test
    void builder_defaults_nullFields() throws Exception {
        final ProviderInfo provider = ProviderInfo.builder().build();

        assertThat(provider.getName()).isNull();
        assertThat(provider.getAddress()).isNull();
        assertThat(provider.getPhone()).isNull();
        assertThat(provider.getEmail()).isNull();
    }

    // ─── All-arg constructor ──────────────────────────────────────────────────

    @Test
    void allArgConstructor_setsAllFields() throws Exception {
        final ProviderInfo provider = new ProviderInfo("Dr. Bob White", "300 Clinic Rd", "800-555-0200", "bwhite@health.org");

        assertThat(provider.getName()).isEqualTo("Dr. Bob White");
        assertThat(provider.getAddress()).isEqualTo("300 Clinic Rd");
        assertThat(provider.getPhone()).isEqualTo("800-555-0200");
        assertThat(provider.getEmail()).isEqualTo("bwhite@health.org");
    }

    // ─── Setters ──────────────────────────────────────────────────────────────

    @Test
    void setters_updateFields() throws Exception {
        final ProviderInfo provider = ProviderInfo.builder().build();

        provider.setName("Dr. Carol Green");
        provider.setAddress("500 Wellness Blvd");
        provider.setPhone("555-111-2222");
        provider.setEmail("cgreen@wellness.com");

        assertThat(provider.getName()).isEqualTo("Dr. Carol Green");
        assertThat(provider.getAddress()).isEqualTo("500 Wellness Blvd");
        assertThat(provider.getPhone()).isEqualTo("555-111-2222");
        assertThat(provider.getEmail()).isEqualTo("cgreen@wellness.com");
    }

    // ─── equals() and hashCode() ──────────────────────────────────────────────

    @Test
    void equals_sameFields_returnsTrue() throws Exception {
        final ProviderInfo p1 = ProviderInfo.builder().name("Dr. X").phone("555-0000").build();
        final ProviderInfo p2 = ProviderInfo.builder().name("Dr. X").phone("555-0000").build();

        assertThat(p1).isEqualTo(p2);
        assertThat(p1.hashCode()).isEqualTo(p2.hashCode());
    }

    @Test
    void equals_differentFields_returnsFalse() throws Exception {
        final ProviderInfo p1 = ProviderInfo.builder().name("Dr. X").build();
        final ProviderInfo p2 = ProviderInfo.builder().name("Dr. Y").build();

        assertThat(p1).isNotEqualTo(p2);
    }
}
