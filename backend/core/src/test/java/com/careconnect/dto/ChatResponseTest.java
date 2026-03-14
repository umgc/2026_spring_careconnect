package com.careconnect.dto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ChatResponseTest {

    // ─── Builder defaults ──────────────────────────────────────────────────────

    @Test
    void builder_default_successIsTrue() throws Exception {
        final ChatResponse response = ChatResponse.builder().build();
        assertThat(response.getSuccess()).isTrue();
    }

    @Test
    void builder_successSetToFalse_returnsFalse() throws Exception {
        final ChatResponse response = ChatResponse.builder().success(false).build();
        assertThat(response.getSuccess()).isFalse();
    }

    // ─── Builder sets all fields correctly ────────────────────────────────────

    @Test
    void builder_allFields_setsCorrectly() throws Exception {
        final LocalDateTime now = LocalDateTime.of(2026, 3, 1, 10, 30);

        final ChatResponse response = ChatResponse.builder()
                .conversationId("conv-42")
                .message("User message")
                .aiResponse("AI reply")
                .messageId(7L)
                .aiProvider("Anthropic")
                .modelUsed("claude-3-sonnet")
                .tokensUsed(150)
                .processingTimeMs(200L)
                .temperatureUsed(0.7)
                .contextIncluded(List.of("vitals", "medications"))
                .isNewConversation(true)
                .timestamp(now)
                .conversationTitle("Health Check")
                .totalMessagesInConversation(5)
                .totalTokensUsedInConversation(800)
                .approachingTokenLimit(false)
                .success(true)
                .errorMessage(null)
                .errorCode(null)
                .build();

        assertThat(response.getConversationId()).isEqualTo("conv-42");
        assertThat(response.getMessage()).isEqualTo("User message");
        assertThat(response.getAiResponse()).isEqualTo("AI reply");
        assertThat(response.getMessageId()).isEqualTo(7L);
        assertThat(response.getAiProvider()).isEqualTo("Anthropic");
        assertThat(response.getModelUsed()).isEqualTo("claude-3-sonnet");
        assertThat(response.getTokensUsed()).isEqualTo(150);
        assertThat(response.getProcessingTimeMs()).isEqualTo(200L);
        assertThat(response.getTemperatureUsed()).isEqualTo(0.7);
        assertThat(response.getContextIncluded()).containsExactly("vitals", "medications");
        assertThat(response.getIsNewConversation()).isTrue();
        assertThat(response.getTimestamp()).isEqualTo(now);
        assertThat(response.getConversationTitle()).isEqualTo("Health Check");
        assertThat(response.getTotalMessagesInConversation()).isEqualTo(5);
        assertThat(response.getTotalTokensUsedInConversation()).isEqualTo(800);
        assertThat(response.getApproachingTokenLimit()).isFalse();
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getErrorMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    // ─── No-arg constructor ───────────────────────────────────────────────────

    @Test
    void noArgConstructor_fieldsAreNull() throws Exception {
        final ChatResponse response = new ChatResponse();
        assertThat(response.getConversationId()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getAiResponse()).isNull();
    }

    // ─── Setters ──────────────────────────────────────────────────────────────

    @Test
    void setters_roundTrip() throws Exception {
        final ChatResponse response = new ChatResponse();

        response.setConversationId("conv-99");
        response.setMessage("Hello");
        response.setAiResponse("Response text");
        response.setMessageId(3L);
        response.setAiProvider("OpenAI");
        response.setModelUsed("gpt-4");
        response.setTokensUsed(300);
        response.setProcessingTimeMs(500L);
        response.setTemperatureUsed(0.9);
        response.setContextIncluded(List.of("notes"));
        response.setIsNewConversation(false);
        response.setTimestamp(LocalDateTime.of(2026, 1, 1, 0, 0));
        response.setConversationTitle("My Chat");
        response.setTotalMessagesInConversation(10);
        response.setTotalTokensUsedInConversation(1200);
        response.setApproachingTokenLimit(true);
        response.setSuccess(false);
        response.setErrorMessage("Something failed");
        response.setErrorCode("ERR_500");

        assertThat(response.getConversationId()).isEqualTo("conv-99");
        assertThat(response.getMessage()).isEqualTo("Hello");
        assertThat(response.getAiResponse()).isEqualTo("Response text");
        assertThat(response.getMessageId()).isEqualTo(3L);
        assertThat(response.getAiProvider()).isEqualTo("OpenAI");
        assertThat(response.getModelUsed()).isEqualTo("gpt-4");
        assertThat(response.getTokensUsed()).isEqualTo(300);
        assertThat(response.getProcessingTimeMs()).isEqualTo(500L);
        assertThat(response.getTemperatureUsed()).isEqualTo(0.9);
        assertThat(response.getContextIncluded()).containsExactly("notes");
        assertThat(response.getIsNewConversation()).isFalse();
        assertThat(response.getTimestamp()).isEqualTo(LocalDateTime.of(2026, 1, 1, 0, 0));
        assertThat(response.getConversationTitle()).isEqualTo("My Chat");
        assertThat(response.getTotalMessagesInConversation()).isEqualTo(10);
        assertThat(response.getTotalTokensUsedInConversation()).isEqualTo(1200);
        assertThat(response.getApproachingTokenLimit()).isTrue();
        assertThat(response.getSuccess()).isFalse();
        assertThat(response.getErrorMessage()).isEqualTo("Something failed");
        assertThat(response.getErrorCode()).isEqualTo("ERR_500");
    }

    // ─── Error scenario ───────────────────────────────────────────────────────

    @Test
    void builder_errorResponse_setsSuccessFalseAndErrorFields() throws Exception {
        final ChatResponse response = ChatResponse.builder()
                .success(false)
                .errorMessage("Service unavailable")
                .errorCode("503")
                .build();

        assertThat(response.getSuccess()).isFalse();
        assertThat(response.getErrorMessage()).isEqualTo("Service unavailable");
        assertThat(response.getErrorCode()).isEqualTo("503");
    }
}
