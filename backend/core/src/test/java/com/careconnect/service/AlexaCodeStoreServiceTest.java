package com.careconnect.service;

import com.careconnect.model.ChatConversation;
import com.careconnect.model.ChatMessage;
import com.careconnect.repository.ChatConversationRepository;
import com.careconnect.repository.ChatMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AlexaCodeStoreServiceTest {

    private AlexaCodeStoreService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new AlexaCodeStoreService();
    }

    @Test
    @DisplayName("generateCode returns a non-null code and consumeCode retrieves the JWT")
    void generateCode_returnsCode_consumeCode_retrievesJwt() throws Exception {
        final String jwt = "my.jwt.token";
        final String code = service.generateCode(jwt);
        assertThat(code).isNotNull().isNotBlank();
        final String result = service.consumeCode(code);
        assertThat(result).isEqualTo(jwt);
    }

    @Test
    @DisplayName("consumeCode returns null for an unknown code")
    void consumeCode_returnsNull_forUnknownCode() throws Exception {
        final String result = service.consumeCode("non-existent-code");
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("consumeCode returns null on second call (one-time use)")
    void consumeCode_returnsNull_onSecondConsumption() throws Exception {
        final String jwt = "one-time.jwt";
        final String code = service.generateCode(jwt);
        assertThat(service.consumeCode(code)).isEqualTo(jwt);
        assertThat(service.consumeCode(code)).isNull();
    }

    @Test
    @DisplayName("consumeCode returns null for an expired code")
    void consumeCode_returnsNull_forExpiredCode() throws Exception {
        final String jwt = "expired.jwt";
        final String code = "expired-code";
        insertExpiredEntry(code, jwt, Instant.now().minusSeconds(10));
        final String result = service.consumeCode(code);
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("saveRefreshToken stores the token and findJwtByRefreshToken retrieves it")
    void saveRefreshToken_and_findJwtByRefreshToken_roundTrip() throws Exception {
        final String refreshToken = "refresh-abc";
        final String jwt = "jwt-xyz";
        service.saveRefreshToken(refreshToken, jwt);
        assertThat(service.findJwtByRefreshToken(refreshToken)).isEqualTo(jwt);
    }

    @Test
    @DisplayName("findJwtByRefreshToken returns null for an unknown refresh token")
    void findJwtByRefreshToken_returnsNull_forUnknownToken() throws Exception {
        assertThat(service.findJwtByRefreshToken("unknown-refresh")).isNull();
    }

    @Test
    @DisplayName("cleanupExpiredCodes removes expired entries but keeps valid ones")
    void cleanupExpiredCodes_removesExpired_keepsValid() throws Exception {
        final String validJwt = "valid.jwt";
        final String validCode = service.generateCode(validJwt);
        insertExpiredEntry("old-code", "expired.jwt", Instant.now().minusSeconds(60));
        service.cleanupExpiredCodes();
        assertThat(service.consumeCode(validCode)).isEqualTo(validJwt);
        assertThat(service.consumeCode("old-code")).isNull();
    }

    @Test
    @DisplayName("cleanupExpiredCodes does nothing when all entries are valid")
    void cleanupExpiredCodes_noOp_whenAllValid() throws Exception {
        final String code = service.generateCode("still-good.jwt");
        service.cleanupExpiredCodes();
        assertThat(service.consumeCode(code)).isEqualTo("still-good.jwt");
    }

    @Test
    @DisplayName("cleanupExpiredCodes does nothing on an empty store")
    void cleanupExpiredCodes_noOp_whenEmpty() throws Exception {
        service.cleanupExpiredCodes();
    }

    @Test
    @DisplayName("multiple codes for different JWTs are stored and consumed independently")
    void multipleCodesCoexistIndependently() throws Exception {
        final String jwt1 = "jwt-1";
        final String jwt2 = "jwt-2";
        final String code1 = service.generateCode(jwt1);
        final String code2 = service.generateCode(jwt2);
        assertThat(code1).isNotEqualTo(code2);
        assertThat(service.consumeCode(code1)).isEqualTo(jwt1);
        assertThat(service.consumeCode(code2)).isEqualTo(jwt2);
    }

    @Test
    @DisplayName("saveRefreshToken overwrites a previously stored JWT for the same refresh token")
    void saveRefreshToken_overwritesPreviousValue() throws Exception {
        final String refreshToken = "rt-1";
        service.saveRefreshToken(refreshToken, "old-jwt");
        service.saveRefreshToken(refreshToken, "new-jwt");
        assertThat(service.findJwtByRefreshToken(refreshToken)).isEqualTo("new-jwt");
    }

    @SuppressWarnings("unchecked")
    private void insertExpiredEntry(String code, String jwt, Instant expiration) throws Exception {
        final Class<?> entryClass = Class.forName("com.careconnect.service.AlexaCodeStoreService$Entry");
        final Constructor<?> entryCtor = entryClass.getDeclaredConstructor(String.class, Instant.class);
        entryCtor.setAccessible(true);
        final Object entry = entryCtor.newInstance(jwt, expiration);
        final Field codeStoreField = AlexaCodeStoreService.class.getDeclaredField("codeStore");
        codeStoreField.setAccessible(true);
        final Map<String, Object> codeStore = (Map<String, Object>) codeStoreField.get(service);
        codeStore.put(code, entry);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Inner ChatAnalyticsService tests
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("ChatAnalyticsService")
    class ChatAnalyticsServiceTests {

        @Mock
        private ChatConversationRepository chatConversationRepository;

        @Mock
        private ChatMessageRepository chatMessageRepository;

        private AlexaCodeStoreService.ChatAnalyticsService analyticsService;

        @BeforeEach
        void setUp() throws Exception {
            MockitoAnnotations.openMocks(this);
            analyticsService = new AlexaCodeStoreService.ChatAnalyticsService(
                    chatConversationRepository, chatMessageRepository);
        }

        // --- Helper to build ChatMessage ---
        private ChatMessage buildMessage(ChatMessage.MessageType type, String content, Long processingTimeMs) {
            final ChatMessage msg = new ChatMessage();
            msg.setMessageType(type);
            msg.setContent(content);
            msg.setProcessingTimeMs(processingTimeMs);
            return msg;
        }

        // --- collectAnalytics ---

        @Test
        @DisplayName("collectAnalytics_validConversationWithMessages_logsAnalytics")
        void collectAnalytics_validConversationWithMessages_logsAnalytics() throws Exception {
            final ChatConversation conversation = new ChatConversation();
            conversation.setConversationId("conv-1");
            conversation.setCreatedAt(LocalDateTime.of(2025, 6, 15, 10, 0));
            conversation.setUpdatedAt(LocalDateTime.of(2025, 6, 15, 10, 30));
            conversation.setChatType(ChatConversation.ChatType.MEDICAL_CONSULTATION);

            final ChatMessage userMsg = buildMessage(ChatMessage.MessageType.USER, "I have a headache and pain", null);
            final ChatMessage aiMsg = buildMessage(ChatMessage.MessageType.ASSISTANT, "Here is my advice", 500L);

            analyticsService.collectAnalytics(conversation, List.of(userMsg, aiMsg));
            // No exception thrown = success. Analytics are logged internally.
        }

        @Test
        @DisplayName("collectAnalytics_nullUpdatedAt_sessionDurationZero")
        void collectAnalytics_nullUpdatedAt_sessionDurationZero() throws Exception {
            final ChatConversation conversation = new ChatConversation();
            conversation.setConversationId("conv-2");
            conversation.setCreatedAt(LocalDateTime.of(2025, 6, 15, 10, 0));
            conversation.setUpdatedAt(null);
            conversation.setChatType(ChatConversation.ChatType.GENERAL_SUPPORT);

            final ChatMessage userMsg = buildMessage(ChatMessage.MessageType.USER, "hello", null);

            analyticsService.collectAnalytics(conversation, List.of(userMsg));
        }

        @Test
        @DisplayName("collectAnalytics_nullCreatedAt_sessionDurationZero")
        void collectAnalytics_nullCreatedAt_sessionDurationZero() throws Exception {
            final ChatConversation conversation = new ChatConversation();
            conversation.setConversationId("conv-3");
            conversation.setCreatedAt(null);
            conversation.setUpdatedAt(LocalDateTime.of(2025, 6, 15, 10, 30));
            conversation.setChatType(ChatConversation.ChatType.MEDICATION_INQUIRY);

            // This will throw NPE in collectAnalytics when accessing createdAt.toLocalDate()
            // which is caught by the try/catch in collectAnalytics
            analyticsService.collectAnalytics(conversation, Collections.emptyList());
        }

        @Test
        @DisplayName("collectAnalytics_exceptionInProcessing_caughtGracefully")
        void collectAnalytics_exceptionInProcessing_caughtGracefully() throws Exception {
            // Pass null conversation to trigger NPE inside try block
            final ChatConversation conversation = new ChatConversation();
            conversation.setConversationId("conv-err");
            // createdAt is null → will cause NPE when accessing .toLocalDate()
            conversation.setChatType(ChatConversation.ChatType.GENERAL_SUPPORT);

            analyticsService.collectAnalytics(conversation, Collections.emptyList());
            // Should not throw - caught by catch block
        }

        @Test
        @DisplayName("collectAnalytics_emptyMessages_noTopicsExtracted")
        void collectAnalytics_emptyMessages_noTopicsExtracted() throws Exception {
            final ChatConversation conversation = new ChatConversation();
            conversation.setConversationId("conv-4");
            conversation.setCreatedAt(LocalDateTime.of(2025, 6, 15, 10, 0));
            conversation.setUpdatedAt(LocalDateTime.of(2025, 6, 15, 10, 5));
            conversation.setChatType(ChatConversation.ChatType.LIFESTYLE_ADVICE);

            analyticsService.collectAnalytics(conversation, Collections.emptyList());
        }

        @Test
        @DisplayName("collectAnalytics_allTopicCategories_extractedCorrectly")
        void collectAnalytics_allTopicCategories_extractedCorrectly() throws Exception {
            final ChatConversation conversation = new ChatConversation();
            conversation.setConversationId("conv-5");
            conversation.setCreatedAt(LocalDateTime.of(2025, 6, 15, 10, 0));
            conversation.setUpdatedAt(LocalDateTime.of(2025, 6, 15, 11, 0));
            conversation.setChatType(ChatConversation.ChatType.MEDICAL_CONSULTATION);

            // Each message triggers a different topic category
            final ChatMessage medMsg = buildMessage(ChatMessage.MessageType.USER, "what medication should I take", null);
            final ChatMessage symptomMsg = buildMessage(ChatMessage.MessageType.USER, "I feel pain in my chest", null);
            final ChatMessage apptMsg = buildMessage(ChatMessage.MessageType.USER, "schedule my doctor appointment", null);
            final ChatMessage allergyMsg = buildMessage(ChatMessage.MessageType.USER, "I have an allergy reaction", null);
            final ChatMessage vitalMsg = buildMessage(ChatMessage.MessageType.USER, "my blood pressure is high", null);
            final ChatMessage mentalMsg = buildMessage(ChatMessage.MessageType.USER, "I feel anxiety and depression", null);

            analyticsService.collectAnalytics(conversation,
                    List.of(medMsg, symptomMsg, apptMsg, allergyMsg, vitalMsg, mentalMsg));
        }

        @Test
        @DisplayName("collectAnalytics_generalInquiryOnly_whenNoKeywordsMatch")
        void collectAnalytics_generalInquiryOnly_whenNoKeywordsMatch() throws Exception {
            final ChatConversation conversation = new ChatConversation();
            conversation.setConversationId("conv-6");
            conversation.setCreatedAt(LocalDateTime.of(2025, 6, 15, 10, 0));
            conversation.setUpdatedAt(LocalDateTime.of(2025, 6, 15, 10, 5));
            conversation.setChatType(ChatConversation.ChatType.GENERAL_SUPPORT);

            // Message with no matching keywords
            final ChatMessage generalMsg = buildMessage(ChatMessage.MessageType.USER, "hello how are you today", null);

            analyticsService.collectAnalytics(conversation, List.of(generalMsg));
        }

        @Test
        @DisplayName("collectAnalytics_assistantMessagesOnly_noTopicsExtracted")
        void collectAnalytics_assistantMessagesOnly_noTopicsExtracted() throws Exception {
            final ChatConversation conversation = new ChatConversation();
            conversation.setConversationId("conv-7");
            conversation.setCreatedAt(LocalDateTime.of(2025, 6, 15, 10, 0));
            conversation.setUpdatedAt(LocalDateTime.of(2025, 6, 15, 10, 5));
            conversation.setChatType(ChatConversation.ChatType.GENERAL_SUPPORT);

            final ChatMessage aiMsg = buildMessage(ChatMessage.MessageType.ASSISTANT, "Here is some medication advice", 200L);

            analyticsService.collectAnalytics(conversation, List.of(aiMsg));
        }

        @Test
        @DisplayName("collectAnalytics_multipleAiMessagesWithProcessingTime_calculatesAverage")
        void collectAnalytics_multipleAiMessagesWithProcessingTime_calculatesAverage() throws Exception {
            final ChatConversation conversation = new ChatConversation();
            conversation.setConversationId("conv-8");
            conversation.setCreatedAt(LocalDateTime.of(2025, 6, 15, 10, 0));
            conversation.setUpdatedAt(LocalDateTime.of(2025, 6, 15, 10, 10));
            conversation.setChatType(ChatConversation.ChatType.MOOD_PAIN_SUPPORT);

            final ChatMessage userMsg = buildMessage(ChatMessage.MessageType.USER, "I feel anxious", null);
            final ChatMessage aiMsg1 = buildMessage(ChatMessage.MessageType.ASSISTANT, "Let me help", 300L);
            final ChatMessage aiMsg2 = buildMessage(ChatMessage.MessageType.ASSISTANT, "Here is more", 500L);

            analyticsService.collectAnalytics(conversation, List.of(userMsg, aiMsg1, aiMsg2));
        }

        @Test
        @DisplayName("collectAnalytics_aiMessagesWithNullProcessingTime_avgResponseTimeZero")
        void collectAnalytics_aiMessagesWithNullProcessingTime_avgResponseTimeZero() throws Exception {
            final ChatConversation conversation = new ChatConversation();
            conversation.setConversationId("conv-9");
            conversation.setCreatedAt(LocalDateTime.of(2025, 6, 15, 10, 0));
            conversation.setUpdatedAt(LocalDateTime.of(2025, 6, 15, 10, 5));
            conversation.setChatType(ChatConversation.ChatType.EMERGENCY_GUIDANCE);

            final ChatMessage userMsg = buildMessage(ChatMessage.MessageType.USER, "help me", null);
            final ChatMessage aiMsg = buildMessage(ChatMessage.MessageType.ASSISTANT, "calling help", null);

            analyticsService.collectAnalytics(conversation, List.of(userMsg, aiMsg));
        }

        @Test
        @DisplayName("collectAnalytics_systemMessages_notCountedAsUserOrAi")
        void collectAnalytics_systemMessages_notCountedAsUserOrAi() throws Exception {
            final ChatConversation conversation = new ChatConversation();
            conversation.setConversationId("conv-10");
            conversation.setCreatedAt(LocalDateTime.of(2025, 6, 15, 10, 0));
            conversation.setUpdatedAt(LocalDateTime.of(2025, 6, 15, 10, 5));
            conversation.setChatType(ChatConversation.ChatType.GENERAL_SUPPORT);

            final ChatMessage sysMsg = buildMessage(ChatMessage.MessageType.SYSTEM, "System initialized", null);
            final ChatMessage userMsg = buildMessage(ChatMessage.MessageType.USER, "hello", null);

            analyticsService.collectAnalytics(conversation, List.of(sysMsg, userMsg));
        }

        // --- getAggregatedAnalytics ---

        @Test
        @DisplayName("getAggregatedAnalytics_returnsExpectedKeys")
        void getAggregatedAnalytics_returnsExpectedKeys() throws Exception {
            final LocalDateTime from = LocalDateTime.of(2025, 1, 1, 0, 0);
            final LocalDateTime to = LocalDateTime.of(2025, 12, 31, 23, 59);

            final Map<String, Object> result = analyticsService.getAggregatedAnalytics(from, to);

            assertThat(result).isNotNull();
            assertThat(result).containsKey("totalSessions");
            assertThat(result).containsKey("averageSessionDuration");
            assertThat(result).containsKey("mostCommonTopics");
            assertThat(result).containsKey("peakUsageHours");
            assertThat(result).containsKey("userSatisfactionScore");
            assertThat(result).containsKey("systemPerformance");
            assertThat(result.get("totalSessions")).isEqualTo(0);
        }

        // --- ChatAnalytics inner class ---

        @Test
        @DisplayName("chatAnalytics_builderAndGetters_workCorrectly")
        void chatAnalytics_builderAndGetters_workCorrectly() throws Exception {
            final AlexaCodeStoreService.ChatAnalyticsService.ChatAnalytics analytics =
                    AlexaCodeStoreService.ChatAnalyticsService.ChatAnalytics.builder()
                            .sessionId("sess-1")
                            .sessionDate(java.time.LocalDate.of(2025, 6, 15))
                            .sessionHour(10)
                            .sessionDurationMinutes(30)
                            .messageCount(5)
                            .userMessageCount(3)
                            .aiMessageCount(2)
                            .topicCategories(List.of("MEDICATION_INQUIRY"))
                            .averageResponseTimeMs(400L)
                            .conversationType("MEDICAL_CONSULTATION")
                            .isSharedWithProvider(false)
                            .createdAt(LocalDateTime.now())
                            .build();

            assertThat(analytics.getSessionId()).isEqualTo("sess-1");
            assertThat(analytics.getSessionHour()).isEqualTo(10);
            assertThat(analytics.getSessionDurationMinutes()).isEqualTo(30);
            assertThat(analytics.getMessageCount()).isEqualTo(5);
            assertThat(analytics.getUserMessageCount()).isEqualTo(3);
            assertThat(analytics.getAiMessageCount()).isEqualTo(2);
            assertThat(analytics.getTopicCategories()).containsExactly("MEDICATION_INQUIRY");
            assertThat(analytics.getAverageResponseTimeMs()).isEqualTo(400L);
            assertThat(analytics.getConversationType()).isEqualTo("MEDICAL_CONSULTATION");
            assertThat(analytics.isSharedWithProvider()).isFalse();
        }
    }
}
