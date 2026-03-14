package com.careconnect.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProfessionalInfoDtoTest {

    @Test
    @DisplayName("constructor - valid args - creates record with correct values")
    void constructor_validArgs_createsRecordWithCorrectValues() throws Exception {
        final ProfessionalInfoDto dto = new ProfessionalInfoDto("LIC123", "MD", 10);

        assertEquals("LIC123", dto.licenseNumber());
        assertEquals("MD", dto.issuingState());
        assertEquals(10, dto.yearsExperience());
    }

    @Test
    @DisplayName("constructor - null licenseNumber and issuingState - creates record with nulls")
    void constructor_nullFields_createsRecordWithNulls() throws Exception {
        final ProfessionalInfoDto dto = new ProfessionalInfoDto(null, null, 0);

        assertNull(dto.licenseNumber());
        assertNull(dto.issuingState());
        assertEquals(0, dto.yearsExperience());
    }

    @Test
    @DisplayName("equals - same values - returns true")
    void equals_sameValues_returnsTrue() throws Exception {
        final ProfessionalInfoDto dto1 = new ProfessionalInfoDto("LIC123", "MD", 10);
        final ProfessionalInfoDto dto2 = new ProfessionalInfoDto("LIC123", "MD", 10);

        assertEquals(dto1, dto2);
    }

    @Test
    @DisplayName("equals - different values - returns false")
    void equals_differentValues_returnsFalse() throws Exception {
        final ProfessionalInfoDto dto1 = new ProfessionalInfoDto("LIC123", "MD", 10);
        final ProfessionalInfoDto dto2 = new ProfessionalInfoDto("LIC456", "VA", 5);

        assertNotEquals(dto1, dto2);
    }

    @Test
    @DisplayName("hashCode - same values - returns same hashCode")
    void hashCode_sameValues_returnsSameHashCode() throws Exception {
        final ProfessionalInfoDto dto1 = new ProfessionalInfoDto("LIC123", "MD", 10);
        final ProfessionalInfoDto dto2 = new ProfessionalInfoDto("LIC123", "MD", 10);

        assertEquals(dto1.hashCode(), dto2.hashCode());
    }

    @Test
    @DisplayName("toString - valid record - contains field values")
    void toString_validRecord_containsFieldValues() throws Exception {
        final ProfessionalInfoDto dto = new ProfessionalInfoDto("LIC123", "MD", 10);

        final String str = dto.toString();
        assertTrue(str.contains("LIC123"));
        assertTrue(str.contains("MD"));
        assertTrue(str.contains("10"));
    }
}
