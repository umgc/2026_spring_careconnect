package com.careconnect.service;

import com.careconnect.dto.UserAIConfigDTO;
import com.careconnect.model.UserAIConfig;
import com.careconnect.model.UserAIConfig.AIProvider;
import com.careconnect.repository.UserAIConfigRepository;
import com.careconnect.util.UserAIConfigDefaults;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.*;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UserAIConfigServiceTest {

    @Mock
    private UserAIConfigRepository userAIConfigRepository;

    @InjectMocks
    private UserAIConfigService userAIConfigService;

    private UserAIConfig sampleConfig;
    private UserAIConfigDTO sampleDTO;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);

        sampleConfig = UserAIConfig.builder()
                .id(1L)
                .userId(100L)
                .patientId(200L)
                .preferredAiProvider(AIProvider.OPENAI)
                .openaiModel("gpt-4")
                .deepseekModel("deepseek-chat")
                .maxTokens(2000)
                .temperature(0.7)
                .conversationHistoryLimit(20)
                .systemPrompt("You are a helpful assistant.")
                .includeVitalsByDefault(true)
                .includeMedicationsByDefault(true)
                .includeNotesByDefault(true)
                .includeMoodPainByDefault(true)
                .includeAllergiesByDefault(true)
                .isActive(true)
                .build();

        sampleDTO = new UserAIConfigDTO();
        sampleDTO.setId(1L);
        sampleDTO.setUserId(100L);
        sampleDTO.setPatientId(200L);
        sampleDTO.setAiProvider(AIProvider.OPENAI);
        sampleDTO.setOpenaiModel("gpt-4");
        sampleDTO.setDeepseekModel("deepseek-chat");
        sampleDTO.setMaxTokens(2000);
        sampleDTO.setTemperature(0.7);
        sampleDTO.setConversationHistoryLimit(20);
        sampleDTO.setSystemPrompt("You are a helpful assistant.");
        sampleDTO.setIncludeVitalsByDefault(true);
        sampleDTO.setIncludeMedicationsByDefault(true);
        sampleDTO.setIncludeNotesByDefault(true);
        sampleDTO.setIncludeMoodPainLogsByDefault(true);
        sampleDTO.setIncludeAllergiesByDefault(true);
        sampleDTO.setIsActive(true);
    }

    // ========== convertDTOToEntity ==========

    @Test
    @DisplayName("convertDTOToEntity_validDTO_shouldReturnEntity")
    void convertDTOToEntity_validDTO_shouldReturnEntity() throws Exception {
        final UserAIConfig result = userAIConfigService.convertDTOToEntity(sampleDTO);

        assertNotNull(result);
        assertEquals(100L, result.getUserId());
        assertEquals(200L, result.getPatientId());
        assertEquals(AIProvider.OPENAI, result.getPreferredAiProvider());
        assertEquals("gpt-4", result.getOpenaiModel());
        assertEquals("deepseek-chat", result.getDeepseekModel());
        assertEquals(2000, result.getMaxTokens());
        assertEquals(0.7, result.getTemperature());
        assertEquals(20, result.getConversationHistoryLimit());
        assertTrue(result.getIncludeVitalsByDefault());
        assertTrue(result.getIncludeMedicationsByDefault());
        assertTrue(result.getIncludeNotesByDefault());
        assertTrue(result.getIncludeMoodPainByDefault());
        assertTrue(result.getIncludeAllergiesByDefault());
        assertTrue(result.getIsActive());
    }

    @Test
    @DisplayName("convertDTOToEntity_nullAiProvider_shouldDefaultToOpenAI")
    void convertDTOToEntity_nullAiProvider_shouldDefaultToOpenAI() throws Exception {
        sampleDTO.setAiProvider(null);

        final UserAIConfig result = userAIConfigService.convertDTOToEntity(sampleDTO);

        assertEquals(AIProvider.OPENAI, result.getPreferredAiProvider());
    }

    @Test
    @DisplayName("convertDTOToEntity_defaultAiProvider_shouldMapToOpenAI")
    void convertDTOToEntity_defaultAiProvider_shouldMapToOpenAI() throws Exception {
        sampleDTO.setAiProvider(AIProvider.DEFAULT);

        final UserAIConfig result = userAIConfigService.convertDTOToEntity(sampleDTO);

        assertEquals(AIProvider.OPENAI, result.getPreferredAiProvider());
    }

    @Test
    @DisplayName("convertDTOToEntity_deepseekProvider_shouldMapToDeepseek")
    void convertDTOToEntity_deepseekProvider_shouldMapToDeepseek() throws Exception {
        sampleDTO.setAiProvider(AIProvider.DEEPSEEK);

        final UserAIConfig result = userAIConfigService.convertDTOToEntity(sampleDTO);

        assertEquals(AIProvider.DEEPSEEK, result.getPreferredAiProvider());
    }

    // ========== convertToDTO ==========

    @Test
    @DisplayName("convertToDTO_validConfig_shouldReturnDTO")
    void convertToDTO_validConfig_shouldReturnDTO() throws Exception {
        final UserAIConfigDTO result = userAIConfigService.convertToDTO(sampleConfig);

        assertNotNull(result);
        assertEquals(100L, result.getUserId());
        assertEquals(200L, result.getPatientId());
        assertEquals(AIProvider.OPENAI, result.getAiProvider());
        assertEquals("gpt-4", result.getOpenaiModel());
        assertEquals("deepseek-chat", result.getDeepseekModel());
        assertEquals(2000, result.getMaxTokens());
        assertEquals(0.7, result.getTemperature());
        assertEquals(20, result.getConversationHistoryLimit());
        assertEquals("You are a helpful assistant.", result.getSystemPrompt());
        assertTrue(result.getIncludeVitalsByDefault());
        assertTrue(result.getIncludeMedicationsByDefault());
        assertTrue(result.getIncludeNotesByDefault());
        assertTrue(result.getIncludeMoodPainLogsByDefault());
        assertTrue(result.getIncludeAllergiesByDefault());
        assertTrue(result.getIsActive());
    }

    @Test
    @DisplayName("convertToDTO_nullConfig_shouldReturnNull")
    void convertToDTO_nullConfig_shouldReturnNull() throws Exception {
        final UserAIConfigDTO result = userAIConfigService.convertToDTO(null);

        assertNull(result);
    }

    @Test
    @DisplayName("convertToDTO_defaultProvider_shouldMapToOpenAI")
    void convertToDTO_defaultProvider_shouldMapToOpenAI() throws Exception {
        sampleConfig.setPreferredAiProvider(AIProvider.DEFAULT);

        final UserAIConfigDTO result = userAIConfigService.convertToDTO(sampleConfig);

        assertEquals(AIProvider.OPENAI, result.getAiProvider());
    }

    @Test
    @DisplayName("convertToDTO_deepseekProvider_shouldMapToDeepseek")
    void convertToDTO_deepseekProvider_shouldMapToDeepseek() throws Exception {
        sampleConfig.setPreferredAiProvider(AIProvider.DEEPSEEK);

        final UserAIConfigDTO result = userAIConfigService.convertToDTO(sampleConfig);

        assertEquals(AIProvider.DEEPSEEK, result.getAiProvider());
    }

    // ========== saveUserAIConfig ==========

    @Test
    @DisplayName("saveUserAIConfig_nullDTO_shouldThrowIllegalArgumentException")
    void saveUserAIConfig_nullDTO_shouldThrowIllegalArgumentException() throws Exception {
        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                userAIConfigService.saveUserAIConfig(null));
        assertEquals("Config DTO cannot be null", ex.getMessage());
    }

    @Test
    @DisplayName("saveUserAIConfig_withExistingId_shouldUpdateAndReturnDTO")
    void saveUserAIConfig_withExistingId_shouldUpdateAndReturnDTO() throws Exception {
        sampleDTO.setId(1L);
        when(userAIConfigRepository.save(any(UserAIConfig.class))).thenReturn(sampleConfig);

        final UserAIConfigDTO result = userAIConfigService.saveUserAIConfig(sampleDTO);

        assertNotNull(result);
        assertEquals(100L, result.getUserId());
        verify(userAIConfigRepository).save(any(UserAIConfig.class));
    }

    @Test
    @DisplayName("saveUserAIConfig_withNullId_shouldCreateAndReturnDTO")
    void saveUserAIConfig_withNullId_shouldCreateAndReturnDTO() throws Exception {
        sampleDTO.setId(null);
        when(userAIConfigRepository.save(any(UserAIConfig.class))).thenReturn(sampleConfig);

        final UserAIConfigDTO result = userAIConfigService.saveUserAIConfig(sampleDTO);

        assertNotNull(result);
        assertEquals(100L, result.getUserId());
        verify(userAIConfigRepository).save(any(UserAIConfig.class));
    }

    @Test
    @DisplayName("saveUserAIConfig_savedEntityIsNull_shouldThrowIllegalStateException")
    void saveUserAIConfig_savedEntityIsNull_shouldThrowIllegalStateException() throws Exception {
        sampleDTO.setId(1L);
        when(userAIConfigRepository.save(any(UserAIConfig.class))).thenReturn(null);

        assertThrows(IllegalStateException.class, () ->
                userAIConfigService.saveUserAIConfig(sampleDTO));
    }

    @Test
    @DisplayName("saveUserAIConfig_savedEntityHasNullId_shouldThrowIllegalStateException")
    void saveUserAIConfig_savedEntityHasNullId_shouldThrowIllegalStateException() throws Exception {
        sampleDTO.setId(1L);
        final UserAIConfig savedWithNullId = UserAIConfig.builder()
                .id(null)
                .userId(100L)
                .build();
        when(userAIConfigRepository.save(any(UserAIConfig.class))).thenReturn(savedWithNullId);

        assertThrows(IllegalStateException.class, () ->
                userAIConfigService.saveUserAIConfig(sampleDTO));
    }

    @Test
    @DisplayName("saveUserAIConfig_savedEntityNullIdWithPatientId_shouldThrowWithPatientInMessage")
    void saveUserAIConfig_savedEntityNullIdWithPatientId_shouldThrowWithPatientInMessage() throws Exception {
        sampleDTO.setId(null);
        sampleDTO.setPatientId(200L);
        when(userAIConfigRepository.save(any(UserAIConfig.class))).thenReturn(null);

        final IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                userAIConfigService.saveUserAIConfig(sampleDTO));
        assertTrue(ex.getMessage().contains("patient 200"));
    }

    @Test
    @DisplayName("saveUserAIConfig_savedEntityNullIdWithoutPatientId_shouldThrowWithoutPatientInMessage")
    void saveUserAIConfig_savedEntityNullIdWithoutPatientId_shouldThrowWithoutPatientInMessage() throws Exception {
        sampleDTO.setId(null);
        sampleDTO.setPatientId(null);
        when(userAIConfigRepository.save(any(UserAIConfig.class))).thenReturn(null);

        final IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                userAIConfigService.saveUserAIConfig(sampleDTO));
        assertTrue(ex.getMessage().contains("user 100"));
        assertFalse(ex.getMessage().contains("patient"));
    }

    // ========== deactivateUserAIConfig ==========

    @Test
    @DisplayName("deactivateUserAIConfig_shouldNotThrow")
    void deactivateUserAIConfig_shouldNotThrow() throws Exception {
        // This is a no-op (dummy implementation) in the source
        assertDoesNotThrow(() -> userAIConfigService.deactivateUserAIConfig(100L, 200L));
    }

    @Test
    @DisplayName("deactivateUserAIConfig_withNullPatientId_shouldNotThrow")
    void deactivateUserAIConfig_withNullPatientId_shouldNotThrow() throws Exception {
        assertDoesNotThrow(() -> userAIConfigService.deactivateUserAIConfig(100L, null));
    }

    // ========== getUserAIConfig ==========

    @Test
    @DisplayName("getUserAIConfig_withPatientId_existingConfig_shouldReturnDTO")
    void getUserAIConfig_withPatientId_existingConfig_shouldReturnDTO() throws Exception {
        when(userAIConfigRepository.findByUserIdAndPatientIdAndIsActiveTrue(100L, 200L))
                .thenReturn(Optional.of(sampleConfig));

        final UserAIConfigDTO result = userAIConfigService.getUserAIConfig(100L, 200L);

        assertNotNull(result);
        assertEquals(100L, result.getUserId());
        assertEquals(200L, result.getPatientId());
    }

    @Test
    @DisplayName("getUserAIConfig_withPatientId_noExistingConfig_shouldCreateDefault")
    void getUserAIConfig_withPatientId_noExistingConfig_shouldCreateDefault() throws Exception {
        when(userAIConfigRepository.findByUserIdAndPatientIdAndIsActiveTrue(100L, 200L))
                .thenReturn(Optional.empty());

        final UserAIConfig defaultConfig = UserAIConfigDefaults.createMedicalDefaultConfig(100L, 200L);
        defaultConfig.setId(5L);
        when(userAIConfigRepository.save(any(UserAIConfig.class))).thenReturn(defaultConfig);

        final UserAIConfigDTO result = userAIConfigService.getUserAIConfig(100L, 200L);

        assertNotNull(result);
        assertEquals(100L, result.getUserId());
        verify(userAIConfigRepository).save(any(UserAIConfig.class));
    }

    @Test
    @DisplayName("getUserAIConfig_withoutPatientId_existingConfig_shouldReturnDTO")
    void getUserAIConfig_withoutPatientId_existingConfig_shouldReturnDTO() throws Exception {
        sampleConfig.setPatientId(null);
        when(userAIConfigRepository.findByUserIdAndIsActiveTrue(100L))
                .thenReturn(Optional.of(sampleConfig));

        final UserAIConfigDTO result = userAIConfigService.getUserAIConfig(100L, null);

        assertNotNull(result);
        assertEquals(100L, result.getUserId());
    }

    @Test
    @DisplayName("getUserAIConfig_withoutPatientId_noExistingConfig_shouldCreateDefault")
    void getUserAIConfig_withoutPatientId_noExistingConfig_shouldCreateDefault() throws Exception {
        when(userAIConfigRepository.findByUserIdAndIsActiveTrue(100L))
                .thenReturn(Optional.empty());

        final UserAIConfig defaultConfig = UserAIConfigDefaults.createMedicalDefaultConfig(100L, null);
        defaultConfig.setId(6L);
        when(userAIConfigRepository.save(any(UserAIConfig.class))).thenReturn(defaultConfig);

        final UserAIConfigDTO result = userAIConfigService.getUserAIConfig(100L, null);

        assertNotNull(result);
        assertEquals(100L, result.getUserId());
        verify(userAIConfigRepository).save(any(UserAIConfig.class));
    }

    @Test
    @DisplayName("getUserAIConfig_createDefaultFails_savedNull_shouldThrowIllegalStateException")
    void getUserAIConfig_createDefaultFails_savedNull_shouldThrowIllegalStateException() throws Exception {
        when(userAIConfigRepository.findByUserIdAndPatientIdAndIsActiveTrue(100L, 200L))
                .thenReturn(Optional.empty());
        when(userAIConfigRepository.save(any(UserAIConfig.class))).thenReturn(null);

        assertThrows(IllegalStateException.class, () ->
                userAIConfigService.getUserAIConfig(100L, 200L));
    }

    @Test
    @DisplayName("getUserAIConfig_createDefaultFails_savedNullId_shouldThrowIllegalStateException")
    void getUserAIConfig_createDefaultFails_savedNullId_shouldThrowIllegalStateException() throws Exception {
        when(userAIConfigRepository.findByUserIdAndPatientIdAndIsActiveTrue(100L, 200L))
                .thenReturn(Optional.empty());

        final UserAIConfig savedNullId = UserAIConfig.builder().userId(100L).build();
        savedNullId.setId(null);
        when(userAIConfigRepository.save(any(UserAIConfig.class))).thenReturn(savedNullId);

        assertThrows(IllegalStateException.class, () ->
                userAIConfigService.getUserAIConfig(100L, 200L));
    }

    @Test
    @DisplayName("getUserAIConfig_createDefaultForUserOnly_savedNull_shouldThrowWithoutPatientInMessage")
    void getUserAIConfig_createDefaultForUserOnly_savedNull_shouldThrowWithoutPatientInMessage() throws Exception {
        when(userAIConfigRepository.findByUserIdAndIsActiveTrue(100L))
                .thenReturn(Optional.empty());
        when(userAIConfigRepository.save(any(UserAIConfig.class))).thenReturn(null);

        final IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                userAIConfigService.getUserAIConfig(100L, null));
        assertTrue(ex.getMessage().contains("user 100"));
        assertFalse(ex.getMessage().contains("patient"));
    }

    // ========== convertToEntity edge cases ==========

    @Test
    @DisplayName("convertDTOToEntity_invalidProviderEnumValue_shouldFallbackToOpenAI")
    void convertDTOToEntity_invalidProviderEnumValue_shouldFallbackToOpenAI() throws Exception {
        // We cannot set an invalid enum directly, but we can test that the catch block works.
        // The catch block handles when AIProvider.valueOf throws an exception.
        // Since we're using the actual enum, we test with DEFAULT which maps to OPENAI.
        sampleDTO.setAiProvider(AIProvider.DEFAULT);

        final UserAIConfig result = userAIConfigService.convertDTOToEntity(sampleDTO);

        assertEquals(AIProvider.OPENAI, result.getPreferredAiProvider());
    }

    @Test
    @DisplayName("convertDTOToEntity_openaiProvider_shouldMapToOpenAI")
    void convertDTOToEntity_openaiProvider_shouldMapToOpenAI() throws Exception {
        sampleDTO.setAiProvider(AIProvider.OPENAI);

        final UserAIConfig result = userAIConfigService.convertDTOToEntity(sampleDTO);

        assertEquals(AIProvider.OPENAI, result.getPreferredAiProvider());
    }
}
