package com.careconnect.service;

import com.careconnect.config.ChatMemoryConfig;
import com.careconnect.model.ChatConversation;
import com.careconnect.model.ChatMessage;
import com.careconnect.repository.ChatConversationRepository;
import com.careconnect.repository.ChatMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class ChatCleanupServiceTest {

    @Mock
    private ChatConversationRepository chatConversationRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private ChatAnalyticsService chatAnalyticsService;

    @Mock
    private ChatMemoryConfig chatMemoryConfig;

    @InjectMocks
    private ChatCleanupService chatCleanupService;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
    }

    // ========================================================================
    // cleanupOldChats()
    // ========================================================================

    @Test
    @DisplayName("cleanupOldChats_autoCleanupDisabled_shouldSkipCleanup")
    void cleanupOldChats_autoCleanupDisabled_shouldSkipCleanup() throws Exception {
        when(chatMemoryConfig.isAutoCleanup()).thenReturn(false);

        chatCleanupService.cleanupOldChats();

        verify(chatConversationRepository, never()).findByCreatedAtBeforeAndIsActiveTrue(any());
    }

    @Test
    @DisplayName("cleanupOldChats_noOldConversations_shouldLogDebugAndNotDelete")
    void cleanupOldChats_noOldConversations_shouldLogDebugAndNotDelete() throws Exception {
        when(chatMemoryConfig.isAutoCleanup()).thenReturn(true);
        when(chatMemoryConfig.getCleanupAfterDays()).thenReturn(30);
        when(chatConversationRepository.findByCreatedAtBeforeAndIsActiveTrue(any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());

        chatCleanupService.cleanupOldChats();

        verify(chatConversationRepository).findByCreatedAtBeforeAndIsActiveTrue(any(LocalDateTime.class));
        verify(chatMessageRepository, never()).findByConversationOrderByCreatedAtAsc(any());
        verify(chatConversationRepository, never()).save(any());
    }

    @Test
    @DisplayName("cleanupOldChats_conversationsWithMessages_shouldCleanUpAll")
    void cleanupOldChats_conversationsWithMessages_shouldCleanUpAll() throws Exception {
        when(chatMemoryConfig.isAutoCleanup()).thenReturn(true);
        when(chatMemoryConfig.getCleanupAfterDays()).thenReturn(30);

        final ChatConversation conversation1 = new ChatConversation();
        conversation1.setConversationId("conv-1");
        conversation1.setIsActive(true);

        final ChatConversation conversation2 = new ChatConversation();
        conversation2.setConversationId("conv-2");
        conversation2.setIsActive(true);

        final List<ChatConversation> oldConversations = Arrays.asList(conversation1, conversation2);
        when(chatConversationRepository.findByCreatedAtBeforeAndIsActiveTrue(any(LocalDateTime.class)))
                .thenReturn(oldConversations);

        final ChatMessage msg1 = new ChatMessage();
        msg1.setId(1L);
        final ChatMessage msg2 = new ChatMessage();
        msg2.setId(2L);
        final List<ChatMessage> messages1 = Arrays.asList(msg1, msg2);

        final ChatMessage msg3 = new ChatMessage();
        msg3.setId(3L);
        final List<ChatMessage> messages2 = Collections.singletonList(msg3);

        when(chatMessageRepository.findByConversationOrderByCreatedAtAsc(conversation1))
                .thenReturn(messages1);
        when(chatMessageRepository.findByConversationOrderByCreatedAtAsc(conversation2))
                .thenReturn(messages2);

        chatCleanupService.cleanupOldChats();

        // Verify analytics were collected for both
        verify(chatAnalyticsService).collectAnalytics(conversation1, messages1);
        verify(chatAnalyticsService).collectAnalytics(conversation2, messages2);

        // Verify messages were deleted
        verify(chatMessageRepository).deleteAll(messages1);
        verify(chatMessageRepository).deleteAll(messages2);

        // Verify conversations were deactivated
        assertFalse(conversation1.getIsActive());
        assertFalse(conversation2.getIsActive());
        verify(chatConversationRepository).save(conversation1);
        verify(chatConversationRepository).save(conversation2);
    }

    @Test
    @DisplayName("cleanupOldChats_conversationWithEmptyMessages_shouldSkipAnalyticsAndDeleteButStillDeactivate")
    void cleanupOldChats_conversationWithEmptyMessages_shouldSkipAnalyticsAndDeleteButStillDeactivate() throws Exception {
        when(chatMemoryConfig.isAutoCleanup()).thenReturn(true);
        when(chatMemoryConfig.getCleanupAfterDays()).thenReturn(30);

        final ChatConversation conversation = new ChatConversation();
        conversation.setConversationId("conv-empty");
        conversation.setIsActive(true);

        when(chatConversationRepository.findByCreatedAtBeforeAndIsActiveTrue(any(LocalDateTime.class)))
                .thenReturn(Collections.singletonList(conversation));
        when(chatMessageRepository.findByConversationOrderByCreatedAtAsc(conversation))
                .thenReturn(Collections.emptyList());

        chatCleanupService.cleanupOldChats();

        // Analytics should NOT be collected (messages empty)
        verify(chatAnalyticsService, never()).collectAnalytics(any(), any());

        // Message deleteAll should NOT be called (messages empty)
        verify(chatMessageRepository, never()).deleteAll(anyList());

        // Conversation should still be deactivated
        assertFalse(conversation.getIsActive());
        verify(chatConversationRepository).save(conversation);
    }

    @Test
    @DisplayName("cleanupOldChats_exceptionThrown_shouldCatchAndNotPropagate")
    void cleanupOldChats_exceptionThrown_shouldCatchAndNotPropagate() throws Exception {
        when(chatMemoryConfig.isAutoCleanup()).thenReturn(true);
        when(chatMemoryConfig.getCleanupAfterDays()).thenReturn(30);
        when(chatConversationRepository.findByCreatedAtBeforeAndIsActiveTrue(any(LocalDateTime.class)))
                .thenThrow(new RuntimeException("Database error"));

        // Should not throw
        assertDoesNotThrow(() -> chatCleanupService.cleanupOldChats());
    }

    // ========================================================================
    // deleteConversationImmediately()
    // ========================================================================

    @Test
    @DisplayName("deleteConversationImmediately_conversationFoundWithMessages_shouldDeleteAndDeactivate")
    void deleteConversationImmediately_conversationFoundWithMessages_shouldDeleteAndDeactivate() throws Exception {
        final String conversationId = "conv-123";
        final ChatConversation conversation = new ChatConversation();
        conversation.setConversationId(conversationId);
        conversation.setIsActive(true);

        when(chatConversationRepository.findByConversationIdAndIsActiveTrue(conversationId))
                .thenReturn(Optional.of(conversation));

        final ChatMessage msg1 = new ChatMessage();
        msg1.setId(1L);
        final ChatMessage msg2 = new ChatMessage();
        msg2.setId(2L);
        final List<ChatMessage> messages = Arrays.asList(msg1, msg2);

        when(chatMessageRepository.findByConversationOrderByCreatedAtAsc(conversation))
                .thenReturn(messages);

        chatCleanupService.deleteConversationImmediately(conversationId);

        verify(chatMessageRepository).deleteAll(messages);
        assertFalse(conversation.getIsActive());
        verify(chatConversationRepository).save(conversation);
    }

    @Test
    @DisplayName("deleteConversationImmediately_conversationFoundNoMessages_shouldDeactivateOnly")
    void deleteConversationImmediately_conversationFoundNoMessages_shouldDeactivateOnly() throws Exception {
        final String conversationId = "conv-empty";
        final ChatConversation conversation = new ChatConversation();
        conversation.setConversationId(conversationId);
        conversation.setIsActive(true);

        when(chatConversationRepository.findByConversationIdAndIsActiveTrue(conversationId))
                .thenReturn(Optional.of(conversation));
        when(chatMessageRepository.findByConversationOrderByCreatedAtAsc(conversation))
                .thenReturn(Collections.emptyList());

        chatCleanupService.deleteConversationImmediately(conversationId);

        // Should not call deleteAll when messages are empty
        verify(chatMessageRepository, never()).deleteAll(anyList());

        // Should still deactivate conversation
        assertFalse(conversation.getIsActive());
        verify(chatConversationRepository).save(conversation);
    }

    @Test
    @DisplayName("deleteConversationImmediately_conversationNotFound_shouldDoNothing")
    void deleteConversationImmediately_conversationNotFound_shouldDoNothing() throws Exception {
        final String conversationId = "conv-nonexistent";

        when(chatConversationRepository.findByConversationIdAndIsActiveTrue(conversationId))
                .thenReturn(Optional.empty());

        chatCleanupService.deleteConversationImmediately(conversationId);

        verify(chatMessageRepository, never()).findByConversationOrderByCreatedAtAsc(any());
        verify(chatMessageRepository, never()).deleteAll(anyList());
        verify(chatConversationRepository, never()).save(any());
    }

    @Test
    @DisplayName("deleteConversationImmediately_exceptionThrown_shouldWrapAndRethrowAsRuntimeException")
    void deleteConversationImmediately_exceptionThrown_shouldWrapAndRethrowAsRuntimeException() throws Exception {
        final String conversationId = "conv-error";

        when(chatConversationRepository.findByConversationIdAndIsActiveTrue(conversationId))
                .thenThrow(new RuntimeException("Database error"));

        final RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> chatCleanupService.deleteConversationImmediately(conversationId));

        assertEquals("Failed to delete conversation", thrown.getMessage());
        assertNotNull(thrown.getCause());
    }

    // ========================================================================
    // getRetentionPolicyInfo()
    // ========================================================================

    @Test
    @DisplayName("getRetentionPolicyInfo_autoCleanupEnabled_shouldReturnInfoWithDays")
    void getRetentionPolicyInfo_autoCleanupEnabled_shouldReturnInfoWithDays() throws Exception {
        when(chatMemoryConfig.isAutoCleanup()).thenReturn(true);
        when(chatMemoryConfig.getCleanupAfterDays()).thenReturn(30);

        final String result = chatCleanupService.getRetentionPolicyInfo();

        assertTrue(result.contains("30 days"));
        assertTrue(result.contains("automatically deleted"));
        assertTrue(result.contains("anonymized usage statistics"));
    }

    @Test
    @DisplayName("getRetentionPolicyInfo_autoCleanupDisabled_shouldReturnDisabledMessage")
    void getRetentionPolicyInfo_autoCleanupDisabled_shouldReturnDisabledMessage() throws Exception {
        when(chatMemoryConfig.isAutoCleanup()).thenReturn(false);

        final String result = chatCleanupService.getRetentionPolicyInfo();

        assertTrue(result.contains("Automatic chat deletion is currently disabled"));
        assertTrue(result.contains("manually delete conversations"));
        assertTrue(result.contains("anonymized usage statistics"));
    }

    @Test
    @DisplayName("getRetentionPolicyInfo_autoCleanupEnabledCustomDays_shouldReturnCorrectDayCount")
    void getRetentionPolicyInfo_autoCleanupEnabledCustomDays_shouldReturnCorrectDayCount() throws Exception {
        when(chatMemoryConfig.isAutoCleanup()).thenReturn(true);
        when(chatMemoryConfig.getCleanupAfterDays()).thenReturn(7);

        final String result = chatCleanupService.getRetentionPolicyInfo();

        assertTrue(result.contains("7 days"));
    }
}
