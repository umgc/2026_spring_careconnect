package com.careconnect.dto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ProfessionalInfoDtoTest {

    // ─── Default constructor ──────────────────────────────────────────────────

    @Test
    void defaultConstructor_createsInstance() throws Exception {
        final ProfessionalInfoDto dto = new ProfessionalInfoDto();

        assertThat(dto).isNotNull();
        assertThat(dto.getLicenseNumber()).isNull();
        assertThat(dto.getIssuingState()).isNull();
        assertThat(dto.getYearsExperience()).isZero();
    }

    // ─── Setters and Getters ──────────────────────────────────────────────────

    @Test
    void setLicenseNumber_getLicenseNumber_roundTrips() throws Exception {
        final ProfessionalInfoDto dto = new ProfessionalInfoDto();
        dto.setLicenseNumber("LIC-12345");
        assertThat(dto.getLicenseNumber()).isEqualTo("LIC-12345");
    }

    @Test
    void setIssuingState_getIssuingState_roundTrips() throws Exception {
        final ProfessionalInfoDto dto = new ProfessionalInfoDto();
        dto.setIssuingState("Maryland");
        assertThat(dto.getIssuingState()).isEqualTo("Maryland");
    }

    @Test
    void setYearsExperience_getYearsExperience_roundTrips() throws Exception {
        final ProfessionalInfoDto dto = new ProfessionalInfoDto();
        dto.setYearsExperience(10);
        assertThat(dto.getYearsExperience()).isEqualTo(10);
    }

    // ─── All fields set together ──────────────────────────────────────────────

    @Test
    void allSetters_allFieldsUpdated() throws Exception {
        final ProfessionalInfoDto dto = new ProfessionalInfoDto();

        dto.setLicenseNumber("RN-99999");
        dto.setIssuingState("Virginia");
        dto.setYearsExperience(5);

        assertThat(dto.getLicenseNumber()).isEqualTo("RN-99999");
        assertThat(dto.getIssuingState()).isEqualTo("Virginia");
        assertThat(dto.getYearsExperience()).isEqualTo(5);
    }
}
