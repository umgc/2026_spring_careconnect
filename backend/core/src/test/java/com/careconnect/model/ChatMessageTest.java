package com.careconnect.model;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ChatMessageTest {

    // ─── No-arg constructor ───────────────────────────────────────────────────

    @Test
    void noArgConstructor_createsInstance() throws Exception {
        final ChatMessage msg = new ChatMessage();

        assertThat(msg).isNotNull();
        assertThat(msg.getId()).isNull();
        assertThat(msg.getConversation()).isNull();
        assertThat(msg.getMessageType()).isNull();
        assertThat(msg.getContent()).isNull();
    }

    // ─── Setters / Getters ────────────────────────────────────────────────────

    @Test
    void settersAndGetters_updateFields() throws Exception {
        final ChatMessage msg = new ChatMessage();
        final ChatConversation conv = ChatConversation.builder().id(1L).build();
        final LocalDateTime now = LocalDateTime.now();

        msg.setId(10L);
        msg.setConversation(conv);
        msg.setMessageType(ChatMessage.MessageType.USER);
        msg.setContent("Hello, how are you?");
        msg.setTokensUsed(50);
        msg.setProcessingTimeMs(200L);
        msg.setTemperatureUsed(0.7);
        msg.setContextIncluded("{\"vitals\":true}");
        msg.setAiModelUsed("gpt-4");
        msg.setCreatedAt(now);

        assertThat(msg.getId()).isEqualTo(10L);
        assertThat(msg.getConversation()).isSameAs(conv);
        assertThat(msg.getMessageType()).isEqualTo(ChatMessage.MessageType.USER);
        assertThat(msg.getContent()).isEqualTo("Hello, how are you?");
        assertThat(msg.getTokensUsed()).isEqualTo(50);
        assertThat(msg.getProcessingTimeMs()).isEqualTo(200L);
        assertThat(msg.getTemperatureUsed()).isEqualTo(0.7);
        assertThat(msg.getContextIncluded()).isEqualTo("{\"vitals\":true}");
        assertThat(msg.getAiModelUsed()).isEqualTo("gpt-4");
        assertThat(msg.getCreatedAt()).isEqualTo(now);
    }

    // ─── @PrePersist: onCreate() ──────────────────────────────────────────────

    @Test
    void onCreate_setsCreatedAt() throws Exception {
        final ChatMessage msg = new ChatMessage();

        final Method m = ChatMessage.class.getDeclaredMethod("onCreate");
        m.setAccessible(true);
        m.invoke(msg);

        assertThat(msg.getCreatedAt()).isNotNull();
    }

    // ─── MessageType enum ─────────────────────────────────────────────────────

    @Test
    void messageTypeEnum_getValue() throws Exception {
        assertThat(ChatMessage.MessageType.USER.getValue()).isEqualTo("user");
        assertThat(ChatMessage.MessageType.ASSISTANT.getValue()).isEqualTo("assistant");
        assertThat(ChatMessage.MessageType.SYSTEM.getValue()).isEqualTo("system");
    }

    @Test
    void messageTypeEnum_containsAllValues() throws Exception {
        assertThat(ChatMessage.MessageType.values()).containsExactly(
                ChatMessage.MessageType.USER,
                ChatMessage.MessageType.ASSISTANT,
                ChatMessage.MessageType.SYSTEM
        );
    }
}
