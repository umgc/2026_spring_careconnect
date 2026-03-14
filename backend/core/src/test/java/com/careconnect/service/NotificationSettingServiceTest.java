package com.careconnect.service;

import com.careconnect.dto.NotificationSettingDTO;
import com.careconnect.model.NotificationSetting;
import com.careconnect.repository.NotificationSettingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class NotificationSettingServiceTest {

    @Mock
    private NotificationSettingRepository notificationSettingRepository;

    @InjectMocks
    private NotificationSettingService notificationSettingService;

    private NotificationSetting existingSetting;
    private Instant now;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        now = Instant.now();

        existingSetting = NotificationSetting.builder()
                .userId(1L)
                .gamification(true)
                .emergency(true)
                .videoCall(true)
                .audioCall(true)
                .sms(true)
                .significantVitals(true)
                .build();
        existingSetting.setId(10L);
        existingSetting.setCreatedAt(now);
        existingSetting.setUpdatedAt(now);
    }

    // ========== getByUserId tests ==========

    @Test
    @DisplayName("getByUserId_settingExists_returnsExistingSettingAsDTO")
    void getByUserId_settingExists_returnsExistingSettingAsDTO() throws Exception {
        when(notificationSettingRepository.findByUserId(1L)).thenReturn(Optional.of(existingSetting));

        final NotificationSettingDTO result = notificationSettingService.getByUserId(1L);

        assertNotNull(result);
        assertEquals(10L, result.id());
        assertEquals(1L, result.userId());
        assertTrue(result.gamification());
        assertTrue(result.emergency());
        assertTrue(result.videoCall());
        assertTrue(result.audioCall());
        assertTrue(result.sms());
        assertTrue(result.significantVitals());
        assertEquals(now, result.createdAt());
        assertEquals(now, result.updatedAt());

        verify(notificationSettingRepository).findByUserId(1L);
        verify(notificationSettingRepository, never()).save(any());
    }

    @Test
    @DisplayName("getByUserId_settingDoesNotExist_createsDefaultAndReturnsDTO")
    void getByUserId_settingDoesNotExist_createsDefaultAndReturnsDTO() throws Exception {
        final NotificationSetting savedSetting = NotificationSetting.builder()
                .userId(2L)
                .gamification(true)
                .emergency(true)
                .videoCall(true)
                .audioCall(true)
                .sms(true)
                .significantVitals(true)
                .build();
        savedSetting.setId(20L);
        savedSetting.setCreatedAt(now);
        savedSetting.setUpdatedAt(now);

        when(notificationSettingRepository.findByUserId(2L)).thenReturn(Optional.empty());
        when(notificationSettingRepository.save(any(NotificationSetting.class))).thenReturn(savedSetting);

        final NotificationSettingDTO result = notificationSettingService.getByUserId(2L);

        assertNotNull(result);
        assertEquals(20L, result.id());
        assertEquals(2L, result.userId());
        assertTrue(result.gamification());
        assertTrue(result.emergency());
        assertTrue(result.videoCall());
        assertTrue(result.audioCall());
        assertTrue(result.sms());
        assertTrue(result.significantVitals());

        verify(notificationSettingRepository).findByUserId(2L);
        verify(notificationSettingRepository).save(argThat(setting -> setting.getUserId().equals(2L)));
    }

    @Test
    @DisplayName("getByUserId_settingWithAllFalseFlags_returnsDTOWithAllFalse")
    void getByUserId_settingWithAllFalseFlags_returnsDTOWithAllFalse() throws Exception {
        final NotificationSetting allFalse = NotificationSetting.builder()
                .userId(3L)
                .build();
        allFalse.setId(30L);
        allFalse.setGamification(false);
        allFalse.setEmergency(false);
        allFalse.setVideoCall(false);
        allFalse.setAudioCall(false);
        allFalse.setSms(false);
        allFalse.setSignificantVitals(false);
        allFalse.setCreatedAt(now);
        allFalse.setUpdatedAt(now);

        when(notificationSettingRepository.findByUserId(3L)).thenReturn(Optional.of(allFalse));

        final NotificationSettingDTO result = notificationSettingService.getByUserId(3L);

        assertNotNull(result);
        assertEquals(3L, result.userId());
        assertFalse(result.gamification());
        assertFalse(result.emergency());
        assertFalse(result.videoCall());
        assertFalse(result.audioCall());
        assertFalse(result.sms());
        assertFalse(result.significantVitals());
    }

    // ========== createOrUpdate tests ==========

    @Test
    @DisplayName("createOrUpdate_settingExists_updatesExistingSettingAndReturnsDTO")
    void createOrUpdate_settingExists_updatesExistingSettingAndReturnsDTO() throws Exception {
        final NotificationSettingDTO inputDTO = NotificationSettingDTO.builder()
                .userId(1L)
                .gamification(false)
                .emergency(false)
                .videoCall(false)
                .audioCall(false)
                .sms(false)
                .significantVitals(false)
                .build();

        when(notificationSettingRepository.findByUserId(1L)).thenReturn(Optional.of(existingSetting));

        final NotificationSetting savedSetting = NotificationSetting.builder()
                .userId(1L)
                .build();
        savedSetting.setId(10L);
        savedSetting.setGamification(false);
        savedSetting.setEmergency(false);
        savedSetting.setVideoCall(false);
        savedSetting.setAudioCall(false);
        savedSetting.setSms(false);
        savedSetting.setSignificantVitals(false);
        savedSetting.setCreatedAt(now);
        savedSetting.setUpdatedAt(now);

        when(notificationSettingRepository.save(any(NotificationSetting.class))).thenReturn(savedSetting);

        final NotificationSettingDTO result = notificationSettingService.createOrUpdate(inputDTO);

        assertNotNull(result);
        assertEquals(10L, result.id());
        assertEquals(1L, result.userId());
        assertFalse(result.gamification());
        assertFalse(result.emergency());
        assertFalse(result.videoCall());
        assertFalse(result.audioCall());
        assertFalse(result.sms());
        assertFalse(result.significantVitals());

        verify(notificationSettingRepository).findByUserId(1L);
        verify(notificationSettingRepository).save(any(NotificationSetting.class));
    }

    @Test
    @DisplayName("createOrUpdate_settingDoesNotExist_createsNewSettingAndReturnsDTO")
    void createOrUpdate_settingDoesNotExist_createsNewSettingAndReturnsDTO() throws Exception {
        final NotificationSettingDTO inputDTO = NotificationSettingDTO.builder()
                .userId(5L)
                .gamification(true)
                .emergency(true)
                .videoCall(false)
                .audioCall(true)
                .sms(false)
                .significantVitals(true)
                .build();

        when(notificationSettingRepository.findByUserId(5L)).thenReturn(Optional.empty());

        final NotificationSetting savedSetting = NotificationSetting.builder()
                .userId(5L)
                .build();
        savedSetting.setId(50L);
        savedSetting.setGamification(true);
        savedSetting.setEmergency(true);
        savedSetting.setVideoCall(false);
        savedSetting.setAudioCall(true);
        savedSetting.setSms(false);
        savedSetting.setSignificantVitals(true);
        savedSetting.setCreatedAt(now);
        savedSetting.setUpdatedAt(now);

        when(notificationSettingRepository.save(any(NotificationSetting.class))).thenReturn(savedSetting);

        final NotificationSettingDTO result = notificationSettingService.createOrUpdate(inputDTO);

        assertNotNull(result);
        assertEquals(50L, result.id());
        assertEquals(5L, result.userId());
        assertTrue(result.gamification());
        assertTrue(result.emergency());
        assertFalse(result.videoCall());
        assertTrue(result.audioCall());
        assertFalse(result.sms());
        assertTrue(result.significantVitals());

        verify(notificationSettingRepository).findByUserId(5L);
        verify(notificationSettingRepository).save(any(NotificationSetting.class));
    }

    @Test
    @DisplayName("createOrUpdate_allFieldsTrue_returnsAllTrueDTO")
    void createOrUpdate_allFieldsTrue_returnsAllTrueDTO() throws Exception {
        final NotificationSettingDTO inputDTO = NotificationSettingDTO.builder()
                .userId(7L)
                .gamification(true)
                .emergency(true)
                .videoCall(true)
                .audioCall(true)
                .sms(true)
                .significantVitals(true)
                .build();

        when(notificationSettingRepository.findByUserId(7L)).thenReturn(Optional.empty());

        final NotificationSetting savedSetting = NotificationSetting.builder()
                .userId(7L)
                .gamification(true)
                .emergency(true)
                .videoCall(true)
                .audioCall(true)
                .sms(true)
                .significantVitals(true)
                .build();
        savedSetting.setId(70L);
        savedSetting.setCreatedAt(now);
        savedSetting.setUpdatedAt(now);

        when(notificationSettingRepository.save(any(NotificationSetting.class))).thenReturn(savedSetting);

        final NotificationSettingDTO result = notificationSettingService.createOrUpdate(inputDTO);

        assertNotNull(result);
        assertTrue(result.gamification());
        assertTrue(result.emergency());
        assertTrue(result.videoCall());
        assertTrue(result.audioCall());
        assertTrue(result.sms());
        assertTrue(result.significantVitals());
    }

    @Test
    @DisplayName("createOrUpdate_allFieldsFalse_returnsAllFalseDTO")
    void createOrUpdate_allFieldsFalse_returnsAllFalseDTO() throws Exception {
        final NotificationSettingDTO inputDTO = NotificationSettingDTO.builder()
                .userId(8L)
                .gamification(false)
                .emergency(false)
                .videoCall(false)
                .audioCall(false)
                .sms(false)
                .significantVitals(false)
                .build();

        when(notificationSettingRepository.findByUserId(8L)).thenReturn(Optional.empty());

        final NotificationSetting savedSetting = NotificationSetting.builder()
                .userId(8L)
                .build();
        savedSetting.setId(80L);
        savedSetting.setGamification(false);
        savedSetting.setEmergency(false);
        savedSetting.setVideoCall(false);
        savedSetting.setAudioCall(false);
        savedSetting.setSms(false);
        savedSetting.setSignificantVitals(false);
        savedSetting.setCreatedAt(now);
        savedSetting.setUpdatedAt(now);

        when(notificationSettingRepository.save(any(NotificationSetting.class))).thenReturn(savedSetting);

        final NotificationSettingDTO result = notificationSettingService.createOrUpdate(inputDTO);

        assertNotNull(result);
        assertFalse(result.gamification());
        assertFalse(result.emergency());
        assertFalse(result.videoCall());
        assertFalse(result.audioCall());
        assertFalse(result.sms());
        assertFalse(result.significantVitals());
    }

    @Test
    @DisplayName("createOrUpdate_existingSettingPartialUpdate_updatesOnlySpecifiedFields")
    void createOrUpdate_existingSettingPartialUpdate_updatesOnlySpecifiedFields() throws Exception {
        final NotificationSettingDTO inputDTO = NotificationSettingDTO.builder()
                .userId(1L)
                .gamification(false)
                .emergency(true)
                .videoCall(false)
                .audioCall(true)
                .sms(false)
                .significantVitals(true)
                .build();

        when(notificationSettingRepository.findByUserId(1L)).thenReturn(Optional.of(existingSetting));

        final NotificationSetting savedSetting = NotificationSetting.builder()
                .userId(1L)
                .build();
        savedSetting.setId(10L);
        savedSetting.setGamification(false);
        savedSetting.setEmergency(true);
        savedSetting.setVideoCall(false);
        savedSetting.setAudioCall(true);
        savedSetting.setSms(false);
        savedSetting.setSignificantVitals(true);
        savedSetting.setCreatedAt(now);
        savedSetting.setUpdatedAt(now);

        when(notificationSettingRepository.save(any(NotificationSetting.class))).thenReturn(savedSetting);

        final NotificationSettingDTO result = notificationSettingService.createOrUpdate(inputDTO);

        assertNotNull(result);
        assertFalse(result.gamification());
        assertTrue(result.emergency());
        assertFalse(result.videoCall());
        assertTrue(result.audioCall());
        assertFalse(result.sms());
        assertTrue(result.significantVitals());
    }

    // ========== toDTO mapping tests (verified through public methods) ==========

    @Test
    @DisplayName("getByUserId_settingWithNullTimestamps_returnsDTOWithNullTimestamps")
    void getByUserId_settingWithNullTimestamps_returnsDTOWithNullTimestamps() throws Exception {
        final NotificationSetting settingNoTimestamps = NotificationSetting.builder()
                .userId(9L)
                .build();
        settingNoTimestamps.setId(90L);
        // createdAt and updatedAt are left null

        when(notificationSettingRepository.findByUserId(9L)).thenReturn(Optional.of(settingNoTimestamps));

        final NotificationSettingDTO result = notificationSettingService.getByUserId(9L);

        assertNotNull(result);
        assertEquals(90L, result.id());
        assertEquals(9L, result.userId());
        assertNull(result.createdAt());
        assertNull(result.updatedAt());
    }

    @Test
    @DisplayName("getByUserId_settingWithNullId_returnsDTOWithNullId")
    void getByUserId_settingWithNullId_returnsDTOWithNullId() throws Exception {
        final NotificationSetting settingNoId = NotificationSetting.builder()
                .userId(11L)
                .build();
        settingNoId.setCreatedAt(now);
        settingNoId.setUpdatedAt(now);

        when(notificationSettingRepository.findByUserId(11L)).thenReturn(Optional.of(settingNoId));

        final NotificationSettingDTO result = notificationSettingService.getByUserId(11L);

        assertNotNull(result);
        assertNull(result.id());
        assertEquals(11L, result.userId());
    }
}
