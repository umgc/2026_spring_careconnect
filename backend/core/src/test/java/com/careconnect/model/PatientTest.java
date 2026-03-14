package com.careconnect.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PatientTest {

    // ─── No-arg constructor ───────────────────────────────────────────────────

    @Test
    void noArgConstructor_createsInstance() throws Exception {
        final Patient patient = new Patient();

        assertThat(patient).isNotNull();
        assertThat(patient.getId()).isNull();
        assertThat(patient.getFirstName()).isNull();
        assertThat(patient.getAllergies()).isNotNull().isEmpty();
    }

    // ─── Builder ──────────────────────────────────────────────────────────────

    @Test
    void builder_setsFields() throws Exception {
        final Address address = new Address("1 Main St", null, "Baltimore", "MD", "21201");
        final User user = new User();

        final Patient patient = Patient.builder()
                .id(1L)
                .firstName("John")
                .lastName("Doe")
                .email("john@example.com")
                .phone("410-555-0001")
                .dob("1985-06-15")
                .gender(Gender.MALE)
                .address(address)
                .user(user)
                .relationship("client")
                .maNumber("MA123456")
                .alexaLinked(true)
                .build();

        assertThat(patient.getId()).isEqualTo(1L);
        assertThat(patient.getFirstName()).isEqualTo("John");
        assertThat(patient.getLastName()).isEqualTo("Doe");
        assertThat(patient.getEmail()).isEqualTo("john@example.com");
        assertThat(patient.getPhone()).isEqualTo("410-555-0001");
        assertThat(patient.getDob()).isEqualTo("1985-06-15");
        assertThat(patient.getGender()).isEqualTo(Gender.MALE);
        assertThat(patient.getAddress()).isSameAs(address);
        assertThat(patient.getUser()).isSameAs(user);
        assertThat(patient.getRelationship()).isEqualTo("client");
        assertThat(patient.getMaNumber()).isEqualTo("MA123456");
        assertThat(patient.isAlexaLinked()).isTrue();
    }

    // ─── isAlexaLinked() ─────────────────────────────────────────────────────

    @Test
    void isAlexaLinked_nullAlexaLinked_returnsFalse() throws Exception {
        final Patient patient = new Patient();
        patient.setAlexaLinked(null);
        assertThat(patient.isAlexaLinked()).isFalse();
    }

    @Test
    void isAlexaLinked_trueAlexaLinked_returnsTrue() throws Exception {
        final Patient patient = new Patient();
        patient.setAlexaLinked(true);
        assertThat(patient.isAlexaLinked()).isTrue();
    }

    @Test
    void isAlexaLinked_falseAlexaLinked_returnsFalse() throws Exception {
        final Patient patient = new Patient();
        patient.setAlexaLinked(false);
        assertThat(patient.isAlexaLinked()).isFalse();
    }

    // ─── isAlexaRefreshTokenExpired() ────────────────────────────────────────

    @Test
    void isAlexaRefreshTokenExpired_nullExpiresAt_returnsTrue() throws Exception {
        final Patient patient = new Patient();
        patient.setAlexaRefreshTokenExpiresAt(null);
        assertThat(patient.isAlexaRefreshTokenExpired()).isTrue();
    }

    @Test
    void isAlexaRefreshTokenExpired_pastExpiresAt_returnsTrue() throws Exception {
        final Patient patient = new Patient();
        patient.setAlexaRefreshTokenExpiresAt(LocalDateTime.now().minusDays(1));
        assertThat(patient.isAlexaRefreshTokenExpired()).isTrue();
    }

    @Test
    void isAlexaRefreshTokenExpired_futureExpiresAt_returnsFalse() throws Exception {
        final Patient patient = new Patient();
        patient.setAlexaRefreshTokenExpiresAt(LocalDateTime.now().plusDays(1));
        assertThat(patient.isAlexaRefreshTokenExpired()).isFalse();
    }

    // ─── Alexa token setters/getters ──────────────────────────────────────────

    @Test
    void alexaTokenFields_setAndGet() throws Exception {
        final Patient patient = new Patient();
        final LocalDateTime now = LocalDateTime.now();

        patient.setAlexaRefreshToken("refresh-token-abc");
        patient.setAlexaRefreshTokenExpiresAt(now.plusDays(30));
        patient.setAlexaRefreshTokenCreatedAt(now);

        assertThat(patient.getAlexaRefreshToken()).isEqualTo("refresh-token-abc");
        assertThat(patient.getAlexaRefreshTokenExpiresAt()).isEqualTo(now.plusDays(30));
        assertThat(patient.getAlexaRefreshTokenCreatedAt()).isEqualTo(now);
    }

    // ─── setPrimaryCareProvider ───────────────────────────────────────────────

    @Test
    void setPrimaryCareProvider_updatesField() throws Exception {
        final Patient patient = new Patient();
        final Provider provider = new Provider();

        patient.setPrimaryCareProvider(provider);
        assertThat(patient.getPrimaryCareProvider()).isSameAs(provider);
    }
}
