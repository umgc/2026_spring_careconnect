package com.careconnect.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderTest {

    // ─── No-arg constructor ───────────────────────────────────────────────────

    @Test
    void noArgConstructor_createsInstance() throws Exception {
        final Provider provider = new Provider();

        assertThat(provider).isNotNull();
        assertThat(provider.getId()).isNull();
        assertThat(provider.getName()).isNull();
        assertThat(provider.getSpecialty()).isNull();
        assertThat(provider.getOrganization()).isNull();
        assertThat(provider.getPhone()).isNull();
        assertThat(provider.getEmail()).isNull();
    }

    // ─── All-arg constructor ──────────────────────────────────────────────────

    @Test
    void allArgConstructor_setsAllFields() throws Exception {
        final Provider provider = new Provider(1L, "Dr. Smith", "Cardiology", "City Hospital", "555-1234", "smith@hospital.org");

        assertThat(provider.getId()).isEqualTo(1L);
        assertThat(provider.getName()).isEqualTo("Dr. Smith");
        assertThat(provider.getSpecialty()).isEqualTo("Cardiology");
        assertThat(provider.getOrganization()).isEqualTo("City Hospital");
        assertThat(provider.getPhone()).isEqualTo("555-1234");
        assertThat(provider.getEmail()).isEqualTo("smith@hospital.org");
    }

    // ─── Builder ──────────────────────────────────────────────────────────────

    @Test
    void builder_setsFields() throws Exception {
        final Provider provider = Provider.builder()
                .id(2L)
                .name("Dr. Jones")
                .specialty("Neurology")
                .organization("Metro Clinic")
                .phone("555-9999")
                .email("jones@clinic.org")
                .build();

        assertThat(provider.getId()).isEqualTo(2L);
        assertThat(provider.getName()).isEqualTo("Dr. Jones");
        assertThat(provider.getSpecialty()).isEqualTo("Neurology");
        assertThat(provider.getOrganization()).isEqualTo("Metro Clinic");
        assertThat(provider.getPhone()).isEqualTo("555-9999");
        assertThat(provider.getEmail()).isEqualTo("jones@clinic.org");
    }

    // ─── Setters ──────────────────────────────────────────────────────────────

    @Test
    void setters_updateFields() throws Exception {
        final Provider provider = new Provider();

        provider.setId(3L);
        provider.setName("Dr. Lee");
        provider.setSpecialty("Pediatrics");
        provider.setOrganization("Children's Hospital");
        provider.setPhone("555-4321");
        provider.setEmail("lee@children.org");

        assertThat(provider.getId()).isEqualTo(3L);
        assertThat(provider.getName()).isEqualTo("Dr. Lee");
        assertThat(provider.getSpecialty()).isEqualTo("Pediatrics");
        assertThat(provider.getOrganization()).isEqualTo("Children's Hospital");
        assertThat(provider.getPhone()).isEqualTo("555-4321");
        assertThat(provider.getEmail()).isEqualTo("lee@children.org");
    }
}
