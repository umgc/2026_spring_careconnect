package com.careconnect.dto;

import com.careconnect.model.Gender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class PatientProfileUpdateDTOTest {

    @Mock
    private AddressDto mockAddress;

    @Mock
    private AllergyDTO mockAllergy;

    // ─── No-arg constructor ───────────────────────────────────────────────────

    @Test
    void noArgConstructor_createsInstance() throws Exception {
        final PatientProfileUpdateDTO dto = new PatientProfileUpdateDTO();

        assertThat(dto).isNotNull();
        assertThat(dto.getFirstName()).isNull();
        assertThat(dto.getLastName()).isNull();
        assertThat(dto.getPhone()).isNull();
        assertThat(dto.getDob()).isNull();
        assertThat(dto.getGender()).isNull();
        assertThat(dto.getAddress()).isNull();
        assertThat(dto.getRelationship()).isNull();
        assertThat(dto.getAllergies()).isNull();
    }

    // ─── Setters and Getters ──────────────────────────────────────────────────

    @Test
    void setFirstName_getFirstName_roundTrips() throws Exception {
        final PatientProfileUpdateDTO dto = new PatientProfileUpdateDTO();
        dto.setFirstName("Alice");
        assertThat(dto.getFirstName()).isEqualTo("Alice");
    }

    @Test
    void setLastName_getLastName_roundTrips() throws Exception {
        final PatientProfileUpdateDTO dto = new PatientProfileUpdateDTO();
        dto.setLastName("Smith");
        assertThat(dto.getLastName()).isEqualTo("Smith");
    }

    @Test
    void setPhone_getPhone_roundTrips() throws Exception {
        final PatientProfileUpdateDTO dto = new PatientProfileUpdateDTO();
        dto.setPhone("555-1234");
        assertThat(dto.getPhone()).isEqualTo("555-1234");
    }

    @Test
    void setDob_getDob_roundTrips() throws Exception {
        final PatientProfileUpdateDTO dto = new PatientProfileUpdateDTO();
        dto.setDob("1990-05-15");
        assertThat(dto.getDob()).isEqualTo("1990-05-15");
    }

    @Test
    void setGender_getGender_roundTrips() throws Exception {
        final PatientProfileUpdateDTO dto = new PatientProfileUpdateDTO();
        dto.setGender(Gender.FEMALE);
        assertThat(dto.getGender()).isEqualTo(Gender.FEMALE);
    }

    @Test
    void setAddress_getAddress_roundTrips() throws Exception {
        final PatientProfileUpdateDTO dto = new PatientProfileUpdateDTO();
        dto.setAddress(mockAddress);
        assertThat(dto.getAddress()).isEqualTo(mockAddress);
    }

    @Test
    void setRelationship_getRelationship_roundTrips() throws Exception {
        final PatientProfileUpdateDTO dto = new PatientProfileUpdateDTO();
        dto.setRelationship("Spouse");
        assertThat(dto.getRelationship()).isEqualTo("Spouse");
    }

    @Test
    void setAllergies_getAllergies_roundTrips() throws Exception {
        final List<AllergyDTO> allergies = List.of(mockAllergy);
        final PatientProfileUpdateDTO dto = new PatientProfileUpdateDTO();
        dto.setAllergies(allergies);
        assertThat(dto.getAllergies()).isEqualTo(allergies);
    }

    // ─── All fields set together ──────────────────────────────────────────────

    @Test
    void allSetters_allFieldsUpdated() throws Exception {
        final List<AllergyDTO> allergies = List.of(mockAllergy);
        final PatientProfileUpdateDTO dto = new PatientProfileUpdateDTO();

        dto.setFirstName("Bob");
        dto.setLastName("Jones");
        dto.setPhone("555-9999");
        dto.setDob("1985-03-22");
        dto.setGender(Gender.MALE);
        dto.setAddress(mockAddress);
        dto.setRelationship("Parent");
        dto.setAllergies(allergies);

        assertThat(dto.getFirstName()).isEqualTo("Bob");
        assertThat(dto.getLastName()).isEqualTo("Jones");
        assertThat(dto.getPhone()).isEqualTo("555-9999");
        assertThat(dto.getDob()).isEqualTo("1985-03-22");
        assertThat(dto.getGender()).isEqualTo(Gender.MALE);
        assertThat(dto.getAddress()).isEqualTo(mockAddress);
        assertThat(dto.getRelationship()).isEqualTo("Parent");
        assertThat(dto.getAllergies()).isEqualTo(allergies);
    }
}
