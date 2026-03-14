package com.careconnect.dto;

import com.careconnect.model.UserAIConfig.AIProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class UserAIConfigDTOTest {

    // ─── No-arg constructor ───────────────────────────────────────────────────

    @Test
    void noArgConstructor_createsInstance() throws Exception {
        final UserAIConfigDTO dto = new UserAIConfigDTO();

        assertThat(dto).isNotNull();
        assertThat(dto.getId()).isNull();
        assertThat(dto.getPatientId()).isNull();
        assertThat(dto.getUserId()).isNull();
        assertThat(dto.getAiProvider()).isNull();
    }

    // ─── Builder: defaults applied ────────────────────────────────────────────

    @Test
    void builder_defaults_areApplied() throws Exception {
        final UserAIConfigDTO dto = UserAIConfigDTO.builder()
                .userId(1L)
                .aiProvider(AIProvider.OPENAI)
                .build();

        assertThat(dto.getOpenaiModel()).isEqualTo("gpt-4");
        assertThat(dto.getDeepseekModel()).isEqualTo("deepseek-chat");
        assertThat(dto.getMaxTokens()).isEqualTo(2000);
        assertThat(dto.getTemperature()).isEqualTo(0.7);
        assertThat(dto.getConversationHistoryLimit()).isEqualTo(20);
        assertThat(dto.getIncludeVitalsByDefault()).isTrue();
        assertThat(dto.getIncludeMedicationsByDefault()).isTrue();
        assertThat(dto.getIncludeNotesByDefault()).isTrue();
        assertThat(dto.getIncludeMoodPainLogsByDefault()).isTrue();
        assertThat(dto.getIncludeAllergiesByDefault()).isTrue();
        assertThat(dto.getIsActive()).isTrue();
    }

    // ─── Builder: all fields overridden ──────────────────────────────────────

    @Test
    void builder_allFieldsOverridden_setsCorrectly() throws Exception {
        final UserAIConfigDTO dto = UserAIConfigDTO.builder()
                .id(10L)
                .patientId(20L)
                .userId(30L)
                .aiProvider(AIProvider.DEEPSEEK)
                .openaiModel("gpt-3.5-turbo")
                .deepseekModel("deepseek-coder")
                .maxTokens(4000)
                .temperature(1.0)
                .conversationHistoryLimit(50)
                .includeVitalsByDefault(false)
                .includeMedicationsByDefault(false)
                .includeNotesByDefault(false)
                .includeMoodPainLogsByDefault(false)
                .includeAllergiesByDefault(false)
                .isActive(false)
                .systemPrompt("You are a helpful assistant.")
                .build();

        assertThat(dto.getId()).isEqualTo(10L);
        assertThat(dto.getPatientId()).isEqualTo(20L);
        assertThat(dto.getUserId()).isEqualTo(30L);
        assertThat(dto.getAiProvider()).isEqualTo(AIProvider.DEEPSEEK);
        assertThat(dto.getOpenaiModel()).isEqualTo("gpt-3.5-turbo");
        assertThat(dto.getDeepseekModel()).isEqualTo("deepseek-coder");
        assertThat(dto.getMaxTokens()).isEqualTo(4000);
        assertThat(dto.getTemperature()).isEqualTo(1.0);
        assertThat(dto.getConversationHistoryLimit()).isEqualTo(50);
        assertThat(dto.getIncludeVitalsByDefault()).isFalse();
        assertThat(dto.getIncludeMedicationsByDefault()).isFalse();
        assertThat(dto.getIncludeNotesByDefault()).isFalse();
        assertThat(dto.getIncludeMoodPainLogsByDefault()).isFalse();
        assertThat(dto.getIncludeAllergiesByDefault()).isFalse();
        assertThat(dto.getIsActive()).isFalse();
        assertThat(dto.getSystemPrompt()).isEqualTo("You are a helpful assistant.");
    }

    // ─── Builder static method ────────────────────────────────────────────────

    @Test
    void builder_staticMethod_returnsBuilderInstance() throws Exception {
        final UserAIConfigDTO.UserAIConfigDTOBuilder builder = UserAIConfigDTO.builder();
        assertThat(builder).isNotNull();
    }

    // ─── Setters ──────────────────────────────────────────────────────────────

    @Test
    void setters_updateAllFields() throws Exception {
        final UserAIConfigDTO dto = new UserAIConfigDTO();

        dto.setId(1L);
        dto.setPatientId(2L);
        dto.setUserId(3L);
        dto.setAiProvider(AIProvider.OPENAI);
        dto.setOpenaiModel("gpt-4");
        dto.setDeepseekModel("deepseek-chat");
        dto.setMaxTokens(3000);
        dto.setTemperature(0.5);
        dto.setConversationHistoryLimit(30);
        dto.setIncludeVitalsByDefault(true);
        dto.setIncludeMedicationsByDefault(true);
        dto.setIncludeNotesByDefault(false);
        dto.setIncludeMoodPainLogsByDefault(false);
        dto.setIncludeAllergiesByDefault(true);
        dto.setIsActive(true);
        dto.setSystemPrompt("Custom prompt");

        assertThat(dto.getId()).isEqualTo(1L);
        assertThat(dto.getPatientId()).isEqualTo(2L);
        assertThat(dto.getUserId()).isEqualTo(3L);
        assertThat(dto.getAiProvider()).isEqualTo(AIProvider.OPENAI);
        assertThat(dto.getOpenaiModel()).isEqualTo("gpt-4");
        assertThat(dto.getDeepseekModel()).isEqualTo("deepseek-chat");
        assertThat(dto.getMaxTokens()).isEqualTo(3000);
        assertThat(dto.getTemperature()).isEqualTo(0.5);
        assertThat(dto.getConversationHistoryLimit()).isEqualTo(30);
        assertThat(dto.getIncludeVitalsByDefault()).isTrue();
        assertThat(dto.getIncludeMedicationsByDefault()).isTrue();
        assertThat(dto.getIncludeNotesByDefault()).isFalse();
        assertThat(dto.getIncludeMoodPainLogsByDefault()).isFalse();
        assertThat(dto.getIncludeAllergiesByDefault()).isTrue();
        assertThat(dto.getIsActive()).isTrue();
        assertThat(dto.getSystemPrompt()).isEqualTo("Custom prompt");
    }
}
