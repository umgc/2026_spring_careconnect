package com.careconnect.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FamilyMemberTest {

    // ─── No-arg constructor ───────────────────────────────────────────────────

    @Test
    void noArgConstructor_createsInstance() throws Exception {
        final FamilyMember fm = new FamilyMember();

        assertThat(fm).isNotNull();
        assertThat(fm.getId()).isNull();
        assertThat(fm.getUser()).isNull();
        assertThat(fm.getFirstName()).isNull();
        assertThat(fm.getLastName()).isNull();
        assertThat(fm.getEmail()).isNull();
        assertThat(fm.getPhone()).isNull();
        assertThat(fm.getAddress()).isNull();
    }

    // ─── All-arg constructor ──────────────────────────────────────────────────

    @Test
    void allArgConstructor_setsAllFields() throws Exception {
        final User user = new User();
        final Address address = new Address("1 Oak St", null, "Towson", "MD", "21204");

        final FamilyMember fm = new FamilyMember(1L, user, "Mary", "Smith", "mary@family.com", "410-555-0200", address);

        assertThat(fm.getId()).isEqualTo(1L);
        assertThat(fm.getUser()).isSameAs(user);
        assertThat(fm.getFirstName()).isEqualTo("Mary");
        assertThat(fm.getLastName()).isEqualTo("Smith");
        assertThat(fm.getEmail()).isEqualTo("mary@family.com");
        assertThat(fm.getPhone()).isEqualTo("410-555-0200");
        assertThat(fm.getAddress()).isSameAs(address);
    }

    // ─── Builder ──────────────────────────────────────────────────────────────

    @Test
    void builder_setsFields() throws Exception {
        final FamilyMember fm = FamilyMember.builder()
                .id(2L)
                .firstName("Bob")
                .lastName("Jones")
                .email("bob@family.com")
                .phone("301-555-0300")
                .build();

        assertThat(fm.getId()).isEqualTo(2L);
        assertThat(fm.getFirstName()).isEqualTo("Bob");
        assertThat(fm.getLastName()).isEqualTo("Jones");
        assertThat(fm.getEmail()).isEqualTo("bob@family.com");
        assertThat(fm.getPhone()).isEqualTo("301-555-0300");
    }

    // ─── Setters ──────────────────────────────────────────────────────────────

    @Test
    void setters_updateFields() throws Exception {
        final FamilyMember fm = new FamilyMember();
        final User user = new User();
        final Address address = new Address();

        fm.setId(3L);
        fm.setUser(user);
        fm.setFirstName("Carol");
        fm.setLastName("White");
        fm.setEmail("carol@family.com");
        fm.setPhone("202-555-0400");
        fm.setAddress(address);

        assertThat(fm.getId()).isEqualTo(3L);
        assertThat(fm.getUser()).isSameAs(user);
        assertThat(fm.getFirstName()).isEqualTo("Carol");
        assertThat(fm.getLastName()).isEqualTo("White");
        assertThat(fm.getEmail()).isEqualTo("carol@family.com");
        assertThat(fm.getPhone()).isEqualTo("202-555-0400");
        assertThat(fm.getAddress()).isSameAs(address);
    }
}
