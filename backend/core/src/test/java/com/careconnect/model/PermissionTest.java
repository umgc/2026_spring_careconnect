package com.careconnect.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionTest {

    // ─── Default constructor ──────────────────────────────────────────────────

    @Test
    void defaultConstructor_createsInstance() throws Exception {
        final Permission perm = new Permission();
        assertThat(perm).isNotNull();
        assertThat(perm.getId()).isNull();
        assertThat(perm.getName()).isNull();
        assertThat(perm.getDescription()).isNull();
    }

    // ─── Parameterized constructor ────────────────────────────────────────────

    @Test
    void parameterizedConstructor_setsNameAndDescription() throws Exception {
        final Permission perm = new Permission("READ_VITALS", "Allows reading patient vitals");

        assertThat(perm.getName()).isEqualTo("READ_VITALS");
        assertThat(perm.getDescription()).isEqualTo("Allows reading patient vitals");
    }

    // ─── Setters / Getters ────────────────────────────────────────────────────

    @Test
    void settersAndGetters_updateFields() throws Exception {
        final Permission perm = new Permission();

        perm.setId("perm-001");
        perm.setName("WRITE_NOTES");
        perm.setDescription("Allows writing clinical notes");

        assertThat(perm.getId()).isEqualTo("perm-001");
        assertThat(perm.getName()).isEqualTo("WRITE_NOTES");
        assertThat(perm.getDescription()).isEqualTo("Allows writing clinical notes");
    }
}
