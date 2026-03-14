package com.careconnect.util;

import com.careconnect.model.UserAIConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserAIConfigDefaultsTest {

    // ─── Constants ────────────────────────────────────────────────────────────

    @Test
    void constants_areDefinedAndNotBlank() throws Exception {
        assertThat(UserAIConfigDefaults.MEDICAL_SYSTEM_PROMPT).isNotBlank();
        assertThat(UserAIConfigDefaults.CAREGIVER_SYSTEM_PROMPT).isNotBlank();
        assertThat(UserAIConfigDefaults.GENERIC_SYSTEM_PROMPT).isNotBlank();
        assertThat(UserAIConfigDefaults.DEFAULT_PROVIDER).isNotNull();
        assertThat(UserAIConfigDefaults.DEFAULT_MAX_TOKENS).isEqualTo(2048);
        assertThat(UserAIConfigDefaults.DEFAULT_TEMPERATURE).isEqualTo(0.7);
        assertThat(UserAIConfigDefaults.DEFAULT_CONVERSATION_HISTORY_LIMIT).isEqualTo(10);
    }

    // ─── createMedicalDefaultConfig() ────────────────────────────────────────

    @Test
    void createMedicalDefaultConfig_returnsFullyPopulatedConfig() throws Exception {
        final UserAIConfig config = UserAIConfigDefaults.createMedicalDefaultConfig(1L, 2L);

        assertThat(config).isNotNull();
        assertThat(config.getUserId()).isEqualTo(1L);
        assertThat(config.getPatientId()).isEqualTo(2L);
        assertThat(config.getPreferredAiProvider()).isEqualTo(UserAIConfigDefaults.DEFAULT_PROVIDER);
        assertThat(config.getMaxTokens()).isEqualTo(UserAIConfigDefaults.DEFAULT_MAX_TOKENS);
        assertThat(config.getTemperature()).isEqualTo(UserAIConfigDefaults.DEFAULT_TEMPERATURE);
        assertThat(config.getConversationHistoryLimit()).isEqualTo(UserAIConfigDefaults.DEFAULT_CONVERSATION_HISTORY_LIMIT);
        assertThat(config.getSystemPrompt()).isEqualTo(UserAIConfigDefaults.MEDICAL_SYSTEM_PROMPT);
        assertThat(config.getIncludeVitalsByDefault()).isTrue();
        assertThat(config.getIncludeMedicationsByDefault()).isTrue();
        assertThat(config.getIncludeNotesByDefault()).isTrue();
        assertThat(config.getIncludeMoodPainByDefault()).isTrue();
        assertThat(config.getIncludeAllergiesByDefault()).isTrue();
        assertThat(config.getIsActive()).isTrue();
    }

    // ─── getSystemPrompt() ────────────────────────────────────────────────────

    @Test
    void getSystemPrompt_nullConfig_returnsGenericPrompt() throws Exception {
        assertThat(UserAIConfigDefaults.getSystemPrompt(null))
                .isEqualTo(UserAIConfigDefaults.GENERIC_SYSTEM_PROMPT);
    }

    @Test
    void getSystemPrompt_nullSystemPromptAndNullPatientId_returnsGenericPrompt() throws Exception {
        final UserAIConfig config = new UserAIConfig();
        config.setSystemPrompt(null);
        config.setPatientId(null);

        assertThat(UserAIConfigDefaults.getSystemPrompt(config))
                .isEqualTo(UserAIConfigDefaults.GENERIC_SYSTEM_PROMPT);
    }

    @Test
    void getSystemPrompt_nullSystemPromptAndNonNullPatientId_returnsMedicalPrompt() throws Exception {
        final UserAIConfig config = new UserAIConfig();
        config.setSystemPrompt(null);
        config.setPatientId(42L);

        assertThat(UserAIConfigDefaults.getSystemPrompt(config))
                .isEqualTo(UserAIConfigDefaults.MEDICAL_SYSTEM_PROMPT);
    }

    @Test
    void getSystemPrompt_blankSystemPromptAndNonNullPatientId_returnsMedicalPrompt() throws Exception {
        final UserAIConfig config = new UserAIConfig();
        config.setSystemPrompt("   ");
        config.setPatientId(42L);

        assertThat(UserAIConfigDefaults.getSystemPrompt(config))
                .isEqualTo(UserAIConfigDefaults.MEDICAL_SYSTEM_PROMPT);
    }

    @Test
    void getSystemPrompt_blankSystemPromptAndNullPatientId_returnsGenericPrompt() throws Exception {
        final UserAIConfig config = new UserAIConfig();
        config.setSystemPrompt("   ");
        config.setPatientId(null);

        assertThat(UserAIConfigDefaults.getSystemPrompt(config))
                .isEqualTo(UserAIConfigDefaults.GENERIC_SYSTEM_PROMPT);
    }

    @Test
    void getSystemPrompt_nonBlankSystemPrompt_returnsIt() throws Exception {
        final UserAIConfig config = new UserAIConfig();
        config.setSystemPrompt("Custom system prompt for this user");

        assertThat(UserAIConfigDefaults.getSystemPrompt(config))
                .isEqualTo("Custom system prompt for this user");
    }

    // ─── Constructor ──────────────────────────────────────────────────────────

    @Test
    void constructor_shouldBeInstantiable() throws Exception {
        final UserAIConfigDefaults defaults = new UserAIConfigDefaults();
        assertThat(defaults).isNotNull();
    }
}
