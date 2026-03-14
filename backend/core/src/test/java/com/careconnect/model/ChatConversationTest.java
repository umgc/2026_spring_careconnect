package com.careconnect.model;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ChatConversationTest {

    // ─── No-arg constructor ───────────────────────────────────────────────────

    @Test
    void noArgConstructor_createsInstance() throws Exception {
        final ChatConversation conv = new ChatConversation();

        assertThat(conv).isNotNull();
        assertThat(conv.getId()).isNull();
        assertThat(conv.getConversationId()).isNull();
        assertThat(conv.getPatientId()).isNull();
        assertThat(conv.getUserId()).isNull();
    }

    // ─── Builder ──────────────────────────────────────────────────────────────

    @Test
    void builder_setsFields() throws Exception {
        final ChatConversation conv = ChatConversation.builder()
                .id(1L)
                .conversationId("conv-uuid-001")
                .patientId(10L)
                .userId(20L)
                .chatType(ChatConversation.ChatType.MEDICAL_CONSULTATION)
                .title("BP Follow-up")
                .aiProviderUsed(UserAIConfig.AIProvider.OPENAI)
                .aiModelUsed("gpt-4")
                .isActive(true)
                .build();

        assertThat(conv.getId()).isEqualTo(1L);
        assertThat(conv.getConversationId()).isEqualTo("conv-uuid-001");
        assertThat(conv.getPatientId()).isEqualTo(10L);
        assertThat(conv.getUserId()).isEqualTo(20L);
        assertThat(conv.getChatType()).isEqualTo(ChatConversation.ChatType.MEDICAL_CONSULTATION);
        assertThat(conv.getTitle()).isEqualTo("BP Follow-up");
        assertThat(conv.getAiProviderUsed()).isEqualTo(UserAIConfig.AIProvider.OPENAI);
        assertThat(conv.getAiModelUsed()).isEqualTo("gpt-4");
        assertThat(conv.getIsActive()).isTrue();
    }

    // ─── Builder defaults ─────────────────────────────────────────────────────

    @Test
    void builder_totalTokensUsed_defaultsToZero() throws Exception {
        final ChatConversation conv = ChatConversation.builder().patientId(1L).userId(1L).build();
        assertThat(conv.getTotalTokensUsed()).isEqualTo(0);
    }

    @Test
    void builder_isActive_defaultsToTrue() throws Exception {
        final ChatConversation conv = ChatConversation.builder().patientId(1L).userId(1L).build();
        assertThat(conv.getIsActive()).isTrue();
    }

    // ─── Setters ──────────────────────────────────────────────────────────────

    @Test
    void setters_updateFields() throws Exception {
        final ChatConversation conv = new ChatConversation();
        final LocalDateTime now = LocalDateTime.now();

        conv.setConversationId("conv-abc");
        conv.setPatientId(5L);
        conv.setUserId(6L);
        conv.setChatType(ChatConversation.ChatType.GENERAL_SUPPORT);
        conv.setTitle("General");
        conv.setAiProviderUsed(UserAIConfig.AIProvider.DEEPSEEK);
        conv.setAiModelUsed("deepseek-chat");
        conv.setTotalTokensUsed(150);
        conv.setIsActive(false);
        conv.setCreatedAt(now);
        conv.setUpdatedAt(now);

        assertThat(conv.getConversationId()).isEqualTo("conv-abc");
        assertThat(conv.getPatientId()).isEqualTo(5L);
        assertThat(conv.getUserId()).isEqualTo(6L);
        assertThat(conv.getChatType()).isEqualTo(ChatConversation.ChatType.GENERAL_SUPPORT);
        assertThat(conv.getTitle()).isEqualTo("General");
        assertThat(conv.getAiProviderUsed()).isEqualTo(UserAIConfig.AIProvider.DEEPSEEK);
        assertThat(conv.getAiModelUsed()).isEqualTo("deepseek-chat");
        assertThat(conv.getTotalTokensUsed()).isEqualTo(150);
        assertThat(conv.getIsActive()).isFalse();
        assertThat(conv.getCreatedAt()).isEqualTo(now);
        assertThat(conv.getUpdatedAt()).isEqualTo(now);
    }

    // ─── @PrePersist: onCreate() ──────────────────────────────────────────────

    @Test
    void onCreate_setsTimestamps() throws Exception {
        final ChatConversation conv = new ChatConversation();

        final Method m = ChatConversation.class.getDeclaredMethod("onCreate");
        m.setAccessible(true);
        m.invoke(conv);

        assertThat(conv.getCreatedAt()).isNotNull();
        assertThat(conv.getUpdatedAt()).isNotNull();
    }

    // ─── @PreUpdate: onUpdate() ───────────────────────────────────────────────

    @Test
    void onUpdate_setsUpdatedAt() throws Exception {
        final ChatConversation conv = new ChatConversation();

        final Method m = ChatConversation.class.getDeclaredMethod("onUpdate");
        m.setAccessible(true);
        m.invoke(conv);

        assertThat(conv.getUpdatedAt()).isNotNull();
    }

    // ─── ChatType enum ────────────────────────────────────────────────────────

    @Test
    void chatTypeEnum_getDisplayName() throws Exception {
        assertThat(ChatConversation.ChatType.MEDICAL_CONSULTATION.getDisplayName()).isEqualTo("Medical Consultation");
        assertThat(ChatConversation.ChatType.GENERAL_SUPPORT.getDisplayName()).isEqualTo("General Support");
        assertThat(ChatConversation.ChatType.MEDICATION_INQUIRY.getDisplayName()).isEqualTo("Medication Inquiry");
        assertThat(ChatConversation.ChatType.MOOD_PAIN_SUPPORT.getDisplayName()).isEqualTo("Mood & Pain Support");
        assertThat(ChatConversation.ChatType.EMERGENCY_GUIDANCE.getDisplayName()).isEqualTo("Emergency Guidance");
        assertThat(ChatConversation.ChatType.LIFESTYLE_ADVICE.getDisplayName()).isEqualTo("Lifestyle Advice");
    }

    @Test
    void chatTypeEnum_containsAllValues() throws Exception {
        assertThat(ChatConversation.ChatType.values()).hasSize(6);
    }
}
