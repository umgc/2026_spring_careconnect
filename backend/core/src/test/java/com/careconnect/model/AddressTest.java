package com.careconnect.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AddressTest {

    // ─── No-arg constructor ───────────────────────────────────────────────────

    @Test
    void noArgConstructor_createsInstance() throws Exception {
        final Address address = new Address();

        assertThat(address).isNotNull();
        assertThat(address.getLine1()).isNull();
        assertThat(address.getLine2()).isNull();
        assertThat(address.getCity()).isNull();
        assertThat(address.getState()).isNull();
        assertThat(address.getZip()).isNull();
    }

    // ─── All-arg constructor ──────────────────────────────────────────────────

    @Test
    void allArgConstructor_setsAllFields() throws Exception {
        final Address address = new Address("123 Main St", "Apt 4B", "Springfield", "IL", "62701");

        assertThat(address.getLine1()).isEqualTo("123 Main St");
        assertThat(address.getLine2()).isEqualTo("Apt 4B");
        assertThat(address.getCity()).isEqualTo("Springfield");
        assertThat(address.getState()).isEqualTo("IL");
        assertThat(address.getZip()).isEqualTo("62701");
    }

    // ─── Builder ──────────────────────────────────────────────────────────────

    @Test
    void builder_setsAllFields() throws Exception {
        final Address address = Address.builder()
                .line1("456 Oak Ave")
                .line2("Suite 200")
                .city("Shelbyville")
                .state("TN")
                .zip("37160")
                .build();

        assertThat(address.getLine1()).isEqualTo("456 Oak Ave");
        assertThat(address.getLine2()).isEqualTo("Suite 200");
        assertThat(address.getCity()).isEqualTo("Shelbyville");
        assertThat(address.getState()).isEqualTo("TN");
        assertThat(address.getZip()).isEqualTo("37160");
    }

    // ─── Setters ──────────────────────────────────────────────────────────────

    @Test
    void setters_updateFields() throws Exception {
        final Address address = new Address();

        address.setLine1("789 Pine Rd");
        address.setLine2("Floor 3");
        address.setCity("Ogdenville");
        address.setState("OH");
        address.setZip("44101");

        assertThat(address.getLine1()).isEqualTo("789 Pine Rd");
        assertThat(address.getLine2()).isEqualTo("Floor 3");
        assertThat(address.getCity()).isEqualTo("Ogdenville");
        assertThat(address.getState()).isEqualTo("OH");
        assertThat(address.getZip()).isEqualTo("44101");
    }
}
