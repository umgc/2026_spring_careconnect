package com.careconnect.service;

import com.careconnect.dto.AllergyDTO;
import com.careconnect.dto.FamilyMemberLinkResponse;
import com.careconnect.dto.MedicationDTO;
import com.careconnect.dto.PatientProfileDTO;
import com.careconnect.model.Allergy.AllergySeverity;
import com.careconnect.model.Gender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VialOfLifePdfServiceTest {

    @Mock
    private PatientService patientService;

    @Mock
    private MedicationService medicationService;

    @Mock
    private FamilyMemberService familyMemberService;

    @InjectMocks
    private VialOfLifePdfService vialOfLifePdfService;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
    }

    // ── extractPatientIdFromEmergencyId (via generateVialOfLifePdf) ──

    @Test
    @DisplayName("generateVialOfLifePdf_validEmergencyIdWithFullProfile_returnsPdfBytes")
    void generateVialOfLifePdf_validEmergencyIdWithFullProfile_returnsPdfBytes() throws Exception {
        final Long patientId = 123L;
        final String emergencyId = "VIAL123";

        final PatientProfileDTO profile = PatientProfileDTO.builder()
                .id(patientId)
                .firstName("John")
                .lastName("Doe")
                .dob(LocalDate.now().minusYears(30).toString())
                .gender(Gender.MALE)
                .phone("555-1234")
                .allergies(List.of(
                        AllergyDTO.builder()
                                .allergen("Penicillin")
                                .severity(AllergySeverity.SEVERE)
                                .reaction("Anaphylaxis")
                                .build(),
                        AllergyDTO.builder()
                                .allergen("Peanuts")
                                .severity(null)
                                .reaction(null)
                                .build()
                ))
                .build();

        final MedicationDTO activeMed = MedicationDTO.builder()
                .medicationName("Metformin")
                .dosage("500mg")
                .frequency("twice daily")
                .isActive(true)
                .build();

        final MedicationDTO inactiveMed = MedicationDTO.builder()
                .medicationName("Ibuprofen")
                .dosage("200mg")
                .frequency(null)
                .isActive(false)
                .build();

        final FamilyMemberLinkResponse contact = new FamilyMemberLinkResponse(
                1L, 10L, "Jane Doe", "jane@example.com", 5L, "John Doe",
                "Spouse", "ACTIVE", LocalDateTime.now(), "self");

        when(patientService.getPatientProfile(patientId)).thenReturn(Optional.of(profile));
        when(medicationService.getAllMedicationsForPatient(patientId)).thenReturn(List.of(activeMed, inactiveMed));
        when(familyMemberService.getFamilyMembersByPatientId(patientId)).thenReturn(List.of(contact));

        final byte[] pdf = vialOfLifePdfService.generateVialOfLifePdf(emergencyId);

        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
        // PDF magic bytes
        assertEquals(0x25, pdf[0] & 0xFF); // '%'
        assertEquals(0x50, pdf[1] & 0xFF); // 'P'
        assertEquals(0x44, pdf[2] & 0xFF); // 'D'
        assertEquals(0x46, pdf[3] & 0xFF); // 'F'

        verify(patientService).getPatientProfile(patientId);
        verify(medicationService).getAllMedicationsForPatient(patientId);
        verify(familyMemberService).getFamilyMembersByPatientId(patientId);
    }

    @Test
    @DisplayName("generateVialOfLifePdf_patientNotFound_throwsIllegalArgumentException")
    void generateVialOfLifePdf_patientNotFound_throwsIllegalArgumentException() throws Exception {
        when(patientService.getPatientProfile(999L)).thenReturn(Optional.empty());

        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> vialOfLifePdfService.generateVialOfLifePdf("VIAL999"));
        assertTrue(ex.getMessage().contains("Patient not found"));
    }

    @Test
    @DisplayName("generateVialOfLifePdf_invalidEmergencyIdFormat_throwsIllegalArgumentException")
    void generateVialOfLifePdf_invalidEmergencyIdFormat_throwsIllegalArgumentException() throws Exception {
        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> vialOfLifePdfService.generateVialOfLifePdf("INVALID123"));
        assertTrue(ex.getMessage().contains("Invalid emergency ID format"));
    }

    @Test
    @DisplayName("generateVialOfLifePdf_emergencyIdWithNonNumericSuffix_throwsIllegalArgumentException")
    void generateVialOfLifePdf_emergencyIdWithNonNumericSuffix_throwsIllegalArgumentException() throws Exception {
        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> vialOfLifePdfService.generateVialOfLifePdf("VIALABC"));
        assertTrue(ex.getMessage().contains("Invalid emergency ID format"));
    }

    // ── Profile with various null fields ──

    @Test
    @DisplayName("generateVialOfLifePdf_profileWithNullDob_returnsPdfBytes")
    void generateVialOfLifePdf_profileWithNullDob_returnsPdfBytes() throws Exception {
        final PatientProfileDTO profile = PatientProfileDTO.builder()
                .id(1L)
                .firstName("Alice")
                .lastName("Smith")
                .dob(null)
                .gender(null)
                .phone(null)
                .allergies(null)
                .build();

        when(patientService.getPatientProfile(1L)).thenReturn(Optional.of(profile));
        when(medicationService.getAllMedicationsForPatient(1L)).thenReturn(Collections.emptyList());
        when(familyMemberService.getFamilyMembersByPatientId(1L)).thenReturn(Collections.emptyList());

        final byte[] pdf = vialOfLifePdfService.generateVialOfLifePdf("VIAL1");

        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
    }

    @Test
    @DisplayName("generateVialOfLifePdf_profileWithInvalidDobFormat_stillReturnsPdf")
    void generateVialOfLifePdf_profileWithInvalidDobFormat_stillReturnsPdf() throws Exception {
        final PatientProfileDTO profile = PatientProfileDTO.builder()
                .id(2L)
                .firstName("Bob")
                .lastName("Jones")
                .dob("not-a-date")
                .gender(Gender.FEMALE)
                .phone("555-9876")
                .allergies(Collections.emptyList())
                .build();

        when(patientService.getPatientProfile(2L)).thenReturn(Optional.of(profile));
        when(medicationService.getAllMedicationsForPatient(2L)).thenReturn(null);
        when(familyMemberService.getFamilyMembersByPatientId(2L)).thenReturn(null);

        final byte[] pdf = vialOfLifePdfService.generateVialOfLifePdf("VIAL2");

        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
    }

    @Test
    @DisplayName("generateVialOfLifePdf_emptyMedicationsAndContacts_returnsPdf")
    void generateVialOfLifePdf_emptyMedicationsAndContacts_returnsPdf() throws Exception {
        final PatientProfileDTO profile = PatientProfileDTO.builder()
                .id(3L)
                .firstName("Charlie")
                .lastName("Brown")
                .dob(LocalDate.of(1990, 5, 15).toString())
                .gender(Gender.OTHER)
                .phone(null)
                .allergies(List.of())
                .build();

        when(patientService.getPatientProfile(3L)).thenReturn(Optional.of(profile));
        when(medicationService.getAllMedicationsForPatient(3L)).thenReturn(List.of());
        when(familyMemberService.getFamilyMembersByPatientId(3L)).thenReturn(List.of());

        final byte[] pdf = vialOfLifePdfService.generateVialOfLifePdf("VIAL3");

        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
    }

    @Test
    @DisplayName("generateVialOfLifePdf_activeMedicationsWithNullDosageAndFrequency_returnsPdf")
    void generateVialOfLifePdf_activeMedicationsWithNullDosageAndFrequency_returnsPdf() throws Exception {
        final PatientProfileDTO profile = PatientProfileDTO.builder()
                .id(4L)
                .firstName("Dana")
                .lastName("White")
                .dob(null)
                .gender(null)
                .phone(null)
                .allergies(null)
                .build();

        final MedicationDTO medNoDosage = MedicationDTO.builder()
                .medicationName("Aspirin")
                .dosage(null)
                .frequency(null)
                .isActive(true)
                .build();

        when(patientService.getPatientProfile(4L)).thenReturn(Optional.of(profile));
        when(medicationService.getAllMedicationsForPatient(4L)).thenReturn(List.of(medNoDosage));
        when(familyMemberService.getFamilyMembersByPatientId(4L)).thenReturn(null);

        final byte[] pdf = vialOfLifePdfService.generateVialOfLifePdf("VIAL4");

        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
    }

    @Test
    @DisplayName("generateVialOfLifePdf_contactWithNullRelationshipAndEmail_returnsPdf")
    void generateVialOfLifePdf_contactWithNullRelationshipAndEmail_returnsPdf() throws Exception {
        final PatientProfileDTO profile = PatientProfileDTO.builder()
                .id(5L)
                .firstName("Eve")
                .lastName("Green")
                .dob(null)
                .gender(null)
                .phone(null)
                .allergies(null)
                .build();

        final FamilyMemberLinkResponse contact = new FamilyMemberLinkResponse(
                1L, 20L, "Frank Green", null, 5L, "Eve Green",
                null, "ACTIVE", LocalDateTime.now(), "self");

        when(patientService.getPatientProfile(5L)).thenReturn(Optional.of(profile));
        when(medicationService.getAllMedicationsForPatient(5L)).thenReturn(Collections.emptyList());
        when(familyMemberService.getFamilyMembersByPatientId(5L)).thenReturn(List.of(contact));

        final byte[] pdf = vialOfLifePdfService.generateVialOfLifePdf("VIAL5");

        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
    }

    @Test
    @DisplayName("generateVialOfLifePdf_allActiveMedsFiltered_returnsPdf")
    void generateVialOfLifePdf_allActiveMedsFiltered_returnsPdf() throws Exception {
        final PatientProfileDTO profile = PatientProfileDTO.builder()
                .id(6L)
                .firstName("Grace")
                .lastName("Hopper")
                .dob(null)
                .gender(null)
                .phone(null)
                .allergies(null)
                .build();

        // All inactive meds - activeMeds list will be empty
        final MedicationDTO inactive1 = MedicationDTO.builder()
                .medicationName("OldDrug")
                .dosage("10mg")
                .frequency("daily")
                .isActive(false)
                .build();

        when(patientService.getPatientProfile(6L)).thenReturn(Optional.of(profile));
        when(medicationService.getAllMedicationsForPatient(6L)).thenReturn(List.of(inactive1));
        when(familyMemberService.getFamilyMembersByPatientId(6L)).thenReturn(Collections.emptyList());

        final byte[] pdf = vialOfLifePdfService.generateVialOfLifePdf("VIAL6");

        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
    }

    @Test
    @DisplayName("generateVialOfLifePdf_allergyWithSeverityOnly_returnsPdf")
    void generateVialOfLifePdf_allergyWithSeverityOnly_returnsPdf() throws Exception {
        final PatientProfileDTO profile = PatientProfileDTO.builder()
                .id(7L)
                .firstName("Henry")
                .lastName("Ford")
                .dob(null)
                .gender(null)
                .phone(null)
                .allergies(List.of(
                        AllergyDTO.builder()
                                .allergen("Dust")
                                .severity(AllergySeverity.MILD)
                                .reaction(null)
                                .build()
                ))
                .build();

        when(patientService.getPatientProfile(7L)).thenReturn(Optional.of(profile));
        when(medicationService.getAllMedicationsForPatient(7L)).thenReturn(Collections.emptyList());
        when(familyMemberService.getFamilyMembersByPatientId(7L)).thenReturn(Collections.emptyList());

        final byte[] pdf = vialOfLifePdfService.generateVialOfLifePdf("VIAL7");

        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
    }

    @Test
    @DisplayName("generateVialOfLifePdf_medicationWithDosageButNoFrequency_returnsPdf")
    void generateVialOfLifePdf_medicationWithDosageButNoFrequency_returnsPdf() throws Exception {
        final PatientProfileDTO profile = PatientProfileDTO.builder()
                .id(8L)
                .firstName("Ivy")
                .lastName("Lee")
                .dob(null)
                .gender(null)
                .phone(null)
                .allergies(null)
                .build();

        final MedicationDTO med = MedicationDTO.builder()
                .medicationName("Lipitor")
                .dosage("20mg")
                .frequency(null)
                .isActive(true)
                .build();

        when(patientService.getPatientProfile(8L)).thenReturn(Optional.of(profile));
        when(medicationService.getAllMedicationsForPatient(8L)).thenReturn(List.of(med));
        when(familyMemberService.getFamilyMembersByPatientId(8L)).thenReturn(Collections.emptyList());

        final byte[] pdf = vialOfLifePdfService.generateVialOfLifePdf("VIAL8");

        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
    }
}
