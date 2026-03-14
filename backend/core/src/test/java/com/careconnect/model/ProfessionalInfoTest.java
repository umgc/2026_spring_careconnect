package com.careconnect.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProfessionalInfoTest {

    // ─── No-arg constructor ───────────────────────────────────────────────────

    @Test
    void noArgConstructor_createsInstance() throws Exception {
        final ProfessionalInfo info = new ProfessionalInfo();

        assertThat(info).isNotNull();
        assertThat(info.getLicenseNumber()).isNull();
        assertThat(info.getIssuingState()).isNull();
        assertThat(info.getYearsExperience()).isNull();
    }

    // ─── All-arg constructor ──────────────────────────────────────────────────

    @Test
    void allArgConstructor_setsAllFields() throws Exception {
        final ProfessionalInfo info = new ProfessionalInfo("LIC-12345", "MD", 10);

        assertThat(info.getLicenseNumber()).isEqualTo("LIC-12345");
        assertThat(info.getIssuingState()).isEqualTo("MD");
        assertThat(info.getYearsExperience()).isEqualTo(10);
    }

    // ─── Builder ──────────────────────────────────────────────────────────────

    @Test
    void builder_setsFields() throws Exception {
        final ProfessionalInfo info = ProfessionalInfo.builder()
                .licenseNumber("RN-99999")
                .issuingState("VA")
                .yearsExperience(5)
                .build();

        assertThat(info.getLicenseNumber()).isEqualTo("RN-99999");
        assertThat(info.getIssuingState()).isEqualTo("VA");
        assertThat(info.getYearsExperience()).isEqualTo(5);
    }

    // ─── Setters ──────────────────────────────────────────────────────────────

    @Test
    void setters_updateFields() throws Exception {
        final ProfessionalInfo info = new ProfessionalInfo();

        info.setLicenseNumber("CNA-777");
        info.setIssuingState("DC");
        info.setYearsExperience(3);

        assertThat(info.getLicenseNumber()).isEqualTo("CNA-777");
        assertThat(info.getIssuingState()).isEqualTo("DC");
        assertThat(info.getYearsExperience()).isEqualTo(3);
    }
}
