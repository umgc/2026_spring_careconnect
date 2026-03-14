package com.careconnect.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CaregiverTest {

    // ─── No-arg constructor ───────────────────────────────────────────────────

    @Test
    void noArgConstructor_createsInstance() throws Exception {
        final Caregiver caregiver = new Caregiver();

        assertThat(caregiver).isNotNull();
        assertThat(caregiver.getId()).isNull();
        assertThat(caregiver.getFirstName()).isNull();
        assertThat(caregiver.getLastName()).isNull();
        assertThat(caregiver.getDob()).isNull();
        assertThat(caregiver.getGender()).isNull();
        assertThat(caregiver.getEmail()).isNull();
        assertThat(caregiver.getPhone()).isNull();
        assertThat(caregiver.getProfessional()).isNull();
        assertThat(caregiver.getAddress()).isNull();
        assertThat(caregiver.getCaregiverType()).isNull();
        assertThat(caregiver.getUser()).isNull();
    }

    // ─── Builder ──────────────────────────────────────────────────────────────

    @Test
    void builder_setsFields() throws Exception {
        final Address address = new Address("1 Main St", null, "Annapolis", "MD", "21401");
        final ProfessionalInfo pro = new ProfessionalInfo("LIC-001", "MD", 5);
        final User user = new User();

        final Caregiver caregiver = Caregiver.builder()
                .id(1L)
                .firstName("Jane")
                .lastName("Nurse")
                .dob("1990-01-15")
                .gender(Gender.FEMALE)
                .email("jane@care.com")
                .phone("410-555-0100")
                .professional(pro)
                .address(address)
                .caregiverType("RN")
                .user(user)
                .build();

        assertThat(caregiver.getId()).isEqualTo(1L);
        assertThat(caregiver.getFirstName()).isEqualTo("Jane");
        assertThat(caregiver.getLastName()).isEqualTo("Nurse");
        assertThat(caregiver.getDob()).isEqualTo("1990-01-15");
        assertThat(caregiver.getGender()).isEqualTo(Gender.FEMALE);
        assertThat(caregiver.getEmail()).isEqualTo("jane@care.com");
        assertThat(caregiver.getPhone()).isEqualTo("410-555-0100");
        assertThat(caregiver.getProfessional()).isSameAs(pro);
        assertThat(caregiver.getAddress()).isSameAs(address);
        assertThat(caregiver.getCaregiverType()).isEqualTo("RN");
        assertThat(caregiver.getUser()).isSameAs(user);
    }

    // ─── getCaregiverType / setCaregiverType ──────────────────────────────────

    @Test
    void setCaregiverType_updatesField() throws Exception {
        final Caregiver caregiver = new Caregiver();
        caregiver.setCaregiverType("CNA");
        assertThat(caregiver.getCaregiverType()).isEqualTo("CNA");
    }
}
