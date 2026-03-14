package com.careconnect.service;

import com.careconnect.dto.AllergyDTO;
import com.careconnect.model.Allergy;
import com.careconnect.model.Allergy.AllergyType;
import com.careconnect.model.Allergy.AllergySeverity;
import com.careconnect.model.Patient;
import com.careconnect.repository.AllergyRepository;
import com.careconnect.repository.PatientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AllergyServiceTest {

    @Mock
    private AllergyRepository allergyRepository;

    @Mock
    private PatientRepository patientRepository;

    @InjectMocks
    private AllergyService allergyService;

    private Patient patient;
    private Allergy allergy;
    private AllergyDTO allergyDTO;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);

        patient = Patient.builder()
                .id(1L)
                .firstName("John")
                .lastName("Doe")
                .build();

        allergy = Allergy.builder()
                .id(10L)
                .patient(patient)
                .allergen("Peanuts")
                .allergyType(AllergyType.FOOD)
                .severity(AllergySeverity.SEVERE)
                .reaction("Anaphylaxis")
                .notes("Carry EpiPen")
                .diagnosedDate("2024-01-15")
                .isActive(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        allergyDTO = AllergyDTO.builder()
                .id(10L)
                .patientId(1L)
                .allergen("Peanuts")
                .allergyType(AllergyType.FOOD)
                .severity(AllergySeverity.SEVERE)
                .reaction("Anaphylaxis")
                .notes("Carry EpiPen")
                .diagnosedDate("2024-01-15")
                .isActive(true)
                .build();
    }

    // ========== createAllergy tests ==========

    @Test
    @DisplayName("createAllergy - valid DTO with isActive set - returns saved AllergyDTO")
    void createAllergy_validDtoWithIsActive_returnsSavedAllergyDTO() throws Exception {
        when(patientRepository.findById(1L)).thenReturn(Optional.of(patient));
        when(allergyRepository.existsByPatientAndAllergenIgnoreCaseAndIsActiveTrue(patient, "Peanuts"))
                .thenReturn(false);
        when(allergyRepository.save(any(Allergy.class))).thenReturn(allergy);

        final AllergyDTO result = allergyService.createAllergy(allergyDTO);

        assertNotNull(result);
        assertEquals(10L, result.id());
        assertEquals(1L, result.patientId());
        assertEquals("Peanuts", result.allergen());
        assertEquals(AllergyType.FOOD, result.allergyType());
        assertEquals(AllergySeverity.SEVERE, result.severity());
        assertEquals("Anaphylaxis", result.reaction());
        assertEquals("Carry EpiPen", result.notes());
        assertEquals("2024-01-15", result.diagnosedDate());
        assertTrue(result.isActive());

        verify(patientRepository).findById(1L);
        verify(allergyRepository).existsByPatientAndAllergenIgnoreCaseAndIsActiveTrue(patient, "Peanuts");
        verify(allergyRepository).save(any(Allergy.class));
    }

    @Test
    @DisplayName("createAllergy - isActive is null - defaults to true")
    void createAllergy_isActiveNull_defaultsToTrue() throws Exception {
        final AllergyDTO dtoWithNullActive = AllergyDTO.builder()
                .patientId(1L)
                .allergen("Dust")
                .allergyType(AllergyType.ENVIRONMENTAL)
                .severity(AllergySeverity.MILD)
                .reaction("Sneezing")
                .notes(null)
                .diagnosedDate(null)
                .isActive(null)
                .build();

        final Allergy savedAllergy = Allergy.builder()
                .id(11L)
                .patient(patient)
                .allergen("Dust")
                .allergyType(AllergyType.ENVIRONMENTAL)
                .severity(AllergySeverity.MILD)
                .reaction("Sneezing")
                .notes(null)
                .diagnosedDate(null)
                .isActive(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(patientRepository.findById(1L)).thenReturn(Optional.of(patient));
        when(allergyRepository.existsByPatientAndAllergenIgnoreCaseAndIsActiveTrue(patient, "Dust"))
                .thenReturn(false);
        when(allergyRepository.save(any(Allergy.class))).thenReturn(savedAllergy);

        final AllergyDTO result = allergyService.createAllergy(dtoWithNullActive);

        assertNotNull(result);
        assertTrue(result.isActive());
        verify(allergyRepository).save(argThat(a -> Boolean.TRUE.equals(a.getIsActive())));
    }

    @Test
    @DisplayName("createAllergy - isActive is false - uses provided value")
    void createAllergy_isActiveFalse_usesProvidedValue() throws Exception {
        final AllergyDTO dtoWithFalseActive = AllergyDTO.builder()
                .patientId(1L)
                .allergen("Shellfish")
                .allergyType(AllergyType.FOOD)
                .severity(AllergySeverity.MODERATE)
                .reaction("Hives")
                .isActive(false)
                .build();

        final Allergy savedAllergy = Allergy.builder()
                .id(12L)
                .patient(patient)
                .allergen("Shellfish")
                .allergyType(AllergyType.FOOD)
                .severity(AllergySeverity.MODERATE)
                .reaction("Hives")
                .isActive(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(patientRepository.findById(1L)).thenReturn(Optional.of(patient));
        when(allergyRepository.existsByPatientAndAllergenIgnoreCaseAndIsActiveTrue(patient, "Shellfish"))
                .thenReturn(false);
        when(allergyRepository.save(any(Allergy.class))).thenReturn(savedAllergy);

        final AllergyDTO result = allergyService.createAllergy(dtoWithFalseActive);

        assertNotNull(result);
        assertFalse(result.isActive());
        verify(allergyRepository).save(argThat(a -> Boolean.FALSE.equals(a.getIsActive())));
    }

    @Test
    @DisplayName("createAllergy - patient not found - throws IllegalArgumentException")
    void createAllergy_patientNotFound_throwsIllegalArgumentException() throws Exception {
        when(patientRepository.findById(99L)).thenReturn(Optional.empty());

        final AllergyDTO dto = AllergyDTO.builder()
                .patientId(99L)
                .allergen("Pollen")
                .build();

        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> allergyService.createAllergy(dto));

        assertEquals("Patient not found with id: 99", ex.getMessage());
        verify(patientRepository).findById(99L);
        verify(allergyRepository, never()).save(any());
    }

    @Test
    @DisplayName("createAllergy - duplicate active allergy - throws IllegalArgumentException")
    void createAllergy_duplicateActiveAllergy_throwsIllegalArgumentException() throws Exception {
        when(patientRepository.findById(1L)).thenReturn(Optional.of(patient));
        when(allergyRepository.existsByPatientAndAllergenIgnoreCaseAndIsActiveTrue(patient, "Peanuts"))
                .thenReturn(true);

        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> allergyService.createAllergy(allergyDTO));

        assertEquals("Active allergy for 'Peanuts' already exists for this patient", ex.getMessage());
        verify(allergyRepository, never()).save(any());
    }

    // ========== updateAllergy tests ==========

    @Test
    @DisplayName("updateAllergy - all fields provided - updates all fields")
    void updateAllergy_allFieldsProvided_updatesAllFields() throws Exception {
        final Allergy existingAllergy = Allergy.builder()
                .id(10L)
                .patient(patient)
                .allergen("OldAllergen")
                .allergyType(AllergyType.OTHER)
                .severity(AllergySeverity.MILD)
                .reaction("OldReaction")
                .notes("OldNotes")
                .diagnosedDate("2023-01-01")
                .isActive(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        final AllergyDTO updateDTO = AllergyDTO.builder()
                .allergen("NewAllergen")
                .allergyType(AllergyType.MEDICATION)
                .severity(AllergySeverity.LIFE_THREATENING)
                .reaction("NewReaction")
                .notes("NewNotes")
                .diagnosedDate("2025-06-15")
                .isActive(true)
                .build();

        when(allergyRepository.findById(10L)).thenReturn(Optional.of(existingAllergy));
        when(allergyRepository.save(any(Allergy.class))).thenAnswer(invocation -> invocation.getArgument(0));

        final AllergyDTO result = allergyService.updateAllergy(10L, updateDTO);

        assertNotNull(result);
        assertEquals("NewAllergen", result.allergen());
        assertEquals(AllergyType.MEDICATION, result.allergyType());
        assertEquals(AllergySeverity.LIFE_THREATENING, result.severity());
        assertEquals("NewReaction", result.reaction());
        assertEquals("NewNotes", result.notes());
        assertEquals("2025-06-15", result.diagnosedDate());
        assertTrue(result.isActive());

        verify(allergyRepository).findById(10L);
        verify(allergyRepository).save(existingAllergy);
    }

    @Test
    @DisplayName("updateAllergy - all fields null - does not update any fields")
    void updateAllergy_allFieldsNull_doesNotUpdateAnyFields() throws Exception {
        final Allergy existingAllergy = Allergy.builder()
                .id(10L)
                .patient(patient)
                .allergen("Peanuts")
                .allergyType(AllergyType.FOOD)
                .severity(AllergySeverity.SEVERE)
                .reaction("Anaphylaxis")
                .notes("Carry EpiPen")
                .diagnosedDate("2024-01-15")
                .isActive(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        final AllergyDTO updateDTO = AllergyDTO.builder()
                .allergen(null)
                .allergyType(null)
                .severity(null)
                .reaction(null)
                .notes(null)
                .diagnosedDate(null)
                .isActive(null)
                .build();

        when(allergyRepository.findById(10L)).thenReturn(Optional.of(existingAllergy));
        when(allergyRepository.save(any(Allergy.class))).thenAnswer(invocation -> invocation.getArgument(0));

        final AllergyDTO result = allergyService.updateAllergy(10L, updateDTO);

        assertNotNull(result);
        assertEquals("Peanuts", result.allergen());
        assertEquals(AllergyType.FOOD, result.allergyType());
        assertEquals(AllergySeverity.SEVERE, result.severity());
        assertEquals("Anaphylaxis", result.reaction());
        assertEquals("Carry EpiPen", result.notes());
        assertEquals("2024-01-15", result.diagnosedDate());
        assertTrue(result.isActive());
    }

    @Test
    @DisplayName("updateAllergy - only allergen provided - updates only allergen")
    void updateAllergy_onlyAllergenProvided_updatesOnlyAllergen() throws Exception {
        final Allergy existingAllergy = Allergy.builder()
                .id(10L)
                .patient(patient)
                .allergen("OldAllergen")
                .allergyType(AllergyType.FOOD)
                .severity(AllergySeverity.MILD)
                .reaction("OldReaction")
                .notes("OldNotes")
                .diagnosedDate("2024-01-01")
                .isActive(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        final AllergyDTO updateDTO = AllergyDTO.builder()
                .allergen("UpdatedAllergen")
                .build();

        when(allergyRepository.findById(10L)).thenReturn(Optional.of(existingAllergy));
        when(allergyRepository.save(any(Allergy.class))).thenAnswer(invocation -> invocation.getArgument(0));

        final AllergyDTO result = allergyService.updateAllergy(10L, updateDTO);

        assertEquals("UpdatedAllergen", result.allergen());
        assertEquals(AllergyType.FOOD, result.allergyType());
        assertEquals(AllergySeverity.MILD, result.severity());
        assertEquals("OldReaction", result.reaction());
        assertEquals("OldNotes", result.notes());
        assertEquals("2024-01-01", result.diagnosedDate());
        assertTrue(result.isActive());
    }

    @Test
    @DisplayName("updateAllergy - only allergyType provided - updates only allergyType")
    void updateAllergy_onlyAllergyTypeProvided_updatesOnlyAllergyType() throws Exception {
        final Allergy existingAllergy = Allergy.builder()
                .id(10L)
                .patient(patient)
                .allergen("Peanuts")
                .allergyType(AllergyType.FOOD)
                .severity(AllergySeverity.MILD)
                .isActive(true)
                .createdAt(Instant.now())
                .build();

        final AllergyDTO updateDTO = AllergyDTO.builder()
                .allergyType(AllergyType.ENVIRONMENTAL)
                .build();

        when(allergyRepository.findById(10L)).thenReturn(Optional.of(existingAllergy));
        when(allergyRepository.save(any(Allergy.class))).thenAnswer(invocation -> invocation.getArgument(0));

        final AllergyDTO result = allergyService.updateAllergy(10L, updateDTO);

        assertEquals(AllergyType.ENVIRONMENTAL, result.allergyType());
        assertEquals("Peanuts", result.allergen());
    }

    @Test
    @DisplayName("updateAllergy - only severity provided - updates only severity")
    void updateAllergy_onlySeverityProvided_updatesOnlySeverity() throws Exception {
        final Allergy existingAllergy = Allergy.builder()
                .id(10L)
                .patient(patient)
                .allergen("Peanuts")
                .severity(AllergySeverity.MILD)
                .isActive(true)
                .createdAt(Instant.now())
                .build();

        final AllergyDTO updateDTO = AllergyDTO.builder()
                .severity(AllergySeverity.SEVERE)
                .build();

        when(allergyRepository.findById(10L)).thenReturn(Optional.of(existingAllergy));
        when(allergyRepository.save(any(Allergy.class))).thenAnswer(invocation -> invocation.getArgument(0));

        final AllergyDTO result = allergyService.updateAllergy(10L, updateDTO);

        assertEquals(AllergySeverity.SEVERE, result.severity());
    }

    @Test
    @DisplayName("updateAllergy - only reaction provided - updates only reaction")
    void updateAllergy_onlyReactionProvided_updatesOnlyReaction() throws Exception {
        final Allergy existingAllergy = Allergy.builder()
                .id(10L)
                .patient(patient)
                .allergen("Peanuts")
                .reaction("OldReaction")
                .isActive(true)
                .createdAt(Instant.now())
                .build();

        final AllergyDTO updateDTO = AllergyDTO.builder()
                .reaction("NewReaction")
                .build();

        when(allergyRepository.findById(10L)).thenReturn(Optional.of(existingAllergy));
        when(allergyRepository.save(any(Allergy.class))).thenAnswer(invocation -> invocation.getArgument(0));

        final AllergyDTO result = allergyService.updateAllergy(10L, updateDTO);

        assertEquals("NewReaction", result.reaction());
    }

    @Test
    @DisplayName("updateAllergy - only notes provided - updates only notes")
    void updateAllergy_onlyNotesProvided_updatesOnlyNotes() throws Exception {
        final Allergy existingAllergy = Allergy.builder()
                .id(10L)
                .patient(patient)
                .allergen("Peanuts")
                .notes("OldNotes")
                .isActive(true)
                .createdAt(Instant.now())
                .build();

        final AllergyDTO updateDTO = AllergyDTO.builder()
                .notes("NewNotes")
                .build();

        when(allergyRepository.findById(10L)).thenReturn(Optional.of(existingAllergy));
        when(allergyRepository.save(any(Allergy.class))).thenAnswer(invocation -> invocation.getArgument(0));

        final AllergyDTO result = allergyService.updateAllergy(10L, updateDTO);

        assertEquals("NewNotes", result.notes());
    }

    @Test
    @DisplayName("updateAllergy - only diagnosedDate provided - updates only diagnosedDate")
    void updateAllergy_onlyDiagnosedDateProvided_updatesOnlyDiagnosedDate() throws Exception {
        final Allergy existingAllergy = Allergy.builder()
                .id(10L)
                .patient(patient)
                .allergen("Peanuts")
                .diagnosedDate("2023-01-01")
                .isActive(true)
                .createdAt(Instant.now())
                .build();

        final AllergyDTO updateDTO = AllergyDTO.builder()
                .diagnosedDate("2025-12-25")
                .build();

        when(allergyRepository.findById(10L)).thenReturn(Optional.of(existingAllergy));
        when(allergyRepository.save(any(Allergy.class))).thenAnswer(invocation -> invocation.getArgument(0));

        final AllergyDTO result = allergyService.updateAllergy(10L, updateDTO);

        assertEquals("2025-12-25", result.diagnosedDate());
    }

    @Test
    @DisplayName("updateAllergy - only isActive provided - updates only isActive")
    void updateAllergy_onlyIsActiveProvided_updatesOnlyIsActive() throws Exception {
        final Allergy existingAllergy = Allergy.builder()
                .id(10L)
                .patient(patient)
                .allergen("Peanuts")
                .isActive(true)
                .createdAt(Instant.now())
                .build();

        final AllergyDTO updateDTO = AllergyDTO.builder()
                .isActive(false)
                .build();

        when(allergyRepository.findById(10L)).thenReturn(Optional.of(existingAllergy));
        when(allergyRepository.save(any(Allergy.class))).thenAnswer(invocation -> invocation.getArgument(0));

        final AllergyDTO result = allergyService.updateAllergy(10L, updateDTO);

        assertFalse(result.isActive());
    }

    @Test
    @DisplayName("updateAllergy - allergy not found - throws IllegalArgumentException")
    void updateAllergy_allergyNotFound_throwsIllegalArgumentException() throws Exception {
        when(allergyRepository.findById(999L)).thenReturn(Optional.empty());

        final AllergyDTO updateDTO = AllergyDTO.builder()
                .allergen("Pollen")
                .build();

        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> allergyService.updateAllergy(999L, updateDTO));

        assertEquals("Allergy not found with id: 999", ex.getMessage());
        verify(allergyRepository, never()).save(any());
    }

    // ========== getAllergiesForPatient tests ==========

    @Test
    @DisplayName("getAllergiesForPatient - patient has allergies - returns list of AllergyDTOs")
    void getAllergiesForPatient_patientHasAllergies_returnsListOfAllergyDTOs() throws Exception {
        final Allergy allergy2 = Allergy.builder()
                .id(11L)
                .patient(patient)
                .allergen("Dust")
                .allergyType(AllergyType.ENVIRONMENTAL)
                .severity(AllergySeverity.MILD)
                .reaction("Sneezing")
                .isActive(true)
                .createdAt(Instant.now())
                .build();

        when(allergyRepository.findByPatientId(1L)).thenReturn(List.of(allergy, allergy2));

        final List<AllergyDTO> results = allergyService.getAllergiesForPatient(1L);

        assertNotNull(results);
        assertEquals(2, results.size());
        assertEquals("Peanuts", results.get(0).allergen());
        assertEquals("Dust", results.get(1).allergen());
        verify(allergyRepository).findByPatientId(1L);
    }

    @Test
    @DisplayName("getAllergiesForPatient - patient has no allergies - returns empty list")
    void getAllergiesForPatient_patientHasNoAllergies_returnsEmptyList() throws Exception {
        when(allergyRepository.findByPatientId(1L)).thenReturn(Collections.emptyList());

        final List<AllergyDTO> results = allergyService.getAllergiesForPatient(1L);

        assertNotNull(results);
        assertTrue(results.isEmpty());
        verify(allergyRepository).findByPatientId(1L);
    }

    // ========== getActiveAllergiesForPatient tests ==========

    @Test
    @DisplayName("getActiveAllergiesForPatient - patient has active allergies - returns list of active AllergyDTOs")
    void getActiveAllergiesForPatient_patientHasActiveAllergies_returnsListOfActiveAllergyDTOs() throws Exception {
        when(allergyRepository.findActiveAllergiesByPatientId(1L)).thenReturn(List.of(allergy));

        final List<AllergyDTO> results = allergyService.getActiveAllergiesForPatient(1L);

        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals("Peanuts", results.get(0).allergen());
        assertTrue(results.get(0).isActive());
        verify(allergyRepository).findActiveAllergiesByPatientId(1L);
    }

    @Test
    @DisplayName("getActiveAllergiesForPatient - patient has no active allergies - returns empty list")
    void getActiveAllergiesForPatient_noActiveAllergies_returnsEmptyList() throws Exception {
        when(allergyRepository.findActiveAllergiesByPatientId(1L)).thenReturn(Collections.emptyList());

        final List<AllergyDTO> results = allergyService.getActiveAllergiesForPatient(1L);

        assertNotNull(results);
        assertTrue(results.isEmpty());
        verify(allergyRepository).findActiveAllergiesByPatientId(1L);
    }

    // ========== getAllergy tests ==========

    @Test
    @DisplayName("getAllergy - allergy exists - returns Optional containing AllergyDTO")
    void getAllergy_allergyExists_returnsOptionalContainingAllergyDTO() throws Exception {
        when(allergyRepository.findById(10L)).thenReturn(Optional.of(allergy));

        final Optional<AllergyDTO> result = allergyService.getAllergy(10L);

        assertTrue(result.isPresent());
        final AllergyDTO dto = result.get();
        assertEquals(10L, dto.id());
        assertEquals(1L, dto.patientId());
        assertEquals("Peanuts", dto.allergen());
        assertEquals(AllergyType.FOOD, dto.allergyType());
        assertEquals(AllergySeverity.SEVERE, dto.severity());
        assertEquals("Anaphylaxis", dto.reaction());
        assertEquals("Carry EpiPen", dto.notes());
        assertEquals("2024-01-15", dto.diagnosedDate());
        assertTrue(dto.isActive());
        verify(allergyRepository).findById(10L);
    }

    @Test
    @DisplayName("getAllergy - allergy does not exist - returns empty Optional")
    void getAllergy_allergyDoesNotExist_returnsEmptyOptional() throws Exception {
        when(allergyRepository.findById(999L)).thenReturn(Optional.empty());

        final Optional<AllergyDTO> result = allergyService.getAllergy(999L);

        assertFalse(result.isPresent());
        verify(allergyRepository).findById(999L);
    }

    // ========== deactivateAllergy tests ==========

    @Test
    @DisplayName("deactivateAllergy - allergy exists - sets isActive to false and saves")
    void deactivateAllergy_allergyExists_setsIsActiveToFalseAndSaves() throws Exception {
        final Allergy activeAllergy = Allergy.builder()
                .id(10L)
                .patient(patient)
                .allergen("Peanuts")
                .isActive(true)
                .createdAt(Instant.now())
                .build();

        when(allergyRepository.findById(10L)).thenReturn(Optional.of(activeAllergy));
        when(allergyRepository.save(any(Allergy.class))).thenReturn(activeAllergy);

        allergyService.deactivateAllergy(10L);

        assertFalse(activeAllergy.getIsActive());
        verify(allergyRepository).findById(10L);
        verify(allergyRepository).save(activeAllergy);
    }

    @Test
    @DisplayName("deactivateAllergy - allergy not found - throws IllegalArgumentException")
    void deactivateAllergy_allergyNotFound_throwsIllegalArgumentException() throws Exception {
        when(allergyRepository.findById(999L)).thenReturn(Optional.empty());

        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> allergyService.deactivateAllergy(999L));

        assertEquals("Allergy not found with id: 999", ex.getMessage());
        verify(allergyRepository, never()).save(any());
    }

    // ========== deleteAllergy tests ==========

    @Test
    @DisplayName("deleteAllergy - allergy exists - deletes allergy by id")
    void deleteAllergy_allergyExists_deletesAllergyById() throws Exception {
        when(allergyRepository.existsById(10L)).thenReturn(true);

        allergyService.deleteAllergy(10L);

        verify(allergyRepository).existsById(10L);
        verify(allergyRepository).deleteById(10L);
    }

    @Test
    @DisplayName("deleteAllergy - allergy not found - throws IllegalArgumentException")
    void deleteAllergy_allergyNotFound_throwsIllegalArgumentException() throws Exception {
        when(allergyRepository.existsById(999L)).thenReturn(false);

        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> allergyService.deleteAllergy(999L));

        assertEquals("Allergy not found with id: 999", ex.getMessage());
        verify(allergyRepository, never()).deleteById(anyLong());
    }
}
