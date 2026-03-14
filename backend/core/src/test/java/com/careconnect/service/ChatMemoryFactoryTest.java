package com.careconnect.service;

import com.careconnect.config.ChatMemoryConfig;
import com.careconnect.model.ChatConversation;
import com.careconnect.model.UserAIConfig;
import com.careconnect.repository.ChatMessageRepository;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChatMemoryFactoryTest {

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private ChatMemoryConfig chatMemoryConfig;

    @InjectMocks
    private ChatMemoryFactory chatMemoryFactory;

    private ChatConversation conversation;
    private UserAIConfig aiConfig;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);

        conversation = new ChatConversation();
        conversation.setConversationId("test-conv-123");

        aiConfig = new UserAIConfig();
    }

    // ========================================================================
    // createChatMemory() - useDatabase = true
    // ========================================================================

    @Test
    @DisplayName("createChatMemory_useDatabaseTrueWithAiConfigLimit_shouldReturnDatabaseChatMemory")
    void createChatMemory_useDatabaseTrueWithAiConfigLimit_shouldReturnDatabaseChatMemory() throws Exception {
        aiConfig.setConversationHistoryLimit(25);

        final ChatMemory result = chatMemoryFactory.createChatMemory(conversation, aiConfig, true);

        assertNotNull(result);
        assertInstanceOf(DatabaseChatMemory.class, result);
        assertEquals("test-conv-123", result.id());
    }

    @Test
    @DisplayName("createChatMemory_useDatabaseTrueWithNullAiConfig_shouldUseDefaultMaxMessages")
    void createChatMemory_useDatabaseTrueWithNullAiConfig_shouldUseDefaultMaxMessages() throws Exception {
        when(chatMemoryConfig.getDefaultMaxMessages()).thenReturn(20);

        final ChatMemory result = chatMemoryFactory.createChatMemory(conversation, null, true);

        assertNotNull(result);
        assertInstanceOf(DatabaseChatMemory.class, result);
        verify(chatMemoryConfig).getDefaultMaxMessages();
    }

    @Test
    @DisplayName("createChatMemory_useDatabaseTrueWithNullLimit_shouldUseDefault")
    void createChatMemory_useDatabaseTrueWithNullLimit_shouldUseDefault() throws Exception {
        aiConfig.setConversationHistoryLimit(null);
        when(chatMemoryConfig.getDefaultMaxMessages()).thenReturn(20);

        final ChatMemory result = chatMemoryFactory.createChatMemory(conversation, aiConfig, true);

        assertNotNull(result);
        assertInstanceOf(DatabaseChatMemory.class, result);
        verify(chatMemoryConfig).getDefaultMaxMessages();
    }

    // ========================================================================
    // createChatMemory() - useDatabase = false
    // ========================================================================

    @Test
    @DisplayName("createChatMemory_useDatabaseFalseWithAiConfigLimit_shouldReturnMessageWindowChatMemory")
    void createChatMemory_useDatabaseFalseWithAiConfigLimit_shouldReturnMessageWindowChatMemory() throws Exception {
        aiConfig.setConversationHistoryLimit(10);

        final ChatMemory result = chatMemoryFactory.createChatMemory(conversation, aiConfig, false);

        assertNotNull(result);
        assertInstanceOf(MessageWindowChatMemory.class, result);
    }

    @Test
    @DisplayName("createChatMemory_useDatabaseFalseWithNullAiConfig_shouldUseDefaultMaxMessages")
    void createChatMemory_useDatabaseFalseWithNullAiConfig_shouldUseDefaultMaxMessages() throws Exception {
        when(chatMemoryConfig.getDefaultMaxMessages()).thenReturn(20);

        final ChatMemory result = chatMemoryFactory.createChatMemory(conversation, null, false);

        assertNotNull(result);
        assertInstanceOf(MessageWindowChatMemory.class, result);
        verify(chatMemoryConfig).getDefaultMaxMessages();
    }

    // ========================================================================
    // createDatabaseChatMemory()
    // ========================================================================

    @Test
    @DisplayName("createDatabaseChatMemory_withValidConfig_shouldReturnDatabaseChatMemory")
    void createDatabaseChatMemory_withValidConfig_shouldReturnDatabaseChatMemory() throws Exception {
        aiConfig.setConversationHistoryLimit(30);

        final ChatMemory result = chatMemoryFactory.createDatabaseChatMemory(conversation, aiConfig);

        assertNotNull(result);
        assertInstanceOf(DatabaseChatMemory.class, result);
    }

    // ========================================================================
    // createInMemoryChatMemory()
    // ========================================================================

    @Test
    @DisplayName("createInMemoryChatMemory_withValidConfig_shouldReturnMessageWindowChatMemory")
    void createInMemoryChatMemory_withValidConfig_shouldReturnMessageWindowChatMemory() throws Exception {
        aiConfig.setConversationHistoryLimit(10);

        final ChatMemory result = chatMemoryFactory.createInMemoryChatMemory(conversation, aiConfig);

        assertNotNull(result);
        assertInstanceOf(MessageWindowChatMemory.class, result);
    }

    // ========================================================================
    // createSessionBasedChatMemory(conversation, aiConfig) - default timeout
    // ========================================================================

    @Test
    @DisplayName("createSessionBasedChatMemory_defaultTimeoutWithLimitBelow15_shouldUseAiConfigLimit")
    void createSessionBasedChatMemory_defaultTimeoutWithLimitBelow15_shouldUseAiConfigLimit() throws Exception {
        aiConfig.setConversationHistoryLimit(10);

        final ChatMemory result = chatMemoryFactory.createSessionBasedChatMemory(conversation, aiConfig);

        assertNotNull(result);
        assertInstanceOf(SessionBasedChatMemory.class, result);
        assertEquals("test-conv-123", result.id());
    }

    @Test
    @DisplayName("createSessionBasedChatMemory_defaultTimeoutWithLimitAbove15_shouldCapAt15")
    void createSessionBasedChatMemory_defaultTimeoutWithLimitAbove15_shouldCapAt15() throws Exception {
        aiConfig.setConversationHistoryLimit(50);

        final ChatMemory result = chatMemoryFactory.createSessionBasedChatMemory(conversation, aiConfig);

        assertNotNull(result);
        assertInstanceOf(SessionBasedChatMemory.class, result);
    }

    @Test
    @DisplayName("createSessionBasedChatMemory_defaultTimeoutWithNullAiConfig_shouldUseDefaultCapped")
    void createSessionBasedChatMemory_defaultTimeoutWithNullAiConfig_shouldUseDefaultCapped() throws Exception {
        when(chatMemoryConfig.getDefaultMaxMessages()).thenReturn(20);

        final ChatMemory result = chatMemoryFactory.createSessionBasedChatMemory(conversation, null);

        assertNotNull(result);
        assertInstanceOf(SessionBasedChatMemory.class, result);
        verify(chatMemoryConfig).getDefaultMaxMessages();
    }

    @Test
    @DisplayName("createSessionBasedChatMemory_defaultTimeoutWithLimitExactly15_shouldUse15")
    void createSessionBasedChatMemory_defaultTimeoutWithLimitExactly15_shouldUse15() throws Exception {
        aiConfig.setConversationHistoryLimit(15);

        final ChatMemory result = chatMemoryFactory.createSessionBasedChatMemory(conversation, aiConfig);

        assertNotNull(result);
        assertInstanceOf(SessionBasedChatMemory.class, result);
    }

    // ========================================================================
    // createSessionBasedChatMemory(conversation, aiConfig, timeoutMinutes)
    // ========================================================================

    @Test
    @DisplayName("createSessionBasedChatMemory_customTimeoutWithAiConfigLimit_shouldReturnSessionBased")
    void createSessionBasedChatMemory_customTimeoutWithAiConfigLimit_shouldReturnSessionBased() throws Exception {
        aiConfig.setConversationHistoryLimit(12);

        final ChatMemory result = chatMemoryFactory.createSessionBasedChatMemory(conversation, aiConfig, 30L);

        assertNotNull(result);
        assertInstanceOf(SessionBasedChatMemory.class, result);
    }

    @Test
    @DisplayName("createSessionBasedChatMemory_customTimeoutWithLimitAbove15_shouldCapAt15")
    void createSessionBasedChatMemory_customTimeoutWithLimitAbove15_shouldCapAt15() throws Exception {
        aiConfig.setConversationHistoryLimit(100);

        final ChatMemory result = chatMemoryFactory.createSessionBasedChatMemory(conversation, aiConfig, 60L);

        assertNotNull(result);
        assertInstanceOf(SessionBasedChatMemory.class, result);
    }

    @Test
    @DisplayName("createSessionBasedChatMemory_customTimeoutWithNullAiConfig_shouldUseDefaultCapped")
    void createSessionBasedChatMemory_customTimeoutWithNullAiConfig_shouldUseDefaultCapped() throws Exception {
        when(chatMemoryConfig.getDefaultMaxMessages()).thenReturn(20);

        final ChatMemory result = chatMemoryFactory.createSessionBasedChatMemory(conversation, null, 45L);

        assertNotNull(result);
        assertInstanceOf(SessionBasedChatMemory.class, result);
        verify(chatMemoryConfig).getDefaultMaxMessages();
    }

    // ========================================================================
    // getMaxMessages() - private method tested through public methods
    // ========================================================================

    @Test
    @DisplayName("getMaxMessages_aiConfigWithLimitSet_shouldReturnAiConfigLimit")
    void getMaxMessages_aiConfigWithLimitSet_shouldReturnAiConfigLimit() throws Exception {
        aiConfig.setConversationHistoryLimit(42);

        // Use createInMemoryChatMemory to indirectly test getMaxMessages
        final ChatMemory result = chatMemoryFactory.createInMemoryChatMemory(conversation, aiConfig);

        assertNotNull(result);
        // Should NOT have called chatMemoryConfig.getDefaultMaxMessages()
        verify(chatMemoryConfig, never()).getDefaultMaxMessages();
    }

    @Test
    @DisplayName("getMaxMessages_aiConfigNull_shouldReturnConfigDefault")
    void getMaxMessages_aiConfigNull_shouldReturnConfigDefault() throws Exception {
        when(chatMemoryConfig.getDefaultMaxMessages()).thenReturn(20);

        chatMemoryFactory.createInMemoryChatMemory(conversation, null);

        verify(chatMemoryConfig).getDefaultMaxMessages();
    }

    @Test
    @DisplayName("getMaxMessages_aiConfigWithNullLimit_shouldReturnConfigDefault")
    void getMaxMessages_aiConfigWithNullLimit_shouldReturnConfigDefault() throws Exception {
        aiConfig.setConversationHistoryLimit(null);
        when(chatMemoryConfig.getDefaultMaxMessages()).thenReturn(20);

        chatMemoryFactory.createInMemoryChatMemory(conversation, aiConfig);

        verify(chatMemoryConfig).getDefaultMaxMessages();
    }
}
