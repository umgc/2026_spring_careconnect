package com.careconnect.service;

import com.careconnect.dto.VitalSampleDTO;
import com.careconnect.model.Patient;
import com.careconnect.model.VitalSample;
import com.careconnect.repository.PatientRepository;
import com.careconnect.repository.VitalSampleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.time.Period;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class VitalSampleServiceTest {

    @Mock
    private VitalSampleRepository vitalSampleRepository;

    @Mock
    private PatientRepository patientRepository;

    @InjectMocks
    private VitalSampleService vitalSampleService;

    private Patient patient;
    private VitalSample vitalSample;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        patient = Patient.builder().id(1L).firstName("John").lastName("Doe").build();
        vitalSample = VitalSample.builder()
                .id(10L)
                .patient(patient)
                .timestamp(Instant.now())
                .heartRate(72.0)
                .spo2(98.0)
                .systolic(120)
                .diastolic(80)
                .weight(75.0)
                .moodValue(7)
                .painValue(2)
                .build();
    }

    // ── createVitalSample ──

    @Test
    @DisplayName("createVitalSample_validDtoWithTimestamp_returnsMappedDto")
    void createVitalSample_validDtoWithTimestamp_returnsMappedDto() throws Exception {
        final Instant ts = Instant.now();
        final VitalSampleDTO dto = VitalSampleDTO.builder()
                .patientId(1L)
                .timestamp(ts)
                .heartRate(72.0)
                .spo2(98.0)
                .systolic(120)
                .diastolic(80)
                .weight(75.0)
                .moodValue(7)
                .painValue(2)
                .build();

        when(patientRepository.findById(1L)).thenReturn(Optional.of(patient));
        when(vitalSampleRepository.save(any(VitalSample.class))).thenReturn(vitalSample);

        final VitalSampleDTO result = vitalSampleService.createVitalSample(dto);

        assertNotNull(result);
        assertEquals(10L, result.id());
        assertEquals(1L, result.patientId());
        verify(vitalSampleRepository).save(any(VitalSample.class));
    }

    @Test
    @DisplayName("createVitalSample_nullTimestamp_usesInstantNow")
    void createVitalSample_nullTimestamp_usesInstantNow() throws Exception {
        final VitalSampleDTO dto = VitalSampleDTO.builder()
                .patientId(1L)
                .timestamp(null)
                .heartRate(80.0)
                .build();

        when(patientRepository.findById(1L)).thenReturn(Optional.of(patient));
        when(vitalSampleRepository.save(any(VitalSample.class))).thenReturn(vitalSample);

        final VitalSampleDTO result = vitalSampleService.createVitalSample(dto);

        assertNotNull(result);
        verify(vitalSampleRepository).save(any(VitalSample.class));
    }

    @Test
    @DisplayName("createVitalSample_patientNotFound_throwsIllegalArgument")
    void createVitalSample_patientNotFound_throwsIllegalArgument() throws Exception {
        final VitalSampleDTO dto = VitalSampleDTO.builder().patientId(999L).build();
        when(patientRepository.findById(999L)).thenReturn(Optional.empty());

        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> vitalSampleService.createVitalSample(dto));
        assertTrue(ex.getMessage().contains("Patient not found"));
    }

    @Test
    @DisplayName("createVitalSample_highHeartRate_triggersAlert")
    void createVitalSample_highHeartRate_triggersAlert() throws Exception {
        final VitalSample highHr = VitalSample.builder()
                .id(11L).patient(patient).timestamp(Instant.now())
                .heartRate(110.0).build();

        final VitalSampleDTO dto = VitalSampleDTO.builder()
                .patientId(1L).timestamp(Instant.now()).heartRate(110.0).build();

        when(patientRepository.findById(1L)).thenReturn(Optional.of(patient));
        when(vitalSampleRepository.save(any(VitalSample.class))).thenReturn(highHr);

        final VitalSampleDTO result = vitalSampleService.createVitalSample(dto);
        assertNotNull(result);
    }

    @Test
    @DisplayName("createVitalSample_lowHeartRate_triggersLowAlert")
    void createVitalSample_lowHeartRate_triggersLowAlert() throws Exception {
        final VitalSample lowHr = VitalSample.builder()
                .id(12L).patient(patient).timestamp(Instant.now())
                .heartRate(50.0).build();

        final VitalSampleDTO dto = VitalSampleDTO.builder()
                .patientId(1L).timestamp(Instant.now()).heartRate(50.0).build();

        when(patientRepository.findById(1L)).thenReturn(Optional.of(patient));
        when(vitalSampleRepository.save(any(VitalSample.class))).thenReturn(lowHr);

        final VitalSampleDTO result = vitalSampleService.createVitalSample(dto);
        assertNotNull(result);
    }

    @Test
    @DisplayName("createVitalSample_criticalSpo2_triggersCriticalAlert")
    void createVitalSample_criticalSpo2_triggersCriticalAlert() throws Exception {
        final VitalSample lowSpo2 = VitalSample.builder()
                .id(13L).patient(patient).timestamp(Instant.now())
                .spo2(85.0).build();

        final VitalSampleDTO dto = VitalSampleDTO.builder()
                .patientId(1L).timestamp(Instant.now()).spo2(85.0).build();

        when(patientRepository.findById(1L)).thenReturn(Optional.of(patient));
        when(vitalSampleRepository.save(any(VitalSample.class))).thenReturn(lowSpo2);

        final VitalSampleDTO result = vitalSampleService.createVitalSample(dto);
        assertNotNull(result);
    }

    @Test
    @DisplayName("createVitalSample_highSpo2Alert_triggersHighAlert")
    void createVitalSample_highSpo2Alert_triggersHighAlert() throws Exception {
        final VitalSample medSpo2 = VitalSample.builder()
                .id(14L).patient(patient).timestamp(Instant.now())
                .spo2(93.0).build();

        final VitalSampleDTO dto = VitalSampleDTO.builder()
                .patientId(1L).timestamp(Instant.now()).spo2(93.0).build();

        when(patientRepository.findById(1L)).thenReturn(Optional.of(patient));
        when(vitalSampleRepository.save(any(VitalSample.class))).thenReturn(medSpo2);

        final VitalSampleDTO result = vitalSampleService.createVitalSample(dto);
        assertNotNull(result);
    }

    @Test
    @DisplayName("createVitalSample_normalSpo2_noAlert")
    void createVitalSample_normalSpo2_noAlert() throws Exception {
        final VitalSample normalSpo2 = VitalSample.builder()
                .id(15L).patient(patient).timestamp(Instant.now())
                .spo2(97.0).build();

        final VitalSampleDTO dto = VitalSampleDTO.builder()
                .patientId(1L).timestamp(Instant.now()).spo2(97.0).build();

        when(patientRepository.findById(1L)).thenReturn(Optional.of(patient));
        when(vitalSampleRepository.save(any(VitalSample.class))).thenReturn(normalSpo2);

        final VitalSampleDTO result = vitalSampleService.createVitalSample(dto);
        assertNotNull(result);
    }

    @Test
    @DisplayName("createVitalSample_criticalBP_triggersCriticalBPAlert")
    void createVitalSample_criticalBP_triggersCriticalBPAlert() throws Exception {
        final VitalSample critBp = VitalSample.builder()
                .id(16L).patient(patient).timestamp(Instant.now())
                .systolic(190).diastolic(115).build();

        final VitalSampleDTO dto = VitalSampleDTO.builder()
                .patientId(1L).timestamp(Instant.now()).systolic(190).diastolic(115).build();

        when(patientRepository.findById(1L)).thenReturn(Optional.of(patient));
        when(vitalSampleRepository.save(any(VitalSample.class))).thenReturn(critBp);

        final VitalSampleDTO result = vitalSampleService.createVitalSample(dto);
        assertNotNull(result);
    }

    @Test
    @DisplayName("createVitalSample_highBP_triggersHighBPAlert")
    void createVitalSample_highBP_triggersHighBPAlert() throws Exception {
        final VitalSample highBp = VitalSample.builder()
                .id(17L).patient(patient).timestamp(Instant.now())
                .systolic(150).diastolic(95).build();

        final VitalSampleDTO dto = VitalSampleDTO.builder()
                .patientId(1L).timestamp(Instant.now()).systolic(150).diastolic(95).build();

        when(patientRepository.findById(1L)).thenReturn(Optional.of(patient));
        when(vitalSampleRepository.save(any(VitalSample.class))).thenReturn(highBp);

        final VitalSampleDTO result = vitalSampleService.createVitalSample(dto);
        assertNotNull(result);
    }

    @Test
    @DisplayName("createVitalSample_lowBP_triggersLowBPAlert")
    void createVitalSample_lowBP_triggersLowBPAlert() throws Exception {
        final VitalSample lowBp = VitalSample.builder()
                .id(18L).patient(patient).timestamp(Instant.now())
                .systolic(85).diastolic(55).build();

        final VitalSampleDTO dto = VitalSampleDTO.builder()
                .patientId(1L).timestamp(Instant.now()).systolic(85).diastolic(55).build();

        when(patientRepository.findById(1L)).thenReturn(Optional.of(patient));
        when(vitalSampleRepository.save(any(VitalSample.class))).thenReturn(lowBp);

        final VitalSampleDTO result = vitalSampleService.createVitalSample(dto);
        assertNotNull(result);
    }

    @Test
    @DisplayName("createVitalSample_normalBP_noAlert")
    void createVitalSample_normalBP_noAlert() throws Exception {
        final VitalSample normalBp = VitalSample.builder()
                .id(19L).patient(patient).timestamp(Instant.now())
                .systolic(120).diastolic(80).build();

        final VitalSampleDTO dto = VitalSampleDTO.builder()
                .patientId(1L).timestamp(Instant.now()).systolic(120).diastolic(80).build();

        when(patientRepository.findById(1L)).thenReturn(Optional.of(patient));
        when(vitalSampleRepository.save(any(VitalSample.class))).thenReturn(normalBp);

        final VitalSampleDTO result = vitalSampleService.createVitalSample(dto);
        assertNotNull(result);
    }

    @Test
    @DisplayName("createVitalSample_lowMood_triggersHighMoodAlert")
    void createVitalSample_lowMood_triggersHighMoodAlert() throws Exception {
        final VitalSample lowMood = VitalSample.builder()
                .id(20L).patient(patient).timestamp(Instant.now())
                .moodValue(1).build();

        final VitalSampleDTO dto = VitalSampleDTO.builder()
                .patientId(1L).timestamp(Instant.now()).moodValue(1).build();

        when(patientRepository.findById(1L)).thenReturn(Optional.of(patient));
        when(vitalSampleRepository.save(any(VitalSample.class))).thenReturn(lowMood);

        final VitalSampleDTO result = vitalSampleService.createVitalSample(dto);
        assertNotNull(result);
    }

    @Test
    @DisplayName("createVitalSample_highPain_triggersHighPainAlert")
    void createVitalSample_highPain_triggersHighPainAlert() throws Exception {
        final VitalSample highPain = VitalSample.builder()
                .id(21L).patient(patient).timestamp(Instant.now())
                .painValue(9).build();

        final VitalSampleDTO dto = VitalSampleDTO.builder()
                .patientId(1L).timestamp(Instant.now()).painValue(9).build();

        when(patientRepository.findById(1L)).thenReturn(Optional.of(patient));
        when(vitalSampleRepository.save(any(VitalSample.class))).thenReturn(highPain);

        final VitalSampleDTO result = vitalSampleService.createVitalSample(dto);
        assertNotNull(result);
    }

    @Test
    @DisplayName("createVitalSample_nullSystolicWithDiastolic_handlesBPBranch")
    void createVitalSample_nullSystolicWithDiastolic_handlesBPBranch() throws Exception {
        final VitalSample sample = VitalSample.builder()
                .id(22L).patient(patient).timestamp(Instant.now())
                .systolic(null).diastolic(120).build();

        final VitalSampleDTO dto = VitalSampleDTO.builder()
                .patientId(1L).timestamp(Instant.now()).diastolic(120).build();

        when(patientRepository.findById(1L)).thenReturn(Optional.of(patient));
        when(vitalSampleRepository.save(any(VitalSample.class))).thenReturn(sample);

        final VitalSampleDTO result = vitalSampleService.createVitalSample(dto);
        assertNotNull(result);
    }

    @Test
    @DisplayName("createVitalSample_systolicWithNullDiastolic_handlesBPBranch")
    void createVitalSample_systolicWithNullDiastolic_handlesBPBranch() throws Exception {
        final VitalSample sample = VitalSample.builder()
                .id(23L).patient(patient).timestamp(Instant.now())
                .systolic(200).diastolic(null).build();

        final VitalSampleDTO dto = VitalSampleDTO.builder()
                .patientId(1L).timestamp(Instant.now()).systolic(200).build();

        when(patientRepository.findById(1L)).thenReturn(Optional.of(patient));
        when(vitalSampleRepository.save(any(VitalSample.class))).thenReturn(sample);

        final VitalSampleDTO result = vitalSampleService.createVitalSample(dto);
        assertNotNull(result);
    }

    @Test
    @DisplayName("createVitalSample_normalHeartRate_noAlert")
    void createVitalSample_normalHeartRate_noAlert() throws Exception {
        final VitalSample normalHr = VitalSample.builder()
                .id(24L).patient(patient).timestamp(Instant.now())
                .heartRate(75.0).build();

        final VitalSampleDTO dto = VitalSampleDTO.builder()
                .patientId(1L).timestamp(Instant.now()).heartRate(75.0).build();

        when(patientRepository.findById(1L)).thenReturn(Optional.of(patient));
        when(vitalSampleRepository.save(any(VitalSample.class))).thenReturn(normalHr);

        final VitalSampleDTO result = vitalSampleService.createVitalSample(dto);
        assertNotNull(result);
    }

    @Test
    @DisplayName("createVitalSample_moodValueOf3_noAlert")
    void createVitalSample_moodValueOf3_noAlert() throws Exception {
        final VitalSample sample = VitalSample.builder()
                .id(25L).patient(patient).timestamp(Instant.now())
                .moodValue(3).build();

        final VitalSampleDTO dto = VitalSampleDTO.builder()
                .patientId(1L).timestamp(Instant.now()).moodValue(3).build();

        when(patientRepository.findById(1L)).thenReturn(Optional.of(patient));
        when(vitalSampleRepository.save(any(VitalSample.class))).thenReturn(sample);

        final VitalSampleDTO result = vitalSampleService.createVitalSample(dto);
        assertNotNull(result);
    }

    @Test
    @DisplayName("createVitalSample_painValueOf7_noAlert")
    void createVitalSample_painValueOf7_noAlert() throws Exception {
        final VitalSample sample = VitalSample.builder()
                .id(26L).patient(patient).timestamp(Instant.now())
                .painValue(7).build();

        final VitalSampleDTO dto = VitalSampleDTO.builder()
                .patientId(1L).timestamp(Instant.now()).painValue(7).build();

        when(patientRepository.findById(1L)).thenReturn(Optional.of(patient));
        when(vitalSampleRepository.save(any(VitalSample.class))).thenReturn(sample);

        final VitalSampleDTO result = vitalSampleService.createVitalSample(dto);
        assertNotNull(result);
    }

    @Test
    @DisplayName("createVitalSample_allVitalsNull_noAlerts")
    void createVitalSample_allVitalsNull_noAlerts() throws Exception {
        final VitalSample nullSample = VitalSample.builder()
                .id(27L).patient(patient).timestamp(Instant.now()).build();

        final VitalSampleDTO dto = VitalSampleDTO.builder()
                .patientId(1L).timestamp(Instant.now()).build();

        when(patientRepository.findById(1L)).thenReturn(Optional.of(patient));
        when(vitalSampleRepository.save(any(VitalSample.class))).thenReturn(nullSample);

        final VitalSampleDTO result = vitalSampleService.createVitalSample(dto);
        assertNotNull(result);
    }

    // ── updateVitalSample ──

    @Test
    @DisplayName("updateVitalSample_allFieldsProvided_updatesAll")
    void updateVitalSample_allFieldsProvided_updatesAll() throws Exception {
        final Instant newTs = Instant.now();
        final VitalSampleDTO dto = VitalSampleDTO.builder()
                .timestamp(newTs)
                .heartRate(80.0)
                .spo2(96.0)
                .systolic(130)
                .diastolic(85)
                .weight(80.0)
                .moodValue(8)
                .painValue(3)
                .build();

        when(vitalSampleRepository.findById(10L)).thenReturn(Optional.of(vitalSample));
        when(vitalSampleRepository.save(any(VitalSample.class))).thenReturn(vitalSample);

        final VitalSampleDTO result = vitalSampleService.updateVitalSample(10L, dto);

        assertNotNull(result);
        verify(vitalSampleRepository).save(any(VitalSample.class));
    }

    @Test
    @DisplayName("updateVitalSample_allFieldsNull_updatesNothing")
    void updateVitalSample_allFieldsNull_updatesNothing() throws Exception {
        final VitalSampleDTO dto = VitalSampleDTO.builder().build();

        when(vitalSampleRepository.findById(10L)).thenReturn(Optional.of(vitalSample));
        when(vitalSampleRepository.save(any(VitalSample.class))).thenReturn(vitalSample);

        final VitalSampleDTO result = vitalSampleService.updateVitalSample(10L, dto);

        assertNotNull(result);
        verify(vitalSampleRepository).save(any(VitalSample.class));
    }

    @Test
    @DisplayName("updateVitalSample_notFound_throwsIllegalArgument")
    void updateVitalSample_notFound_throwsIllegalArgument() throws Exception {
        final VitalSampleDTO dto = VitalSampleDTO.builder().build();
        when(vitalSampleRepository.findById(999L)).thenReturn(Optional.empty());

        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> vitalSampleService.updateVitalSample(999L, dto));
        assertTrue(ex.getMessage().contains("VitalSample not found"));
    }

    // ── getVitalSamples ──

    @Test
    @DisplayName("getVitalSamples_validPatientAndPeriod_returnsList")
    void getVitalSamples_validPatientAndPeriod_returnsList() throws Exception {
        final Period period = Period.ofDays(7);
        when(patientRepository.findById(1L)).thenReturn(Optional.of(patient));
        when(vitalSampleRepository.findByPatientAndTimestampBetweenOrderByTimestampDesc(
                eq(patient), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(vitalSample));

        final List<VitalSampleDTO> results = vitalSampleService.getVitalSamples(1L, period);

        assertEquals(1, results.size());
        assertEquals(10L, results.get(0).id());
    }

    @Test
    @DisplayName("getVitalSamples_patientNotFound_throwsIllegalArgument")
    void getVitalSamples_patientNotFound_throwsIllegalArgument() throws Exception {
        when(patientRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> vitalSampleService.getVitalSamples(999L, Period.ofDays(7)));
    }

    // ── getVitalSample ──

    @Test
    @DisplayName("getVitalSample_exists_returnsOptionalWithDto")
    void getVitalSample_exists_returnsOptionalWithDto() throws Exception {
        when(vitalSampleRepository.findById(10L)).thenReturn(Optional.of(vitalSample));

        final Optional<VitalSampleDTO> result = vitalSampleService.getVitalSample(10L);

        assertTrue(result.isPresent());
        assertEquals(10L, result.get().id());
    }

    @Test
    @DisplayName("getVitalSample_notExists_returnsEmpty")
    void getVitalSample_notExists_returnsEmpty() throws Exception {
        when(vitalSampleRepository.findById(999L)).thenReturn(Optional.empty());

        final Optional<VitalSampleDTO> result = vitalSampleService.getVitalSample(999L);

        assertTrue(result.isEmpty());
    }

    // ── deleteVitalSample ──

    @Test
    @DisplayName("deleteVitalSample_exists_deletesSuccessfully")
    void deleteVitalSample_exists_deletesSuccessfully() throws Exception {
        when(vitalSampleRepository.existsById(10L)).thenReturn(true);

        vitalSampleService.deleteVitalSample(10L);

        verify(vitalSampleRepository).deleteById(10L);
    }

    @Test
    @DisplayName("deleteVitalSample_notExists_throwsIllegalArgument")
    void deleteVitalSample_notExists_throwsIllegalArgument() throws Exception {
        when(vitalSampleRepository.existsById(999L)).thenReturn(false);

        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> vitalSampleService.deleteVitalSample(999L));
        assertTrue(ex.getMessage().contains("VitalSample not found"));
    }

    // ── getLatestVitalSample ──

    @Test
    @DisplayName("getLatestVitalSample_exists_returnsDto")
    void getLatestVitalSample_exists_returnsDto() throws Exception {
        when(patientRepository.findById(1L)).thenReturn(Optional.of(patient));
        when(vitalSampleRepository.findFirstByPatientOrderByTimestampDesc(patient))
                .thenReturn(Optional.of(vitalSample));

        final Optional<VitalSampleDTO> result = vitalSampleService.getLatestVitalSample(1L);

        assertTrue(result.isPresent());
        assertEquals(10L, result.get().id());
    }

    @Test
    @DisplayName("getLatestVitalSample_noSamples_returnsEmpty")
    void getLatestVitalSample_noSamples_returnsEmpty() throws Exception {
        when(patientRepository.findById(1L)).thenReturn(Optional.of(patient));
        when(vitalSampleRepository.findFirstByPatientOrderByTimestampDesc(patient))
                .thenReturn(Optional.empty());

        final Optional<VitalSampleDTO> result = vitalSampleService.getLatestVitalSample(1L);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("getLatestVitalSample_patientNotFound_throwsIllegalArgument")
    void getLatestVitalSample_patientNotFound_throwsIllegalArgument() throws Exception {
        when(patientRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> vitalSampleService.getLatestVitalSample(999L));
    }

    // ── determineBPAlert edge cases ──

    @Test
    @DisplayName("createVitalSample_criticalDiastolicOnly_triggersCritical")
    void createVitalSample_criticalDiastolicOnly_triggersCritical() throws Exception {
        final VitalSample sample = VitalSample.builder()
                .id(30L).patient(patient).timestamp(Instant.now())
                .systolic(null).diastolic(115).build();

        final VitalSampleDTO dto = VitalSampleDTO.builder()
                .patientId(1L).timestamp(Instant.now()).diastolic(115).build();

        when(patientRepository.findById(1L)).thenReturn(Optional.of(patient));
        when(vitalSampleRepository.save(any(VitalSample.class))).thenReturn(sample);

        final VitalSampleDTO result = vitalSampleService.createVitalSample(dto);
        assertNotNull(result);
    }

    @Test
    @DisplayName("createVitalSample_highDiastolicOnly_triggersHigh")
    void createVitalSample_highDiastolicOnly_triggersHigh() throws Exception {
        final VitalSample sample = VitalSample.builder()
                .id(31L).patient(patient).timestamp(Instant.now())
                .systolic(null).diastolic(95).build();

        final VitalSampleDTO dto = VitalSampleDTO.builder()
                .patientId(1L).timestamp(Instant.now()).diastolic(95).build();

        when(patientRepository.findById(1L)).thenReturn(Optional.of(patient));
        when(vitalSampleRepository.save(any(VitalSample.class))).thenReturn(sample);

        final VitalSampleDTO result = vitalSampleService.createVitalSample(dto);
        assertNotNull(result);
    }

    @Test
    @DisplayName("createVitalSample_lowDiastolicOnly_triggersLow")
    void createVitalSample_lowDiastolicOnly_triggersLow() throws Exception {
        final VitalSample sample = VitalSample.builder()
                .id(32L).patient(patient).timestamp(Instant.now())
                .systolic(null).diastolic(55).build();

        final VitalSampleDTO dto = VitalSampleDTO.builder()
                .patientId(1L).timestamp(Instant.now()).diastolic(55).build();

        when(patientRepository.findById(1L)).thenReturn(Optional.of(patient));
        when(vitalSampleRepository.save(any(VitalSample.class))).thenReturn(sample);

        final VitalSampleDTO result = vitalSampleService.createVitalSample(dto);
        assertNotNull(result);
    }

    @Test
    @DisplayName("createVitalSample_moodValueExactly2_triggersAlert")
    void createVitalSample_moodValueExactly2_triggersAlert() throws Exception {
        final VitalSample sample = VitalSample.builder()
                .id(33L).patient(patient).timestamp(Instant.now())
                .moodValue(2).build();

        final VitalSampleDTO dto = VitalSampleDTO.builder()
                .patientId(1L).timestamp(Instant.now()).moodValue(2).build();

        when(patientRepository.findById(1L)).thenReturn(Optional.of(patient));
        when(vitalSampleRepository.save(any(VitalSample.class))).thenReturn(sample);

        final VitalSampleDTO result = vitalSampleService.createVitalSample(dto);
        assertNotNull(result);
    }

    @Test
    @DisplayName("createVitalSample_painValueExactly8_triggersAlert")
    void createVitalSample_painValueExactly8_triggersAlert() throws Exception {
        final VitalSample sample = VitalSample.builder()
                .id(34L).patient(patient).timestamp(Instant.now())
                .painValue(8).build();

        final VitalSampleDTO dto = VitalSampleDTO.builder()
                .patientId(1L).timestamp(Instant.now()).painValue(8).build();

        when(patientRepository.findById(1L)).thenReturn(Optional.of(patient));
        when(vitalSampleRepository.save(any(VitalSample.class))).thenReturn(sample);

        final VitalSampleDTO result = vitalSampleService.createVitalSample(dto);
        assertNotNull(result);
    }

    @Test
    @DisplayName("createVitalSample_heartRateExactly60_normal")
    void createVitalSample_heartRateExactly60_normal() throws Exception {
        final VitalSample sample = VitalSample.builder()
                .id(35L).patient(patient).timestamp(Instant.now())
                .heartRate(60.0).build();

        final VitalSampleDTO dto = VitalSampleDTO.builder()
                .patientId(1L).timestamp(Instant.now()).heartRate(60.0).build();

        when(patientRepository.findById(1L)).thenReturn(Optional.of(patient));
        when(vitalSampleRepository.save(any(VitalSample.class))).thenReturn(sample);

        final VitalSampleDTO result = vitalSampleService.createVitalSample(dto);
        assertNotNull(result);
    }

    @Test
    @DisplayName("createVitalSample_heartRateExactly100_normal")
    void createVitalSample_heartRateExactly100_normal() throws Exception {
        final VitalSample sample = VitalSample.builder()
                .id(36L).patient(patient).timestamp(Instant.now())
                .heartRate(100.0).build();

        final VitalSampleDTO dto = VitalSampleDTO.builder()
                .patientId(1L).timestamp(Instant.now()).heartRate(100.0).build();

        when(patientRepository.findById(1L)).thenReturn(Optional.of(patient));
        when(vitalSampleRepository.save(any(VitalSample.class))).thenReturn(sample);

        final VitalSampleDTO result = vitalSampleService.createVitalSample(dto);
        assertNotNull(result);
    }

    @Test
    @DisplayName("createVitalSample_spo2Exactly95_normal")
    void createVitalSample_spo2Exactly95_normal() throws Exception {
        final VitalSample sample = VitalSample.builder()
                .id(37L).patient(patient).timestamp(Instant.now())
                .spo2(95.0).build();

        final VitalSampleDTO dto = VitalSampleDTO.builder()
                .patientId(1L).timestamp(Instant.now()).spo2(95.0).build();

        when(patientRepository.findById(1L)).thenReturn(Optional.of(patient));
        when(vitalSampleRepository.save(any(VitalSample.class))).thenReturn(sample);

        final VitalSampleDTO result = vitalSampleService.createVitalSample(dto);
        assertNotNull(result);
    }

    @Test
    @DisplayName("createVitalSample_spo2Exactly90_highAlert")
    void createVitalSample_spo2Exactly90_highAlert() throws Exception {
        final VitalSample sample = VitalSample.builder()
                .id(38L).patient(patient).timestamp(Instant.now())
                .spo2(90.0).build();

        final VitalSampleDTO dto = VitalSampleDTO.builder()
                .patientId(1L).timestamp(Instant.now()).spo2(90.0).build();

        when(patientRepository.findById(1L)).thenReturn(Optional.of(patient));
        when(vitalSampleRepository.save(any(VitalSample.class))).thenReturn(sample);

        final VitalSampleDTO result = vitalSampleService.createVitalSample(dto);
        assertNotNull(result);
    }
}
