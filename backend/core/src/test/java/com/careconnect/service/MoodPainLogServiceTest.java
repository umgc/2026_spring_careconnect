package com.careconnect.service;

import com.careconnect.dto.MoodPainAnalyticsDTO;
import com.careconnect.dto.MoodPainLogRequest;
import com.careconnect.dto.MoodPainLogResponse;
import com.careconnect.exception.AppException;
import com.careconnect.model.MoodPainLog;
import com.careconnect.model.Patient;
import com.careconnect.model.User;
import com.careconnect.repository.MoodPainLogRepository;
import com.careconnect.repository.PatientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class MoodPainLogServiceTest {

    @Mock
    private MoodPainLogRepository moodPainLogRepository;

    @Mock
    private PatientRepository patientRepository;

    @InjectMocks
    private MoodPainLogService moodPainLogService;

    private User testUser;
    private Patient testPatient;
    private MoodPainLog testLog;
    private MoodPainLogRequest validRequest;
    private LocalDateTime pastTimestamp;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);

        testUser = User.builder().id(1L).email("test@test.com").build();
        testPatient = Patient.builder().id(10L).user(testUser).build();

        pastTimestamp = LocalDateTime.now().minusDays(1);

        testLog = MoodPainLog.builder()
                .id(100L)
                .patient(testPatient)
                .moodValue(7)
                .painValue(3)
                .note("Feeling okay")
                .timestamp(pastTimestamp)
                .createdAt(pastTimestamp)
                .updatedAt(pastTimestamp)
                .build();

        validRequest = MoodPainLogRequest.builder()
                .moodValue(7)
                .painValue(3)
                .note("Feeling okay")
                .timestamp(pastTimestamp)
                .build();
    }

    @Test
    @DisplayName("createMoodPainLog - valid request - returns saved response")
    void createMoodPainLog_validRequest_returnsSavedResponse() throws Exception {
        when(patientRepository.findByUser(testUser)).thenReturn(Optional.of(testPatient));
        when(moodPainLogRepository.save(any(MoodPainLog.class))).thenReturn(testLog);

        final MoodPainLogResponse response = moodPainLogService.createMoodPainLog(testUser, validRequest);

        assertNotNull(response);
        assertEquals(100L, response.getId());
        assertEquals(10L, response.getPatientId());
        assertEquals(7, response.getMoodValue());
        assertEquals(3, response.getPainValue());
        assertEquals("Feeling okay", response.getNote());
        assertEquals(pastTimestamp, response.getTimestamp());
        verify(moodPainLogRepository).save(any(MoodPainLog.class));
    }

    @Test
    @DisplayName("createMoodPainLog - patient not found - throws AppException")
    void createMoodPainLog_patientNotFound_throwsAppException() throws Exception {
        when(patientRepository.findByUser(testUser)).thenReturn(Optional.empty());

        final AppException ex = assertThrows(AppException.class,
                () -> moodPainLogService.createMoodPainLog(testUser, validRequest));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        assertEquals("Patient profile not found", ex.getMessage());
    }

    @Test
    @DisplayName("createMoodPainLog - null mood value - throws AppException")
    void createMoodPainLog_nullMoodValue_throwsAppException() throws Exception {
        when(patientRepository.findByUser(testUser)).thenReturn(Optional.of(testPatient));
        final MoodPainLogRequest request = MoodPainLogRequest.builder()
                .moodValue(null)
                .painValue(3)
                .timestamp(pastTimestamp)
                .build();

        final AppException ex = assertThrows(AppException.class,
                () -> moodPainLogService.createMoodPainLog(testUser, request));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("Mood value must be between 1 and 10", ex.getMessage());
    }

    @Test
    @DisplayName("createMoodPainLog - mood value below 1 - throws AppException")
    void createMoodPainLog_moodValueBelow1_throwsAppException() throws Exception {
        when(patientRepository.findByUser(testUser)).thenReturn(Optional.of(testPatient));
        final MoodPainLogRequest request = MoodPainLogRequest.builder()
                .moodValue(0)
                .painValue(3)
                .timestamp(pastTimestamp)
                .build();

        final AppException ex = assertThrows(AppException.class,
                () -> moodPainLogService.createMoodPainLog(testUser, request));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("Mood value must be between 1 and 10", ex.getMessage());
    }

    @Test
    @DisplayName("createMoodPainLog - mood value above 10 - throws AppException")
    void createMoodPainLog_moodValueAbove10_throwsAppException() throws Exception {
        when(patientRepository.findByUser(testUser)).thenReturn(Optional.of(testPatient));
        final MoodPainLogRequest request = MoodPainLogRequest.builder()
                .moodValue(11)
                .painValue(3)
                .timestamp(pastTimestamp)
                .build();

        final AppException ex = assertThrows(AppException.class,
                () -> moodPainLogService.createMoodPainLog(testUser, request));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("Mood value must be between 1 and 10", ex.getMessage());
    }

    @Test
    @DisplayName("createMoodPainLog - null pain value - throws AppException")
    void createMoodPainLog_nullPainValue_throwsAppException() throws Exception {
        when(patientRepository.findByUser(testUser)).thenReturn(Optional.of(testPatient));
        final MoodPainLogRequest request = MoodPainLogRequest.builder()
                .moodValue(5)
                .painValue(null)
                .timestamp(pastTimestamp)
                .build();

        final AppException ex = assertThrows(AppException.class,
                () -> moodPainLogService.createMoodPainLog(testUser, request));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("Pain value must be between 0 and 10", ex.getMessage());
    }

    @Test
    @DisplayName("createMoodPainLog - pain value below 0 - throws AppException")
    void createMoodPainLog_painValueBelow0_throwsAppException() throws Exception {
        when(patientRepository.findByUser(testUser)).thenReturn(Optional.of(testPatient));
        final MoodPainLogRequest request = MoodPainLogRequest.builder()
                .moodValue(5)
                .painValue(-1)
                .timestamp(pastTimestamp)
                .build();

        final AppException ex = assertThrows(AppException.class,
                () -> moodPainLogService.createMoodPainLog(testUser, request));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("Pain value must be between 0 and 10", ex.getMessage());
    }

    @Test
    @DisplayName("createMoodPainLog - pain value above 10 - throws AppException")
    void createMoodPainLog_painValueAbove10_throwsAppException() throws Exception {
        when(patientRepository.findByUser(testUser)).thenReturn(Optional.of(testPatient));
        final MoodPainLogRequest request = MoodPainLogRequest.builder()
                .moodValue(5)
                .painValue(11)
                .timestamp(pastTimestamp)
                .build();

        final AppException ex = assertThrows(AppException.class,
                () -> moodPainLogService.createMoodPainLog(testUser, request));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("Pain value must be between 0 and 10", ex.getMessage());
    }

    @Test
    @DisplayName("createMoodPainLog - null timestamp - throws AppException")
    void createMoodPainLog_nullTimestamp_throwsAppException() throws Exception {
        when(patientRepository.findByUser(testUser)).thenReturn(Optional.of(testPatient));
        final MoodPainLogRequest request = MoodPainLogRequest.builder()
                .moodValue(5)
                .painValue(3)
                .timestamp(null)
                .build();

        final AppException ex = assertThrows(AppException.class,
                () -> moodPainLogService.createMoodPainLog(testUser, request));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("Timestamp is required", ex.getMessage());
    }

    @Test
    @DisplayName("createMoodPainLog - future timestamp - throws AppException")
    void createMoodPainLog_futureTimestamp_throwsAppException() throws Exception {
        when(patientRepository.findByUser(testUser)).thenReturn(Optional.of(testPatient));
        final MoodPainLogRequest request = MoodPainLogRequest.builder()
                .moodValue(5)
                .painValue(3)
                .timestamp(LocalDateTime.now().plusDays(1))
                .build();

        final AppException ex = assertThrows(AppException.class,
                () -> moodPainLogService.createMoodPainLog(testUser, request));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("Timestamp cannot be in the future", ex.getMessage());
    }

    @Test
    @DisplayName("getMoodPainLogs - logs exist - returns list of responses")
    void getMoodPainLogs_logsExist_returnsListOfResponses() throws Exception {
        when(patientRepository.findByUser(testUser)).thenReturn(Optional.of(testPatient));
        when(moodPainLogRepository.findByPatientOrderByTimestampDesc(testPatient))
                .thenReturn(Arrays.asList(testLog));

        final List<MoodPainLogResponse> result = moodPainLogService.getMoodPainLogs(testUser);

        assertEquals(1, result.size());
        assertEquals(100L, result.get(0).getId());
    }

    @Test
    @DisplayName("getMoodPainLogs - no logs exist - returns empty list")
    void getMoodPainLogs_noLogsExist_returnsEmptyList() throws Exception {
        when(patientRepository.findByUser(testUser)).thenReturn(Optional.of(testPatient));
        when(moodPainLogRepository.findByPatientOrderByTimestampDesc(testPatient))
                .thenReturn(Collections.emptyList());

        final List<MoodPainLogResponse> result = moodPainLogService.getMoodPainLogs(testUser);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("getMoodPainLogs - patient not found - throws AppException")
    void getMoodPainLogs_patientNotFound_throwsAppException() throws Exception {
        when(patientRepository.findByUser(testUser)).thenReturn(Optional.empty());

        final AppException ex = assertThrows(AppException.class,
                () -> moodPainLogService.getMoodPainLogs(testUser));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    @DisplayName("getMoodPainLogsWithPagination - valid request - returns paginated responses")
    void getMoodPainLogsWithPagination_validRequest_returnsPaginatedResponses() throws Exception {
        when(patientRepository.findByUser(testUser)).thenReturn(Optional.of(testPatient));
        final Pageable pageable = PageRequest.of(0, 10);
        final Page<MoodPainLog> page = new PageImpl<>(Arrays.asList(testLog), pageable, 1);
        when(moodPainLogRepository.findByPatientOrderByTimestampDesc(eq(testPatient), any(Pageable.class)))
                .thenReturn(page);

        final Page<MoodPainLogResponse> result = moodPainLogService.getMoodPainLogsWithPagination(testUser, 0, 10);

        assertEquals(1, result.getTotalElements());
        assertEquals(100L, result.getContent().get(0).getId());
    }

    @Test
    @DisplayName("getMoodPainLogsWithPagination - patient not found - throws AppException")
    void getMoodPainLogsWithPagination_patientNotFound_throwsAppException() throws Exception {
        when(patientRepository.findByUser(testUser)).thenReturn(Optional.empty());

        final AppException ex = assertThrows(AppException.class,
                () -> moodPainLogService.getMoodPainLogsWithPagination(testUser, 0, 10));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    @DisplayName("getMoodPainLogsByDateRange - valid range - returns matching logs")
    void getMoodPainLogsByDateRange_validRange_returnsMatchingLogs() throws Exception {
        final LocalDateTime start = LocalDateTime.now().minusDays(7);
        final LocalDateTime end = LocalDateTime.now();
        when(patientRepository.findByUser(testUser)).thenReturn(Optional.of(testPatient));
        when(moodPainLogRepository.findByPatientAndTimestampBetween(testPatient, start, end))
                .thenReturn(Arrays.asList(testLog));

        final List<MoodPainLogResponse> result = moodPainLogService.getMoodPainLogsByDateRange(testUser, start, end);

        assertEquals(1, result.size());
        assertEquals(100L, result.get(0).getId());
    }

    @Test
    @DisplayName("getMoodPainLogsByDateRange - patient not found - throws AppException")
    void getMoodPainLogsByDateRange_patientNotFound_throwsAppException() throws Exception {
        when(patientRepository.findByUser(testUser)).thenReturn(Optional.empty());

        final AppException ex = assertThrows(AppException.class,
                () -> moodPainLogService.getMoodPainLogsByDateRange(testUser,
                        LocalDateTime.now().minusDays(7), LocalDateTime.now()));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    @DisplayName("getLatestMoodPainLog - log exists - returns latest response")
    void getLatestMoodPainLog_logExists_returnsLatestResponse() throws Exception {
        when(patientRepository.findByUser(testUser)).thenReturn(Optional.of(testPatient));
        when(moodPainLogRepository.findFirstByPatientOrderByTimestampDesc(testPatient))
                .thenReturn(testLog);

        final MoodPainLogResponse result = moodPainLogService.getLatestMoodPainLog(testUser);

        assertNotNull(result);
        assertEquals(100L, result.getId());
    }

    @Test
    @DisplayName("getLatestMoodPainLog - no logs found - throws AppException")
    void getLatestMoodPainLog_noLogsFound_throwsAppException() throws Exception {
        when(patientRepository.findByUser(testUser)).thenReturn(Optional.of(testPatient));
        when(moodPainLogRepository.findFirstByPatientOrderByTimestampDesc(testPatient))
                .thenReturn(null);

        final AppException ex = assertThrows(AppException.class,
                () -> moodPainLogService.getLatestMoodPainLog(testUser));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        assertEquals("No mood pain logs found for this patient", ex.getMessage());
    }

    @Test
    @DisplayName("getLatestMoodPainLog - patient not found - throws AppException")
    void getLatestMoodPainLog_patientNotFound_throwsAppException() throws Exception {
        when(patientRepository.findByUser(testUser)).thenReturn(Optional.empty());

        final AppException ex = assertThrows(AppException.class,
                () -> moodPainLogService.getLatestMoodPainLog(testUser));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    @DisplayName("updateMoodPainLog - valid update - returns updated response")
    void updateMoodPainLog_validUpdate_returnsUpdatedResponse() throws Exception {
        when(patientRepository.findByUser(testUser)).thenReturn(Optional.of(testPatient));
        when(moodPainLogRepository.findById(100L)).thenReturn(Optional.of(testLog));

        final MoodPainLog updatedLog = MoodPainLog.builder()
                .id(100L)
                .patient(testPatient)
                .moodValue(8)
                .painValue(2)
                .note("Feeling better")
                .timestamp(pastTimestamp)
                .createdAt(pastTimestamp)
                .updatedAt(pastTimestamp)
                .build();
        when(moodPainLogRepository.save(any(MoodPainLog.class))).thenReturn(updatedLog);

        final MoodPainLogRequest updateRequest = MoodPainLogRequest.builder()
                .moodValue(8)
                .painValue(2)
                .note("Feeling better")
                .timestamp(pastTimestamp)
                .build();

        final MoodPainLogResponse result = moodPainLogService.updateMoodPainLog(testUser, 100L, updateRequest);

        assertNotNull(result);
        assertEquals(8, result.getMoodValue());
        assertEquals(2, result.getPainValue());
        assertEquals("Feeling better", result.getNote());
    }

    @Test
    @DisplayName("updateMoodPainLog - patient not found - throws AppException")
    void updateMoodPainLog_patientNotFound_throwsAppException() throws Exception {
        when(patientRepository.findByUser(testUser)).thenReturn(Optional.empty());

        final AppException ex = assertThrows(AppException.class,
                () -> moodPainLogService.updateMoodPainLog(testUser, 100L, validRequest));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    @DisplayName("updateMoodPainLog - log not found - throws AppException")
    void updateMoodPainLog_logNotFound_throwsAppException() throws Exception {
        when(patientRepository.findByUser(testUser)).thenReturn(Optional.of(testPatient));
        when(moodPainLogRepository.findById(999L)).thenReturn(Optional.empty());

        final AppException ex = assertThrows(AppException.class,
                () -> moodPainLogService.updateMoodPainLog(testUser, 999L, validRequest));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        assertEquals("Mood pain log not found", ex.getMessage());
    }

    @Test
    @DisplayName("updateMoodPainLog - log belongs to different patient - throws AppException")
    void updateMoodPainLog_logBelongsToDifferentPatient_throwsAppException() throws Exception {
        final Patient otherPatient = Patient.builder().id(99L).build();
        final MoodPainLog otherLog = MoodPainLog.builder()
                .id(200L)
                .patient(otherPatient)
                .moodValue(5)
                .painValue(5)
                .timestamp(pastTimestamp)
                .build();

        when(patientRepository.findByUser(testUser)).thenReturn(Optional.of(testPatient));
        when(moodPainLogRepository.findById(200L)).thenReturn(Optional.of(otherLog));

        final AppException ex = assertThrows(AppException.class,
                () -> moodPainLogService.updateMoodPainLog(testUser, 200L, validRequest));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertEquals("You don't have permission to update this log", ex.getMessage());
    }

    @Test
    @DisplayName("updateMoodPainLog - invalid request values - throws AppException")
    void updateMoodPainLog_invalidRequestValues_throwsAppException() throws Exception {
        when(patientRepository.findByUser(testUser)).thenReturn(Optional.of(testPatient));
        when(moodPainLogRepository.findById(100L)).thenReturn(Optional.of(testLog));

        final MoodPainLogRequest badRequest = MoodPainLogRequest.builder()
                .moodValue(null)
                .painValue(3)
                .timestamp(pastTimestamp)
                .build();

        final AppException ex = assertThrows(AppException.class,
                () -> moodPainLogService.updateMoodPainLog(testUser, 100L, badRequest));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    @DisplayName("deleteMoodPainLog - valid delete - deletes log successfully")
    void deleteMoodPainLog_validDelete_deletesLogSuccessfully() throws Exception {
        when(patientRepository.findByUser(testUser)).thenReturn(Optional.of(testPatient));
        when(moodPainLogRepository.findById(100L)).thenReturn(Optional.of(testLog));

        moodPainLogService.deleteMoodPainLog(testUser, 100L);

        verify(moodPainLogRepository).delete(testLog);
    }

    @Test
    @DisplayName("deleteMoodPainLog - patient not found - throws AppException")
    void deleteMoodPainLog_patientNotFound_throwsAppException() throws Exception {
        when(patientRepository.findByUser(testUser)).thenReturn(Optional.empty());

        final AppException ex = assertThrows(AppException.class,
                () -> moodPainLogService.deleteMoodPainLog(testUser, 100L));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    @DisplayName("deleteMoodPainLog - log not found - throws AppException")
    void deleteMoodPainLog_logNotFound_throwsAppException() throws Exception {
        when(patientRepository.findByUser(testUser)).thenReturn(Optional.of(testPatient));
        when(moodPainLogRepository.findById(999L)).thenReturn(Optional.empty());

        final AppException ex = assertThrows(AppException.class,
                () -> moodPainLogService.deleteMoodPainLog(testUser, 999L));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        assertEquals("Mood pain log not found", ex.getMessage());
    }

    @Test
    @DisplayName("deleteMoodPainLog - log belongs to different patient - throws AppException")
    void deleteMoodPainLog_logBelongsToDifferentPatient_throwsAppException() throws Exception {
        final Patient otherPatient = Patient.builder().id(99L).build();
        final MoodPainLog otherLog = MoodPainLog.builder()
                .id(200L)
                .patient(otherPatient)
                .moodValue(5)
                .painValue(5)
                .timestamp(pastTimestamp)
                .build();

        when(patientRepository.findByUser(testUser)).thenReturn(Optional.of(testPatient));
        when(moodPainLogRepository.findById(200L)).thenReturn(Optional.of(otherLog));

        final AppException ex = assertThrows(AppException.class,
                () -> moodPainLogService.deleteMoodPainLog(testUser, 200L));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertEquals("You don't have permission to delete this log", ex.getMessage());
    }

    @Test
    @DisplayName("getMoodPainLogsForPatient - patient exists - returns logs")
    void getMoodPainLogsForPatient_patientExists_returnsLogs() throws Exception {
        when(patientRepository.findById(10L)).thenReturn(Optional.of(testPatient));
        when(moodPainLogRepository.findByPatientOrderByTimestampDesc(testPatient))
                .thenReturn(Arrays.asList(testLog));

        final List<MoodPainLogResponse> result = moodPainLogService.getMoodPainLogsForPatient(10L);

        assertEquals(1, result.size());
        assertEquals(100L, result.get(0).getId());
    }

    @Test
    @DisplayName("getMoodPainLogsForPatient - patient not found - throws AppException")
    void getMoodPainLogsForPatient_patientNotFound_throwsAppException() throws Exception {
        when(patientRepository.findById(999L)).thenReturn(Optional.empty());

        final AppException ex = assertThrows(AppException.class,
                () -> moodPainLogService.getMoodPainLogsForPatient(999L));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        assertEquals("Patient not found", ex.getMessage());
    }

    @Test
    @DisplayName("getMoodPainAnalytics - empty logs - returns empty analytics")
    void getMoodPainAnalytics_emptyLogs_returnsEmptyAnalytics() throws Exception {
        final LocalDateTime start = LocalDateTime.now().minusDays(7);
        final LocalDateTime end = LocalDateTime.now();
        when(patientRepository.findByUser(testUser)).thenReturn(Optional.of(testPatient));
        when(moodPainLogRepository.findByPatientAndTimestampBetween(testPatient, start, end))
                .thenReturn(Collections.emptyList());

        final MoodPainAnalyticsDTO result = moodPainLogService.getMoodPainAnalytics(testUser, start, end);

        assertNotNull(result);
        assertEquals(start, result.getPeriodStart());
        assertEquals(end, result.getPeriodEnd());
        assertEquals(0, result.getTotalEntries());
        assertEquals(0, result.getMoodEntries());
        assertEquals(0, result.getPainEntries());
        assertTrue(result.getTimeSeries().isEmpty());
    }

    @Test
    @DisplayName("getMoodPainAnalytics - with mood and pain values - returns computed analytics")
    void getMoodPainAnalytics_withMoodAndPainValues_returnsComputedAnalytics() throws Exception {
        final LocalDateTime start = LocalDateTime.now().minusDays(7);
        final LocalDateTime end = LocalDateTime.now();

        final MoodPainLog log1 = MoodPainLog.builder()
                .id(1L).patient(testPatient)
                .moodValue(6).painValue(4)
                .note("note1")
                .timestamp(start.plusDays(1))
                .build();
        final MoodPainLog log2 = MoodPainLog.builder()
                .id(2L).patient(testPatient)
                .moodValue(8).painValue(2)
                .note("note2")
                .timestamp(start.plusDays(2))
                .build();

        when(patientRepository.findByUser(testUser)).thenReturn(Optional.of(testPatient));
        when(moodPainLogRepository.findByPatientAndTimestampBetween(testPatient, start, end))
                .thenReturn(Arrays.asList(log1, log2));

        final MoodPainAnalyticsDTO result = moodPainLogService.getMoodPainAnalytics(testUser, start, end);

        assertNotNull(result);
        assertEquals(2, result.getTotalEntries());
        assertEquals(2, result.getMoodEntries());
        assertEquals(2, result.getPainEntries());
        assertEquals(7.0, result.getAvgMood());
        assertEquals(3.0, result.getAvgPain());
        assertEquals(6, result.getMinMood());
        assertEquals(8, result.getMaxMood());
        assertEquals(2, result.getMinPain());
        assertEquals(4, result.getMaxPain());
        assertNotNull(result.getMoodTrend());
        assertNotNull(result.getPainTrend());
        assertEquals(2, result.getTimeSeries().size());
    }

    @Test
    @DisplayName("getMoodPainAnalytics - only one log with values - trend is null")
    void getMoodPainAnalytics_onlyOneLog_trendIsNull() throws Exception {
        final LocalDateTime start = LocalDateTime.now().minusDays(7);
        final LocalDateTime end = LocalDateTime.now();

        final MoodPainLog singleLog = MoodPainLog.builder()
                .id(1L).patient(testPatient)
                .moodValue(5).painValue(3)
                .note("only one")
                .timestamp(start.plusDays(1))
                .build();

        when(patientRepository.findByUser(testUser)).thenReturn(Optional.of(testPatient));
        when(moodPainLogRepository.findByPatientAndTimestampBetween(testPatient, start, end))
                .thenReturn(Arrays.asList(singleLog));

        final MoodPainAnalyticsDTO result = moodPainLogService.getMoodPainAnalytics(testUser, start, end);

        assertNotNull(result);
        assertEquals(1, result.getTotalEntries());
        assertNull(result.getMoodTrend());
        assertNull(result.getPainTrend());
    }

    @Test
    @DisplayName("getMoodPainAnalytics - null mood values - mood stats are null")
    void getMoodPainAnalytics_nullMoodValues_moodStatsAreNull() throws Exception {
        final LocalDateTime start = LocalDateTime.now().minusDays(7);
        final LocalDateTime end = LocalDateTime.now();

        final MoodPainLog log1 = MoodPainLog.builder()
                .id(1L).patient(testPatient)
                .moodValue(null).painValue(4)
                .note("pain only 1")
                .timestamp(start.plusDays(1))
                .build();
        final MoodPainLog log2 = MoodPainLog.builder()
                .id(2L).patient(testPatient)
                .moodValue(null).painValue(6)
                .note("pain only 2")
                .timestamp(start.plusDays(2))
                .build();

        when(patientRepository.findByUser(testUser)).thenReturn(Optional.of(testPatient));
        when(moodPainLogRepository.findByPatientAndTimestampBetween(testPatient, start, end))
                .thenReturn(Arrays.asList(log1, log2));

        final MoodPainAnalyticsDTO result = moodPainLogService.getMoodPainAnalytics(testUser, start, end);

        assertNotNull(result);
        assertEquals(2, result.getTotalEntries());
        assertEquals(0, result.getMoodEntries());
        assertEquals(2, result.getPainEntries());
        assertNull(result.getAvgMood());
        assertNull(result.getMinMood());
        assertNull(result.getMaxMood());
        assertNull(result.getMoodTrend());
        assertNotNull(result.getAvgPain());
        assertNotNull(result.getPainTrend());
    }

    @Test
    @DisplayName("getMoodPainAnalytics - null pain values - pain stats are null")
    void getMoodPainAnalytics_nullPainValues_painStatsAreNull() throws Exception {
        final LocalDateTime start = LocalDateTime.now().minusDays(7);
        final LocalDateTime end = LocalDateTime.now();

        final MoodPainLog log1 = MoodPainLog.builder()
                .id(1L).patient(testPatient)
                .moodValue(5).painValue(null)
                .note("mood only 1")
                .timestamp(start.plusDays(1))
                .build();
        final MoodPainLog log2 = MoodPainLog.builder()
                .id(2L).patient(testPatient)
                .moodValue(7).painValue(null)
                .note("mood only 2")
                .timestamp(start.plusDays(2))
                .build();

        when(patientRepository.findByUser(testUser)).thenReturn(Optional.of(testPatient));
        when(moodPainLogRepository.findByPatientAndTimestampBetween(testPatient, start, end))
                .thenReturn(Arrays.asList(log1, log2));

        final MoodPainAnalyticsDTO result = moodPainLogService.getMoodPainAnalytics(testUser, start, end);

        assertNotNull(result);
        assertEquals(2, result.getTotalEntries());
        assertEquals(2, result.getMoodEntries());
        assertEquals(0, result.getPainEntries());
        assertNotNull(result.getAvgMood());
        assertNotNull(result.getMoodTrend());
        assertNull(result.getAvgPain());
        assertNull(result.getMinPain());
        assertNull(result.getMaxPain());
        assertNull(result.getPainTrend());
    }

    @Test
    @DisplayName("getMoodPainAnalytics - patient not found - throws AppException")
    void getMoodPainAnalytics_patientNotFound_throwsAppException() throws Exception {
        when(patientRepository.findByUser(testUser)).thenReturn(Optional.empty());

        final AppException ex = assertThrows(AppException.class,
                () -> moodPainLogService.getMoodPainAnalytics(testUser,
                        LocalDateTime.now().minusDays(7), LocalDateTime.now()));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    @DisplayName("getMoodPainAnalytics - three logs with mixed null values - computes correctly")
    void getMoodPainAnalytics_threeLogsWithMixedNulls_computesCorrectly() throws Exception {
        final LocalDateTime start = LocalDateTime.now().minusDays(7);
        final LocalDateTime end = LocalDateTime.now();

        final MoodPainLog log1 = MoodPainLog.builder()
                .id(1L).patient(testPatient)
                .moodValue(4).painValue(null)
                .note("a")
                .timestamp(start.plusDays(1))
                .build();
        final MoodPainLog log2 = MoodPainLog.builder()
                .id(2L).patient(testPatient)
                .moodValue(null).painValue(6)
                .note("b")
                .timestamp(start.plusDays(2))
                .build();
        final MoodPainLog log3 = MoodPainLog.builder()
                .id(3L).patient(testPatient)
                .moodValue(8).painValue(2)
                .note("c")
                .timestamp(start.plusDays(3))
                .build();

        when(patientRepository.findByUser(testUser)).thenReturn(Optional.of(testPatient));
        when(moodPainLogRepository.findByPatientAndTimestampBetween(testPatient, start, end))
                .thenReturn(Arrays.asList(log1, log2, log3));

        final MoodPainAnalyticsDTO result = moodPainLogService.getMoodPainAnalytics(testUser, start, end);

        assertNotNull(result);
        assertEquals(3, result.getTotalEntries());
        assertEquals(2, result.getMoodEntries());
        assertEquals(2, result.getPainEntries());
        assertEquals(6.0, result.getAvgMood());
        assertEquals(4.0, result.getAvgPain());
        assertEquals(4, result.getMinMood());
        assertEquals(8, result.getMaxMood());
        assertEquals(2, result.getMinPain());
        assertEquals(6, result.getMaxPain());
        assertNotNull(result.getMoodTrend());
        assertNotNull(result.getPainTrend());
        assertEquals(3, result.getTimeSeries().size());
    }

    @Test
    @DisplayName("createMoodPainLog - mood value exactly 1 and pain value exactly 0 - succeeds")
    void createMoodPainLog_moodValue1PainValue0_succeeds() throws Exception {
        when(patientRepository.findByUser(testUser)).thenReturn(Optional.of(testPatient));

        final MoodPainLog savedLog = MoodPainLog.builder()
                .id(101L).patient(testPatient)
                .moodValue(1).painValue(0).note(null)
                .timestamp(pastTimestamp)
                .createdAt(pastTimestamp).updatedAt(pastTimestamp)
                .build();
        when(moodPainLogRepository.save(any(MoodPainLog.class))).thenReturn(savedLog);

        final MoodPainLogRequest request = MoodPainLogRequest.builder()
                .moodValue(1).painValue(0).timestamp(pastTimestamp).build();

        final MoodPainLogResponse result = moodPainLogService.createMoodPainLog(testUser, request);

        assertEquals(1, result.getMoodValue());
        assertEquals(0, result.getPainValue());
    }

    @Test
    @DisplayName("createMoodPainLog - mood value exactly 10 and pain value exactly 10 - succeeds")
    void createMoodPainLog_moodValue10PainValue10_succeeds() throws Exception {
        when(patientRepository.findByUser(testUser)).thenReturn(Optional.of(testPatient));

        final MoodPainLog savedLog = MoodPainLog.builder()
                .id(102L).patient(testPatient)
                .moodValue(10).painValue(10).note("max")
                .timestamp(pastTimestamp)
                .createdAt(pastTimestamp).updatedAt(pastTimestamp)
                .build();
        when(moodPainLogRepository.save(any(MoodPainLog.class))).thenReturn(savedLog);

        final MoodPainLogRequest request = MoodPainLogRequest.builder()
                .moodValue(10).painValue(10).note("max").timestamp(pastTimestamp).build();

        final MoodPainLogResponse result = moodPainLogService.createMoodPainLog(testUser, request);

        assertEquals(10, result.getMoodValue());
        assertEquals(10, result.getPainValue());
    }
}
