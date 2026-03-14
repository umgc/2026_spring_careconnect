package com.careconnect.service;

import com.careconnect.dto.ChatRequest;
import com.careconnect.model.ClinicalNote;
import com.careconnect.model.User;
import com.careconnect.model.UserAIConfig;
import com.careconnect.model.Vital;
import com.careconnect.repository.ClinicalNotesRepository;
import com.careconnect.repository.UserRepository;
import com.careconnect.repository.VitalsRepository;
import com.careconnect.service.MedicalDataAnonymizer.AnonymizationLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.PageRequest;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PrivacyAwareMedicalContextServiceTest {

    @Mock
    private MedicalDataAnonymizer anonymizer;

    @Mock
    private VitalsRepository vitalsRepository;

    @Mock
    private ClinicalNotesRepository clinicalNotesRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private PrivacyAwareMedicalContextService service;

    private Long patientId;
    private UserAIConfig aiConfig;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        patientId = 1L;
        aiConfig = new UserAIConfig();
        aiConfig.setIncludeVitalsByDefault(true);
        aiConfig.setIncludeMedicationsByDefault(true);
        aiConfig.setIncludeNotesByDefault(true);
    }

    @Test
    @DisplayName("buildAnonymizedPatientContext - all flags false - returns only demographics and disclaimer")
    void buildAnonymizedPatientContext_allFlagsFalse_returnsOnlyDemographicsAndDisclaimer() throws Exception {
        final ChatRequest request = ChatRequest.builder()
                .includeVitals(false)
                .includeMedications(false)
                .includeNotes(false)
                .enableDifferentialPrivacy(false)
                .anonymizationLevel(AnonymizationLevel.MODERATE)
                .build();

        when(userRepository.findById(patientId)).thenReturn(Optional.of(new User()));
        when(anonymizer.generatePseudoId(patientId)).thenReturn("Patient_1234");
        when(anonymizer.anonymizePatientContext(anyString(), eq(patientId), eq(AnonymizationLevel.MODERATE)))
                .thenAnswer(inv -> inv.getArgument(0));

        final String result = service.buildAnonymizedPatientContext(patientId, request, aiConfig);

        assertNotNull(result);
        assertTrue(result.contains("PRIVACY NOTICE"));
        assertTrue(result.contains("MEDICAL DISCLAIMER"));
        verify(vitalsRepository, never()).findRecentByPatientId(anyLong(), any());
        verify(clinicalNotesRepository, never()).findRecentByPatientId(anyLong(), any());
    }

    @Test
    @DisplayName("buildAnonymizedPatientContext - include vitals true but aiConfig false - skips vitals")
    void buildAnonymizedPatientContext_includeVitalsTrueAiConfigFalse_skipsVitals() throws Exception {
        final ChatRequest request = ChatRequest.builder()
                .includeVitals(true)
                .includeMedications(false)
                .includeNotes(false)
                .enableDifferentialPrivacy(false)
                .anonymizationLevel(AnonymizationLevel.MODERATE)
                .build();

        aiConfig.setIncludeVitalsByDefault(false);

        when(userRepository.findById(patientId)).thenReturn(Optional.of(new User()));
        when(anonymizer.generatePseudoId(patientId)).thenReturn("Patient_1234");
        when(anonymizer.anonymizePatientContext(anyString(), eq(patientId), eq(AnonymizationLevel.MODERATE)))
                .thenAnswer(inv -> inv.getArgument(0));

        final String result = service.buildAnonymizedPatientContext(patientId, request, aiConfig);

        assertNotNull(result);
        verify(vitalsRepository, never()).findRecentByPatientId(anyLong(), any());
    }

    @Test
    @DisplayName("buildAnonymizedPatientContext - all sections enabled - includes vitals medications and notes")
    void buildAnonymizedPatientContext_allSectionsEnabled_includesVitalsMedicationsAndNotes() throws Exception {
        final ChatRequest request = ChatRequest.builder()
                .includeVitals(true)
                .includeMedications(true)
                .includeNotes(true)
                .enableDifferentialPrivacy(false)
                .anonymizationLevel(AnonymizationLevel.MODERATE)
                .build();

        final Vital vital = Vital.builder().vitalType("HEART_RATE").value("72").build();
        final ClinicalNote note = ClinicalNote.builder().noteType("ASSESSMENT").content("Patient is stable").build();

        when(userRepository.findById(patientId)).thenReturn(Optional.of(new User()));
        when(anonymizer.generatePseudoId(patientId)).thenReturn("Patient_1234");
        when(vitalsRepository.findRecentByPatientId(eq(patientId), any(PageRequest.class)))
                .thenReturn(List.of(vital));
        when(clinicalNotesRepository.findRecentByPatientId(eq(patientId), any(PageRequest.class)))
                .thenReturn(List.of(note));
        when(anonymizer.anonymizePatientContext(anyString(), eq(patientId), eq(AnonymizationLevel.MODERATE)))
                .thenAnswer(inv -> inv.getArgument(0));

        final String result = service.buildAnonymizedPatientContext(patientId, request, aiConfig);

        assertNotNull(result);
        assertTrue(result.contains("PRIVACY NOTICE"));
        assertTrue(result.contains("MEDICAL DISCLAIMER"));
    }

    @Test
    @DisplayName("buildAnonymizedPatientContext - differential privacy enabled - calls addDifferentialPrivacyNoise")
    void buildAnonymizedPatientContext_differentialPrivacyEnabled_callsAddDifferentialPrivacyNoise() throws Exception {
        final ChatRequest request = ChatRequest.builder()
                .includeVitals(false)
                .includeMedications(false)
                .includeNotes(false)
                .enableDifferentialPrivacy(true)
                .anonymizationLevel(AnonymizationLevel.MODERATE)
                .build();

        when(userRepository.findById(patientId)).thenReturn(Optional.of(new User()));
        when(anonymizer.generatePseudoId(patientId)).thenReturn("Patient_1234");
        when(anonymizer.anonymizePatientContext(anyString(), eq(patientId), eq(AnonymizationLevel.MODERATE)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(anonymizer.addDifferentialPrivacyNoise(anyString(), eq(0.1)))
                .thenReturn("noisy context");

        final String result = service.buildAnonymizedPatientContext(patientId, request, aiConfig);

        assertEquals("noisy context", result);
        verify(anonymizer).addDifferentialPrivacyNoise(anyString(), eq(0.1));
    }

    @Test
    @DisplayName("buildAnonymizedPatientContext - user not found - returns demographics not available")
    void buildAnonymizedPatientContext_userNotFound_returnsDemographicsNotAvailable() throws Exception {
        final ChatRequest request = ChatRequest.builder()
                .includeVitals(false)
                .includeMedications(false)
                .includeNotes(false)
                .enableDifferentialPrivacy(false)
                .anonymizationLevel(AnonymizationLevel.MODERATE)
                .build();

        when(userRepository.findById(patientId)).thenReturn(Optional.empty());
        when(anonymizer.anonymizePatientContext(anyString(), eq(patientId), eq(AnonymizationLevel.MODERATE)))
                .thenAnswer(inv -> inv.getArgument(0));

        final String result = service.buildAnonymizedPatientContext(patientId, request, aiConfig);

        assertTrue(result.contains("Patient Demographics: Information not available"));
    }

    @Test
    @DisplayName("buildAnonymizedPatientContext - user found - returns formatted demographics")
    void buildAnonymizedPatientContext_userFound_returnsFormattedDemographics() throws Exception {
        final ChatRequest request = ChatRequest.builder()
                .includeVitals(false)
                .includeMedications(false)
                .includeNotes(false)
                .enableDifferentialPrivacy(false)
                .anonymizationLevel(AnonymizationLevel.MODERATE)
                .build();

        final User user = new User();
        user.setId(patientId);
        when(userRepository.findById(patientId)).thenReturn(Optional.of(user));
        when(anonymizer.generatePseudoId(patientId)).thenReturn("Patient_1234");
        when(anonymizer.anonymizePatientContext(anyString(), eq(patientId), eq(AnonymizationLevel.MODERATE)))
                .thenAnswer(inv -> inv.getArgument(0));

        final String result = service.buildAnonymizedPatientContext(patientId, request, aiConfig);

        assertTrue(result.contains("Patient_1234"));
    }

    @Test
    @DisplayName("buildAnonymizedPatientContext - vitals enabled but empty list - returns no recent vitals message")
    void buildAnonymizedPatientContext_vitalsEnabledEmptyList_returnsNoRecentVitalsMessage() throws Exception {
        final ChatRequest request = ChatRequest.builder()
                .includeVitals(true)
                .includeMedications(false)
                .includeNotes(false)
                .enableDifferentialPrivacy(false)
                .anonymizationLevel(AnonymizationLevel.MODERATE)
                .build();

        when(userRepository.findById(patientId)).thenReturn(Optional.of(new User()));
        when(anonymizer.generatePseudoId(patientId)).thenReturn("Patient_1234");
        when(vitalsRepository.findRecentByPatientId(eq(patientId), any(PageRequest.class)))
                .thenReturn(Collections.emptyList());
        when(anonymizer.anonymizePatientContext(anyString(), eq(patientId), eq(AnonymizationLevel.MODERATE)))
                .thenAnswer(inv -> inv.getArgument(0));

        final String result = service.buildAnonymizedPatientContext(patientId, request, aiConfig);

        assertTrue(result.contains("Recent Vitals: No recent vital signs recorded"));
    }

    @Test
    @DisplayName("buildAnonymizedPatientContext - vitals with STATISTICAL level - returns statistical summary")
    void buildAnonymizedPatientContext_vitalsWithStatisticalLevel_returnsStatisticalSummary() throws Exception {
        final ChatRequest request = ChatRequest.builder()
                .includeVitals(true)
                .includeMedications(false)
                .includeNotes(false)
                .enableDifferentialPrivacy(false)
                .anonymizationLevel(AnonymizationLevel.STATISTICAL)
                .build();

        final Vital vital1 = Vital.builder().vitalType("HEART_RATE").value("72").build();
        final Vital vital2 = Vital.builder().vitalType("HEART_RATE").value("75").build();

        when(userRepository.findById(patientId)).thenReturn(Optional.of(new User()));
        when(anonymizer.generatePseudoId(patientId)).thenReturn("Patient_1234");
        when(vitalsRepository.findRecentByPatientId(eq(patientId), any(PageRequest.class)))
                .thenReturn(List.of(vital1, vital2));
        when(anonymizer.anonymizePatientContext(anyString(), eq(patientId), eq(AnonymizationLevel.STATISTICAL)))
                .thenAnswer(inv -> inv.getArgument(0));

        final String result = service.buildAnonymizedPatientContext(patientId, request, aiConfig);

        assertTrue(result.contains("HEART_RATE"));
    }

    @Test
    @DisplayName("buildAnonymizedPatientContext - vitals with stable numeric trend - returns stable")
    void buildAnonymizedPatientContext_vitalsWithStableNumericTrend_returnsStable() throws Exception {
        final ChatRequest request = ChatRequest.builder()
                .includeVitals(true)
                .includeMedications(false)
                .includeNotes(false)
                .enableDifferentialPrivacy(false)
                .anonymizationLevel(AnonymizationLevel.MODERATE)
                .build();

        final Vital vital1 = Vital.builder().vitalType("HEART_RATE").value("73").build();
        final Vital vital2 = Vital.builder().vitalType("HEART_RATE").value("72").build();

        when(userRepository.findById(patientId)).thenReturn(Optional.of(new User()));
        when(anonymizer.generatePseudoId(patientId)).thenReturn("Patient_1234");
        when(vitalsRepository.findRecentByPatientId(eq(patientId), any(PageRequest.class)))
                .thenReturn(List.of(vital1, vital2));
        when(anonymizer.anonymizePatientContext(anyString(), eq(patientId), eq(AnonymizationLevel.MODERATE)))
                .thenAnswer(inv -> inv.getArgument(0));

        final String result = service.buildAnonymizedPatientContext(patientId, request, aiConfig);

        assertTrue(result.contains("stable"));
    }

    @Test
    @DisplayName("buildAnonymizedPatientContext - vitals with increasing trend - returns increasing")
    void buildAnonymizedPatientContext_vitalsWithIncreasingTrend_returnsIncreasing() throws Exception {
        final ChatRequest request = ChatRequest.builder()
                .includeVitals(true)
                .includeMedications(false)
                .includeNotes(false)
                .enableDifferentialPrivacy(false)
                .anonymizationLevel(AnonymizationLevel.MODERATE)
                .build();

        final Vital vital1 = Vital.builder().vitalType("HEART_RATE").value("90").build();
        final Vital vital2 = Vital.builder().vitalType("HEART_RATE").value("60").build();

        when(userRepository.findById(patientId)).thenReturn(Optional.of(new User()));
        when(anonymizer.generatePseudoId(patientId)).thenReturn("Patient_1234");
        when(vitalsRepository.findRecentByPatientId(eq(patientId), any(PageRequest.class)))
                .thenReturn(List.of(vital1, vital2));
        when(anonymizer.anonymizePatientContext(anyString(), eq(patientId), eq(AnonymizationLevel.MODERATE)))
                .thenAnswer(inv -> inv.getArgument(0));

        final String result = service.buildAnonymizedPatientContext(patientId, request, aiConfig);

        assertTrue(result.contains("increasing"));
    }

    @Test
    @DisplayName("buildAnonymizedPatientContext - notes enabled but empty - returns no clinical notes message")
    void buildAnonymizedPatientContext_notesEnabledButEmpty_returnsNoClinicalNotesMessage() throws Exception {
        final ChatRequest request = ChatRequest.builder()
                .includeVitals(false)
                .includeMedications(false)
                .includeNotes(true)
                .enableDifferentialPrivacy(false)
                .anonymizationLevel(AnonymizationLevel.MODERATE)
                .build();

        when(userRepository.findById(patientId)).thenReturn(Optional.of(new User()));
        when(anonymizer.generatePseudoId(patientId)).thenReturn("Patient_1234");
        when(clinicalNotesRepository.findRecentByPatientId(eq(patientId), any(PageRequest.class)))
                .thenReturn(Collections.emptyList());
        when(anonymizer.anonymizePatientContext(anyString(), eq(patientId), eq(AnonymizationLevel.MODERATE)))
                .thenAnswer(inv -> inv.getArgument(0));

        final String result = service.buildAnonymizedPatientContext(patientId, request, aiConfig);

        assertTrue(result.contains("Clinical Notes: No recent clinical notes available"));
    }

    @Test
    @DisplayName("buildAnonymizedPatientContext - medications enabled both flags true - includes medication classes")
    void buildAnonymizedPatientContext_medicationsEnabledBothFlagsTrue_includesMedicationClasses() throws Exception {
        final ChatRequest request = ChatRequest.builder()
                .includeVitals(false)
                .includeMedications(true)
                .includeNotes(false)
                .enableDifferentialPrivacy(false)
                .anonymizationLevel(AnonymizationLevel.MODERATE)
                .build();

        aiConfig.setIncludeMedicationsByDefault(true);

        when(userRepository.findById(patientId)).thenReturn(Optional.of(new User()));
        when(anonymizer.generatePseudoId(patientId)).thenReturn("Patient_1234");
        when(anonymizer.anonymizePatientContext(anyString(), eq(patientId), eq(AnonymizationLevel.MODERATE)))
                .thenAnswer(inv -> inv.getArgument(0));

        final String result = service.buildAnonymizedPatientContext(patientId, request, aiConfig);

        assertTrue(result.contains("Specific medications generalized to drug classes for privacy"));
    }

    @Test
    @DisplayName("contextContainsPHI - context with PHI - returns true")
    void contextContainsPHI_contextWithPHI_returnsTrue() throws Exception {
        when(anonymizer.containsPHI("John Smith is a patient")).thenReturn(true);

        final boolean result = service.contextContainsPHI("John Smith is a patient");

        assertTrue(result);
        verify(anonymizer).containsPHI("John Smith is a patient");
    }

    @Test
    @DisplayName("contextContainsPHI - context without PHI - returns false")
    void contextContainsPHI_contextWithoutPHI_returnsFalse() throws Exception {
        when(anonymizer.containsPHI("anonymized data")).thenReturn(false);

        final boolean result = service.contextContainsPHI("anonymized data");

        assertFalse(result);
        verify(anonymizer).containsPHI("anonymized data");
    }

    @Test
    @DisplayName("buildStatisticalContext - valid patient id - returns statistical summary with pseudo id")
    void buildStatisticalContext_validPatientId_returnsStatisticalSummaryWithPseudoId() throws Exception {
        when(anonymizer.generatePseudoId(patientId)).thenReturn("Patient_5678");

        final String result = service.buildStatisticalContext(patientId);

        assertNotNull(result);
        assertTrue(result.contains("Patient_5678"));
        assertTrue(result.contains("Statistical Patient Summary"));
        assertTrue(result.contains("MEDICAL DISCLAIMER"));
    }

    // --- AGGRESSIVE anonymization tests ---

    @Test
    @DisplayName("buildAnonymizedPatientContext - AGGRESSIVE with elevated blood pressure - returns elevated range")
    void buildAnonymizedPatientContext_aggressiveWithElevatedBP_returnsElevatedRange() throws Exception {
        final ChatRequest request = ChatRequest.builder()
                .includeVitals(true)
                .includeMedications(false)
                .includeNotes(false)
                .enableDifferentialPrivacy(false)
                .anonymizationLevel(AnonymizationLevel.AGGRESSIVE)
                .build();

        final Vital vital1 = Vital.builder().vitalType("BLOOD_PRESSURE").value("150/95").build();
        final Vital vital2 = Vital.builder().vitalType("BLOOD_PRESSURE").value("145/92").build();

        when(userRepository.findById(patientId)).thenReturn(Optional.of(new User()));
        when(anonymizer.generatePseudoId(patientId)).thenReturn("Patient_1234");
        when(vitalsRepository.findRecentByPatientId(eq(patientId), any(PageRequest.class)))
                .thenReturn(List.of(vital1, vital2));
        when(anonymizer.anonymizePatientContext(anyString(), eq(patientId), eq(AnonymizationLevel.AGGRESSIVE)))
                .thenAnswer(inv -> inv.getArgument(0));

        final String result = service.buildAnonymizedPatientContext(patientId, request, aiConfig);

        assertTrue(result.contains("elevated range"));
    }

    @Test
    @DisplayName("buildAnonymizedPatientContext - AGGRESSIVE with high-normal blood pressure - returns high-normal range")
    void buildAnonymizedPatientContext_aggressiveWithHighNormalBP_returnsHighNormalRange() throws Exception {
        final ChatRequest request = ChatRequest.builder()
                .includeVitals(true)
                .includeMedications(false)
                .includeNotes(false)
                .enableDifferentialPrivacy(false)
                .anonymizationLevel(AnonymizationLevel.AGGRESSIVE)
                .build();

        final Vital vital1 = Vital.builder().vitalType("BLOOD_PRESSURE").value("135/85").build();
        final Vital vital2 = Vital.builder().vitalType("BLOOD_PRESSURE").value("132/82").build();

        when(userRepository.findById(patientId)).thenReturn(Optional.of(new User()));
        when(anonymizer.generatePseudoId(patientId)).thenReturn("Patient_1234");
        when(vitalsRepository.findRecentByPatientId(eq(patientId), any(PageRequest.class)))
                .thenReturn(List.of(vital1, vital2));
        when(anonymizer.anonymizePatientContext(anyString(), eq(patientId), eq(AnonymizationLevel.AGGRESSIVE)))
                .thenAnswer(inv -> inv.getArgument(0));

        final String result = service.buildAnonymizedPatientContext(patientId, request, aiConfig);

        assertTrue(result.contains("high-normal range"));
    }

    @Test
    @DisplayName("buildAnonymizedPatientContext - AGGRESSIVE with normal blood pressure - returns normal range")
    void buildAnonymizedPatientContext_aggressiveWithNormalBP_returnsNormalRange() throws Exception {
        final ChatRequest request = ChatRequest.builder()
                .includeVitals(true)
                .includeMedications(false)
                .includeNotes(false)
                .enableDifferentialPrivacy(false)
                .anonymizationLevel(AnonymizationLevel.AGGRESSIVE)
                .build();

        final Vital vital1 = Vital.builder().vitalType("BLOOD_PRESSURE").value("120/75").build();
        final Vital vital2 = Vital.builder().vitalType("BLOOD_PRESSURE").value("118/72").build();

        when(userRepository.findById(patientId)).thenReturn(Optional.of(new User()));
        when(anonymizer.generatePseudoId(patientId)).thenReturn("Patient_1234");
        when(vitalsRepository.findRecentByPatientId(eq(patientId), any(PageRequest.class)))
                .thenReturn(List.of(vital1, vital2));
        when(anonymizer.anonymizePatientContext(anyString(), eq(patientId), eq(AnonymizationLevel.AGGRESSIVE)))
                .thenAnswer(inv -> inv.getArgument(0));

        final String result = service.buildAnonymizedPatientContext(patientId, request, aiConfig);

        assertTrue(result.contains("normal range"));
    }

    @Test
    @DisplayName("buildAnonymizedPatientContext - AGGRESSIVE with numeric vital - returns normal range category")
    void buildAnonymizedPatientContext_aggressiveWithNumericVital_returnsNormalRangeCategory() throws Exception {
        final ChatRequest request = ChatRequest.builder()
                .includeVitals(true)
                .includeMedications(false)
                .includeNotes(false)
                .enableDifferentialPrivacy(false)
                .anonymizationLevel(AnonymizationLevel.AGGRESSIVE)
                .build();

        final Vital vital1 = Vital.builder().vitalType("HEART_RATE").value("72").build();
        final Vital vital2 = Vital.builder().vitalType("HEART_RATE").value("75").build();

        when(userRepository.findById(patientId)).thenReturn(Optional.of(new User()));
        when(anonymizer.generatePseudoId(patientId)).thenReturn("Patient_1234");
        when(vitalsRepository.findRecentByPatientId(eq(patientId), any(PageRequest.class)))
                .thenReturn(List.of(vital1, vital2));
        when(anonymizer.anonymizePatientContext(anyString(), eq(patientId), eq(AnonymizationLevel.AGGRESSIVE)))
                .thenAnswer(inv -> inv.getArgument(0));

        final String result = service.buildAnonymizedPatientContext(patientId, request, aiConfig);

        assertTrue(result.contains("normal range"));
    }

    @Test
    @DisplayName("buildAnonymizedPatientContext - AGGRESSIVE with non-numeric vital - returns normal range fallback")
    void buildAnonymizedPatientContext_aggressiveWithNonNumericVital_returnsNormalRangeFallback() throws Exception {
        final ChatRequest request = ChatRequest.builder()
                .includeVitals(true)
                .includeMedications(false)
                .includeNotes(false)
                .enableDifferentialPrivacy(false)
                .anonymizationLevel(AnonymizationLevel.AGGRESSIVE)
                .build();

        final Vital vital1 = Vital.builder().vitalType("STATUS").value("active").build();
        final Vital vital2 = Vital.builder().vitalType("STATUS").value("resting").build();

        when(userRepository.findById(patientId)).thenReturn(Optional.of(new User()));
        when(anonymizer.generatePseudoId(patientId)).thenReturn("Patient_1234");
        when(vitalsRepository.findRecentByPatientId(eq(patientId), any(PageRequest.class)))
                .thenReturn(List.of(vital1, vital2));
        when(anonymizer.anonymizePatientContext(anyString(), eq(patientId), eq(AnonymizationLevel.AGGRESSIVE)))
                .thenAnswer(inv -> inv.getArgument(0));

        final String result = service.buildAnonymizedPatientContext(patientId, request, aiConfig);

        // NumberFormatException in anonymizeVitalValue caught, returns "normal range"
        assertTrue(result.contains("normal range"));
    }

    // --- analyzeTrend tests ---

    @Test
    @DisplayName("buildAnonymizedPatientContext - single vital reading - returns insufficient data trend")
    void buildAnonymizedPatientContext_singleVitalReading_returnsInsufficientDataTrend() throws Exception {
        final ChatRequest request = ChatRequest.builder()
                .includeVitals(true)
                .includeMedications(false)
                .includeNotes(false)
                .enableDifferentialPrivacy(false)
                .anonymizationLevel(AnonymizationLevel.MODERATE)
                .build();

        final Vital vital = Vital.builder().vitalType("HEART_RATE").value("72").build();

        when(userRepository.findById(patientId)).thenReturn(Optional.of(new User()));
        when(anonymizer.generatePseudoId(patientId)).thenReturn("Patient_1234");
        when(vitalsRepository.findRecentByPatientId(eq(patientId), any(PageRequest.class)))
                .thenReturn(List.of(vital));
        when(anonymizer.anonymizePatientContext(anyString(), eq(patientId), eq(AnonymizationLevel.MODERATE)))
                .thenAnswer(inv -> inv.getArgument(0));

        final String result = service.buildAnonymizedPatientContext(patientId, request, aiConfig);

        assertTrue(result.contains("insufficient data"));
    }

    @Test
    @DisplayName("buildAnonymizedPatientContext - blood pressure trend - returns stable")
    void buildAnonymizedPatientContext_bloodPressureTrend_returnsStable() throws Exception {
        final ChatRequest request = ChatRequest.builder()
                .includeVitals(true)
                .includeMedications(false)
                .includeNotes(false)
                .enableDifferentialPrivacy(false)
                .anonymizationLevel(AnonymizationLevel.MODERATE)
                .build();

        final Vital vital1 = Vital.builder().vitalType("BLOOD_PRESSURE").value("120/80").build();
        final Vital vital2 = Vital.builder().vitalType("BLOOD_PRESSURE").value("118/78").build();

        when(userRepository.findById(patientId)).thenReturn(Optional.of(new User()));
        when(anonymizer.generatePseudoId(patientId)).thenReturn("Patient_1234");
        when(vitalsRepository.findRecentByPatientId(eq(patientId), any(PageRequest.class)))
                .thenReturn(List.of(vital1, vital2));
        when(anonymizer.anonymizePatientContext(anyString(), eq(patientId), eq(AnonymizationLevel.MODERATE)))
                .thenAnswer(inv -> inv.getArgument(0));

        final String result = service.buildAnonymizedPatientContext(patientId, request, aiConfig);

        assertTrue(result.contains("stable"));
    }

    @Test
    @DisplayName("buildAnonymizedPatientContext - vitals with decreasing trend - returns decreasing")
    void buildAnonymizedPatientContext_vitalsWithDecreasingTrend_returnsDecreasing() throws Exception {
        final ChatRequest request = ChatRequest.builder()
                .includeVitals(true)
                .includeMedications(false)
                .includeNotes(false)
                .enableDifferentialPrivacy(false)
                .anonymizationLevel(AnonymizationLevel.MODERATE)
                .build();

        final Vital vital1 = Vital.builder().vitalType("HEART_RATE").value("60").build();
        final Vital vital2 = Vital.builder().vitalType("HEART_RATE").value("90").build();

        when(userRepository.findById(patientId)).thenReturn(Optional.of(new User()));
        when(anonymizer.generatePseudoId(patientId)).thenReturn("Patient_1234");
        when(vitalsRepository.findRecentByPatientId(eq(patientId), any(PageRequest.class)))
                .thenReturn(List.of(vital1, vital2));
        when(anonymizer.anonymizePatientContext(anyString(), eq(patientId), eq(AnonymizationLevel.MODERATE)))
                .thenAnswer(inv -> inv.getArgument(0));

        final String result = service.buildAnonymizedPatientContext(patientId, request, aiConfig);

        assertTrue(result.contains("decreasing"));
    }

    @Test
    @DisplayName("buildAnonymizedPatientContext - vitals with non-numeric values - analyzeTrend returns stable on exception")
    void buildAnonymizedPatientContext_vitalsNonNumericValues_analyzeTrendReturnsStableOnException() throws Exception {
        final ChatRequest request = ChatRequest.builder()
                .includeVitals(true)
                .includeMedications(false)
                .includeNotes(false)
                .enableDifferentialPrivacy(false)
                .anonymizationLevel(AnonymizationLevel.MODERATE)
                .build();

        final Vital vital1 = Vital.builder().vitalType("STATUS").value("active").build();
        final Vital vital2 = Vital.builder().vitalType("STATUS").value("resting").build();

        when(userRepository.findById(patientId)).thenReturn(Optional.of(new User()));
        when(anonymizer.generatePseudoId(patientId)).thenReturn("Patient_1234");
        when(vitalsRepository.findRecentByPatientId(eq(patientId), any(PageRequest.class)))
                .thenReturn(List.of(vital1, vital2));
        when(anonymizer.anonymizePatientContext(anyString(), eq(patientId), eq(AnonymizationLevel.MODERATE)))
                .thenAnswer(inv -> inv.getArgument(0));

        final String result = service.buildAnonymizedPatientContext(patientId, request, aiConfig);

        // Non-numeric values cause exception in analyzeTrend, caught and returns "stable"
        assertTrue(result.contains("stable"));
    }

    // --- Clinical notes coverage ---

    @Test
    @DisplayName("buildAnonymizedPatientContext - notes with STATISTICAL level - returns note count summary")
    void buildAnonymizedPatientContext_notesWithStatisticalLevel_returnsNoteCountSummary() throws Exception {
        final ChatRequest request = ChatRequest.builder()
                .includeVitals(false)
                .includeMedications(false)
                .includeNotes(true)
                .enableDifferentialPrivacy(false)
                .anonymizationLevel(AnonymizationLevel.STATISTICAL)
                .build();

        final ClinicalNote note = ClinicalNote.builder().noteType("ASSESSMENT").content("Patient is stable").build();

        when(userRepository.findById(patientId)).thenReturn(Optional.of(new User()));
        when(anonymizer.generatePseudoId(patientId)).thenReturn("Patient_1234");
        when(clinicalNotesRepository.findRecentByPatientId(eq(patientId), any(PageRequest.class)))
                .thenReturn(List.of(note));
        when(anonymizer.anonymizePatientContext(anyString(), eq(patientId), eq(AnonymizationLevel.STATISTICAL)))
                .thenAnswer(inv -> inv.getArgument(0));

        final String result = service.buildAnonymizedPatientContext(patientId, request, aiConfig);

        assertTrue(result.contains("1 recent clinical notes available"));
        assertTrue(result.contains("Statistical summary only"));
    }

    @Test
    @DisplayName("buildAnonymizedPatientContext - notes with long content - truncates to 100 chars")
    void buildAnonymizedPatientContext_notesWithLongContent_truncatesTo100Chars() throws Exception {
        final ChatRequest request = ChatRequest.builder()
                .includeVitals(false)
                .includeMedications(false)
                .includeNotes(true)
                .enableDifferentialPrivacy(false)
                .anonymizationLevel(AnonymizationLevel.MODERATE)
                .build();

        final String longContent = "A".repeat(200);
        final ClinicalNote note = ClinicalNote.builder().noteType("ASSESSMENT").content(longContent).build();

        when(userRepository.findById(patientId)).thenReturn(Optional.of(new User()));
        when(anonymizer.generatePseudoId(patientId)).thenReturn("Patient_1234");
        when(clinicalNotesRepository.findRecentByPatientId(eq(patientId), any(PageRequest.class)))
                .thenReturn(List.of(note));
        when(anonymizer.anonymizePatientContext(anyString(), eq(patientId), any(AnonymizationLevel.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        final String result = service.buildAnonymizedPatientContext(patientId, request, aiConfig);

        // Content should be truncated with "..."
        assertTrue(result.contains("..."));
    }

    @Test
    @DisplayName("buildAnonymizedPatientContext - notes with null content - handles null gracefully")
    void buildAnonymizedPatientContext_notesWithNullContent_handlesNullGracefully() throws Exception {
        final ChatRequest request = ChatRequest.builder()
                .includeVitals(false)
                .includeMedications(false)
                .includeNotes(true)
                .enableDifferentialPrivacy(false)
                .anonymizationLevel(AnonymizationLevel.MODERATE)
                .build();

        final ClinicalNote note = ClinicalNote.builder().noteType("ASSESSMENT").content(null).build();

        when(userRepository.findById(patientId)).thenReturn(Optional.of(new User()));
        when(anonymizer.generatePseudoId(patientId)).thenReturn("Patient_1234");
        when(clinicalNotesRepository.findRecentByPatientId(eq(patientId), any(PageRequest.class)))
                .thenReturn(List.of(note));
        // Use any() to match both null (inner call) and non-null (outer final pass)
        when(anonymizer.anonymizePatientContext(any(), eq(patientId), eq(AnonymizationLevel.MODERATE)))
                .thenAnswer(inv -> inv.getArgument(0));

        final String result = service.buildAnonymizedPatientContext(patientId, request, aiConfig);

        // truncateContent handles null by returning null, which becomes "null" in String.format
        assertNotNull(result);
        assertTrue(result.contains("ASSESSMENT"));
    }

    // --- Missing flag combination tests ---

    @Test
    @DisplayName("buildAnonymizedPatientContext - include notes true but aiConfig false - skips notes")
    void buildAnonymizedPatientContext_includeNotesTrueAiConfigFalse_skipsNotes() throws Exception {
        final ChatRequest request = ChatRequest.builder()
                .includeVitals(false)
                .includeMedications(false)
                .includeNotes(true)
                .enableDifferentialPrivacy(false)
                .anonymizationLevel(AnonymizationLevel.MODERATE)
                .build();

        aiConfig.setIncludeNotesByDefault(false);

        when(userRepository.findById(patientId)).thenReturn(Optional.of(new User()));
        when(anonymizer.generatePseudoId(patientId)).thenReturn("Patient_1234");
        when(anonymizer.anonymizePatientContext(anyString(), eq(patientId), eq(AnonymizationLevel.MODERATE)))
                .thenAnswer(inv -> inv.getArgument(0));

        final String result = service.buildAnonymizedPatientContext(patientId, request, aiConfig);

        assertNotNull(result);
        verify(clinicalNotesRepository, never()).findRecentByPatientId(anyLong(), any());
    }

    @Test
    @DisplayName("buildAnonymizedPatientContext - include medications true but aiConfig false - skips medications")
    void buildAnonymizedPatientContext_includeMedicationsTrueAiConfigFalse_skipsMedications() throws Exception {
        final ChatRequest request = ChatRequest.builder()
                .includeVitals(false)
                .includeMedications(true)
                .includeNotes(false)
                .enableDifferentialPrivacy(false)
                .anonymizationLevel(AnonymizationLevel.MODERATE)
                .build();

        aiConfig.setIncludeMedicationsByDefault(false);

        when(userRepository.findById(patientId)).thenReturn(Optional.of(new User()));
        when(anonymizer.generatePseudoId(patientId)).thenReturn("Patient_1234");
        when(anonymizer.anonymizePatientContext(anyString(), eq(patientId), eq(AnonymizationLevel.MODERATE)))
                .thenAnswer(inv -> inv.getArgument(0));

        final String result = service.buildAnonymizedPatientContext(patientId, request, aiConfig);

        assertNotNull(result);
        assertFalse(result.contains("Specific medications generalized to drug classes for privacy"));
    }

    @Test
    @DisplayName("buildAnonymizedPatientContext - AGGRESSIVE with elevated BP via diastolic only - returns elevated range")
    void buildAnonymizedPatientContext_aggressiveElevatedBPViaDiastolicOnly_returnsElevatedRange() throws Exception {
        final ChatRequest request = ChatRequest.builder()
                .includeVitals(true)
                .includeMedications(false)
                .includeNotes(false)
                .enableDifferentialPrivacy(false)
                .anonymizationLevel(AnonymizationLevel.AGGRESSIVE)
                .build();

        // systolic < 140 but diastolic >= 90
        final Vital vital1 = Vital.builder().vitalType("BLOOD_PRESSURE").value("135/95").build();
        final Vital vital2 = Vital.builder().vitalType("BLOOD_PRESSURE").value("130/92").build();

        when(userRepository.findById(patientId)).thenReturn(Optional.of(new User()));
        when(anonymizer.generatePseudoId(patientId)).thenReturn("Patient_1234");
        when(vitalsRepository.findRecentByPatientId(eq(patientId), any(PageRequest.class)))
                .thenReturn(List.of(vital1, vital2));
        when(anonymizer.anonymizePatientContext(anyString(), eq(patientId), eq(AnonymizationLevel.AGGRESSIVE)))
                .thenAnswer(inv -> inv.getArgument(0));

        final String result = service.buildAnonymizedPatientContext(patientId, request, aiConfig);

        assertTrue(result.contains("elevated range"));
    }

    @Test
    @DisplayName("buildAnonymizedPatientContext - AGGRESSIVE with high-normal BP via diastolic only - returns high-normal range")
    void buildAnonymizedPatientContext_aggressiveHighNormalBPViaDiastolicOnly_returnsHighNormalRange() throws Exception {
        final ChatRequest request = ChatRequest.builder()
                .includeVitals(true)
                .includeMedications(false)
                .includeNotes(false)
                .enableDifferentialPrivacy(false)
                .anonymizationLevel(AnonymizationLevel.AGGRESSIVE)
                .build();

        // systolic < 130 but diastolic >= 80
        final Vital vital1 = Vital.builder().vitalType("BLOOD_PRESSURE").value("125/85").build();
        final Vital vital2 = Vital.builder().vitalType("BLOOD_PRESSURE").value("122/82").build();

        when(userRepository.findById(patientId)).thenReturn(Optional.of(new User()));
        when(anonymizer.generatePseudoId(patientId)).thenReturn("Patient_1234");
        when(vitalsRepository.findRecentByPatientId(eq(patientId), any(PageRequest.class)))
                .thenReturn(List.of(vital1, vital2));
        when(anonymizer.anonymizePatientContext(anyString(), eq(patientId), eq(AnonymizationLevel.AGGRESSIVE)))
                .thenAnswer(inv -> inv.getArgument(0));

        final String result = service.buildAnonymizedPatientContext(patientId, request, aiConfig);

        assertTrue(result.contains("high-normal range"));
    }
}
