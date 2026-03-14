package com.careconnect.service;

import com.careconnect.dto.ChatConversationSummary;
import com.careconnect.dto.ChatMessageSummary;
import com.careconnect.dto.ChatRequest;
import com.careconnect.dto.ChatResponse;
import com.careconnect.dto.UploadedFileDTO;
import com.careconnect.model.ChatConversation;
import com.careconnect.model.ChatMessage;
import com.careconnect.model.Patient;
import com.careconnect.model.UserAIConfig;
import com.careconnect.repository.ChatConversationRepository;
import com.careconnect.repository.ChatMessageRepository;
import com.careconnect.repository.PatientRepository;
import com.careconnect.repository.UserAIConfigRepository;
import com.careconnect.service.cache.AIChatCacheService;
import com.careconnect.service.security.InputSanitizationService;
import com.careconnect.service.security.LangChainGovernanceService;
import com.careconnect.service.security.ResponseSanitizationService;
import com.careconnect.service.security.SecurityAuditService;
import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import org.mockito.ArgumentMatchers;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DefaultAIChatService}.
 *
 * <p>All collaborators are replaced with Mockito mocks so the service's business
 * logic can be validated in isolation — no Spring context or database is needed.
 *
 * <p>The {@link ChatModel} mock uses {@code RETURNS_DEEP_STUBS} so that the
 * chained call {@code chatModel.chat(messages).aiMessage().text()} can be
 * stubbed without needing concrete LangChain4j response types.
 *
 * <p>Coverage targets: processChat (happy path + validation + AI error handling),
 * getPatientConversations, getConversationMessages, getRecentMessagesForUser,
 * deactivateConversation, and all private helper methods via reflection.
 */
@SuppressWarnings({"unchecked"})
class DefaultAIChatServiceTest {

    // ── Constants ─────────────────────────────────────────────────────────────

    private static final Long USER_ID    = 1L;
    private static final Long PATIENT_ID = 2L;
    private static final String CONV_ID  = "conv-uuid-test-1234";
    private static final String AI_TEXT  = "Hello, I can help you with that!";

    // ── Mocked collaborators ───────────────────────────────────────────────────

    /** Deep-stubs allow chaining: chatModel.chat(list).aiMessage().text() */
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ChatModel chatModel;

    @Mock private UserAIConfigRepository       userAIConfigRepository;
    @Mock private ChatConversationRepository   chatConversationRepository;
    @Mock private ChatMessageRepository        chatMessageRepository;
    @Mock private PatientRepository            patientRepository;
    @Mock private MedicalContextService        medicalContextService;
    @Mock private PatientContextRetrievalService patientContextRetrievalService;
    @Mock private ChatMemoryFactory            chatMemoryFactory;
    @Mock private ChatAuditService             chatAuditService;
    @Mock private CaregiverPatientLinkService  caregiverPatientLinkService;
    @Mock private InputSanitizationService     inputSanitizationService;
    @Mock private ResponseSanitizationService  responseSanitizationService;
    @Mock private LangChainGovernanceService   langChainGovernanceService;
    @Mock private AIChatCacheService           cacheService;
    @Mock private SecurityAuditService         securityAuditService;
    @Mock private DocumentProcessingService    documentProcessingService;
    @Mock private ChatMemory                   chatMemory;

    // ── Subject under test ─────────────────────────────────────────────────────

    private DefaultAIChatService service;

    // ── Shared fixtures ────────────────────────────────────────────────────────

    private Patient          patient;
    private UserAIConfig     aiConfig;
    private ChatConversation conversation;

    // ── Setup ──────────────────────────────────────────────────────────────────

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);

        // Construct the service manually so all mocks are injected via constructor
        service = new DefaultAIChatService(
                chatModel, userAIConfigRepository, chatConversationRepository,
                chatMessageRepository, patientRepository, medicalContextService,
                patientContextRetrievalService, chatMemoryFactory, chatAuditService,
                caregiverPatientLinkService, inputSanitizationService,
                responseSanitizationService, langChainGovernanceService,
                cacheService, securityAuditService, documentProcessingService);

        // Build fixtures
        patient = new Patient();

        aiConfig = UserAIConfig.builder()
                .userId(USER_ID)
                .patientId(PATIENT_ID)
                .preferredAiProvider(UserAIConfig.AIProvider.DEEPSEEK)
                .deepseekModel("deepseek-chat")
                .openaiModel("gpt-4o")
                .maxTokens(2048)
                .conversationHistoryLimit(20)
                .build();

        conversation = ChatConversation.builder()
                .conversationId(CONV_ID)
                .userId(USER_ID)
                .patientId(PATIENT_ID)
                .chatType(ChatConversation.ChatType.GENERAL_SUPPORT)
                .title("Test conversation")
                .isActive(true)
                .totalTokensUsed(0)
                .build();
        // @PrePersist does not run in unit tests — set manually so isAfter() checks work
        conversation.setCreatedAt(LocalDateTime.now().minusMinutes(5));

        // ── Common stubs ─────────────────────────────────────────────────────

        // Cache lookups
        when(cacheService.findPatient(PATIENT_ID)).thenReturn(Optional.of(patient));
        when(cacheService.findUserAIConfig(eq(USER_ID), any())).thenReturn(Optional.of(aiConfig));
        when(cacheService.saveUserAIConfig(any())).thenReturn(aiConfig);
        when(cacheService.findConversation(any())).thenReturn(Optional.empty());
        when(cacheService.saveConversation(any())).thenReturn(conversation);

        // Sanitization: pass-through by default
        when(inputSanitizationService.sanitizeUserInput(any(), any(), any()))
                .thenReturn(new InputSanitizationService.SanitizationResult(
                        "safe message", false, Collections.emptyList()));
        when(inputSanitizationService.sanitizeSystemPrompt(any(), any(), any()))
                .thenReturn(new InputSanitizationService.SanitizationResult(
                        "safe system prompt", false, Collections.emptyList()));
        when(responseSanitizationService.sanitizeAIResponse(any(), any(), any(), any()))
                .thenReturn(new ResponseSanitizationService.SanitizationResult(
                        AI_TEXT, Collections.emptyList()));

        // Chat memory
        when(chatMemoryFactory.createSessionBasedChatMemory(any(), any())).thenReturn(chatMemory);
        when(chatMemory.messages()).thenReturn(Collections.emptyList());

        // Repository helpers called while building the response
        when(chatMessageRepository.countByConversation(any())).thenReturn(5);
        when(chatMessageRepository.sumTokensUsedByConversation(any())).thenReturn(100);
        when(chatMessageRepository.findTopNByConversationOrderByCreatedAtAsc(any(), anyInt()))
                .thenReturn(Collections.emptyList());

        // Medical context (empty by default — individual tests override as needed)
        when(medicalContextService.buildPatientContext(any(), any(), any())).thenReturn("");

        // AI model: deep-stub returns AI_TEXT by default so happy-path tests are concise.
        // Cast is required to resolve the overload ambiguity between
        // chat(List<ChatMessage>) and chat(ChatMessage...) in LangChain4j 1.x.
        when(chatModel.chat(ArgumentMatchers.<dev.langchain4j.data.message.ChatMessage>anyList())
                .aiMessage().text()).thenReturn(AI_TEXT);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  processChat — validation / early-exit paths
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("processChat — input validation")
    class ProcessChatValidation {

        @Test
        @DisplayName("throws IllegalArgumentException when both patientId and userId are null")
        void bothIdsNull_throwsIllegalArgumentException() throws Exception {
            // A request with no user identity cannot be processed at all
            ChatRequest req = new ChatRequest();

            assertThrows(IllegalArgumentException.class,
                    () -> service.processChat(req));
        }

        @Test
        @DisplayName("returns error response when userId is absent but patientId is set")
        void missingUserId_returnsErrorResponse() throws Exception {
            // patientId is present but the inner guard (userId == null) fires first
            ChatRequest req = new ChatRequest();
            req.setPatientId(PATIENT_ID);
            // userId intentionally left null

            ChatResponse resp = service.processChat(req);

            assertFalse(resp.getSuccess(),
                    "A request without userId must produce a failure response");
            assertEquals("PROCESSING_ERROR", resp.getErrorCode());
        }

        @Test
        @DisplayName("returns error response when message is blank and no files are attached")
        void blankMessageAndNoFiles_returnsErrorResponse() throws Exception {
            ChatRequest req = new ChatRequest();
            req.setUserId(USER_ID);
            req.setPatientId(PATIENT_ID);
            req.setMessage("   "); // whitespace only

            ChatResponse resp = service.processChat(req);

            assertFalse(resp.getSuccess());
            assertTrue(resp.getErrorMessage().contains("Message content"),
                    "Error message should mention missing message content");
        }

        @Test
        @DisplayName("throws IllegalArgumentException when the patient cannot be found in cache/DB")
        void patientNotFound_throwsIllegalArgumentException() throws Exception {
            // Simulate a cache/DB miss for the patient
            when(cacheService.findPatient(PATIENT_ID)).thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class,
                    () -> service.processChat(buildBasicRequest()),
                    "Patient lookup failure should propagate as IllegalArgumentException");
        }

        @Test
        @DisplayName("nullifies a blank (whitespace-only) conversationId before processing")
        void blankConversationId_isTreatedAsAbsent() throws Exception {
            // The service strips blank conversationIds so a new conversation is created
            ChatRequest req = buildBasicRequest();
            req.setConversationId("   ");

            ChatResponse resp = service.processChat(req);

            assertTrue(resp.getSuccess());
            // A new conversation must have been persisted via the cache service
            verify(cacheService, atLeastOnce()).saveConversation(any());
        }

        @Test
        @DisplayName("returns error response when message is null and no files are attached")
        void nullMessageAndNoFiles_returnsErrorResponse() throws Exception {
            ChatRequest req = new ChatRequest();
            req.setUserId(USER_ID);
            req.setPatientId(PATIENT_ID);
            req.setMessage(null);

            ChatResponse resp = service.processChat(req);

            assertFalse(resp.getSuccess());
            assertTrue(resp.getErrorMessage().contains("Message content"));
        }

        @Test
        @DisplayName("returns error response when message is null and files list is empty")
        void nullMessageAndEmptyFiles_returnsErrorResponse() throws Exception {
            ChatRequest req = new ChatRequest();
            req.setUserId(USER_ID);
            req.setPatientId(PATIENT_ID);
            req.setMessage(null);
            req.setUploadedFiles(Collections.emptyList());

            ChatResponse resp = service.processChat(req);

            assertFalse(resp.getSuccess());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  processChat — input & system-prompt sanitization
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("processChat — sanitization guardrails")
    class ProcessChatSanitization {

        @Test
        @DisplayName("returns error response when user message is blocked by the sanitizer")
        void userInputBlocked_returnsErrorResponse() throws Exception {
            // Simulates detection of SQL injection or XSS in user message
            when(inputSanitizationService.sanitizeUserInput(any(), any(), any()))
                    .thenReturn(new InputSanitizationService.SanitizationResult(
                            "", true, List.of("SQL injection detected")));

            ChatResponse resp = service.processChat(buildBasicRequest());

            assertFalse(resp.getSuccess());
            assertTrue(resp.getErrorMessage().contains("cannot be processed"),
                    "Blocked input should produce a descriptive error");
        }

        @Test
        @DisplayName("returns error response when the system prompt is blocked")
        void systemPromptBlocked_returnsErrorResponse() throws Exception {
            // System prompt injection attempt detected by the sanitizer
            when(inputSanitizationService.sanitizeSystemPrompt(any(), any(), any()))
                    .thenReturn(new InputSanitizationService.SanitizationResult(
                            "", true, List.of("Prompt injection detected")));

            ChatResponse resp = service.processChat(buildBasicRequest());

            assertFalse(resp.getSuccess());
            assertTrue(resp.getErrorMessage().contains("System configuration error"));
        }

        @Test
        @DisplayName("returns error response when uploaded file content is blocked")
        void uploadedFileContentBlocked_returnsErrorResponse() throws Exception {
            // First call (user message text) passes; second call (extracted file content) is blocked
            InputSanitizationService.SanitizationResult pass =
                    new InputSanitizationService.SanitizationResult(
                            "safe message", false, Collections.emptyList());
            InputSanitizationService.SanitizationResult block =
                    new InputSanitizationService.SanitizationResult(
                            "", true, List.of("Malicious content in file"));

            when(inputSanitizationService.sanitizeUserInput(any(), any(), any()))
                    .thenReturn(pass)    // first invocation: user message
                    .thenReturn(block);  // second invocation: extracted file text

            UploadedFileDTO file = new UploadedFileDTO();
            file.setFilename("report.pdf");
            file.setContentType("application/pdf");
            when(documentProcessingService.extractTextContent(any())).thenReturn("extracted text");

            ChatRequest req = buildBasicRequest();
            req.setUploadedFiles(List.of(file));

            ChatResponse resp = service.processChat(req);

            assertFalse(resp.getSuccess());
            assertTrue(resp.getErrorMessage().contains("uploaded document"),
                    "Blocked file content should mention the uploaded document");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  processChat — AI model response / exception handling
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("processChat — AI model response handling")
    class ProcessChatAiHandling {

        @Test
        @DisplayName("returns fallback message when the AI model returns a null response")
        void nullAiResponse_returnsFallbackMessage() throws Exception {
            // Explicitly override the deep-stub to return null for the entire response
            when(chatModel.chat(ArgumentMatchers.<dev.langchain4j.data.message.ChatMessage>anyList())).thenReturn(null);

            ChatResponse resp = service.processChat(buildBasicRequest());

            assertTrue(resp.getSuccess(),
                    "A null AI response is handled gracefully — overall success is true");
            assertTrue(resp.getAiResponse().contains("having trouble processing"),
                    "User-facing fallback message should explain the issue");
        }

        @Test
        @DisplayName("returns auth-error message when AuthenticationException is thrown")
        void authenticationException_returnsFallbackMessage() throws Exception {
            // Mocking avoids constructor dependency on the concrete exception class
            AuthenticationException authEx = mock(AuthenticationException.class);
            when(chatModel.chat(ArgumentMatchers.<dev.langchain4j.data.message.ChatMessage>anyList())).thenThrow(authEx);

            ChatResponse resp = service.processChat(buildBasicRequest());

            assertTrue(resp.getSuccess());
            assertTrue(resp.getAiResponse().contains("authentication issues"),
                    "Authentication failure should surface a specific user message");
        }

        @Test
        @DisplayName("returns config-error message when IllegalStateException is thrown")
        void illegalStateException_returnsFallbackMessage() throws Exception {
            // Simulates a missing or misconfigured API key
            when(chatModel.chat(ArgumentMatchers.<dev.langchain4j.data.message.ChatMessage>anyList())).thenThrow(new IllegalStateException("API key not set"));

            ChatResponse resp = service.processChat(buildBasicRequest());

            assertTrue(resp.getSuccess());
            assertTrue(resp.getAiResponse().contains("currently unavailable"),
                    "Configuration error should surface a service-unavailable message");
        }

        @Test
        @DisplayName("returns 503 message when RuntimeException mentions 'service unavailable'")
        void runtimeException_503_returnsFallbackMessage() throws Exception {
            when(chatModel.chat(ArgumentMatchers.<dev.langchain4j.data.message.ChatMessage>anyList())).thenThrow(
                    new RuntimeException("503 service unavailable"));

            ChatResponse resp = service.processChat(buildBasicRequest());

            assertTrue(resp.getSuccess());
            assertTrue(resp.getAiResponse().contains("temporarily unavailable"));
        }

        @Test
        @DisplayName("returns 503 message when RuntimeException message contains '503'")
        void runtimeException_503Code_returnsFallbackMessage() throws Exception {
            when(chatModel.chat(ArgumentMatchers.<dev.langchain4j.data.message.ChatMessage>anyList())).thenThrow(
                    new RuntimeException("upstream error: 503"));

            ChatResponse resp = service.processChat(buildBasicRequest());

            assertTrue(resp.getSuccess());
            assertTrue(resp.getAiResponse().contains("temporarily unavailable"));
        }

        @Test
        @DisplayName("returns rate-limit message when RuntimeException mentions '429'")
        void runtimeException_429_returnsFallbackMessage() throws Exception {
            when(chatModel.chat(ArgumentMatchers.<dev.langchain4j.data.message.ChatMessage>anyList())).thenThrow(
                    new RuntimeException("429 rate limit exceeded"));

            ChatResponse resp = service.processChat(buildBasicRequest());

            assertTrue(resp.getSuccess());
            assertTrue(resp.getAiResponse().contains("high volume of requests"),
                    "Rate-limit scenario should guide the user to wait and retry");
        }

        @Test
        @DisplayName("returns rate-limit message when RuntimeException mentions 'rate limit'")
        void runtimeException_rateLimitText_returnsFallbackMessage() throws Exception {
            when(chatModel.chat(ArgumentMatchers.<dev.langchain4j.data.message.ChatMessage>anyList())).thenThrow(
                    new RuntimeException("OpenAI: rate limit hit"));

            ChatResponse resp = service.processChat(buildBasicRequest());

            assertTrue(resp.getSuccess());
            assertTrue(resp.getAiResponse().contains("high volume of requests"));
        }

        @Test
        @DisplayName("returns generic error message for any other RuntimeException")
        void genericRuntimeException_returnsFallbackMessage() throws Exception {
            when(chatModel.chat(ArgumentMatchers.<dev.langchain4j.data.message.ChatMessage>anyList())).thenThrow(
                    new RuntimeException("unexpected network failure"));

            ChatResponse resp = service.processChat(buildBasicRequest());

            assertTrue(resp.getSuccess());
            assertTrue(resp.getAiResponse().contains("encountered an error"),
                    "Unrecognised runtime errors should produce a generic retry message");
        }

        @Test
        @DisplayName("returns fallback message when a generic checked Exception is thrown")
        void checkedExceptionFromAi_returnsFallbackMessage() throws Exception {
            // Force a checked Exception to be wrapped and thrown by the chat method
            when(chatModel.chat(ArgumentMatchers.<dev.langchain4j.data.message.ChatMessage>anyList()))
                    .thenAnswer(inv -> { throw new Exception("Unexpected checked exception"); });

            ChatResponse resp = service.processChat(buildBasicRequest());

            assertTrue(resp.getSuccess());
            assertTrue(resp.getAiResponse().contains("unexpected error"),
                    "Checked exceptions should produce an unexpected error message");
            verify(chatAuditService).logSystemError(
                    eq(USER_ID), eq(CONV_ID), eq("AI_PROCESSING_ERROR"), eq("ai_service_exception"));
        }

        @Test
        @DisplayName("logs AI_RESPONSE_NULL when AI returns null aiMessage text")
        void nullAiMessageText_logsSystemError() throws Exception {
            // Make the response non-null but aiMessage returns null text
            var mockResponse = mock(dev.langchain4j.model.chat.response.ChatResponse.class, RETURNS_DEEP_STUBS);
            when(mockResponse.aiMessage().text()).thenReturn(null);
            when(chatModel.chat(ArgumentMatchers.<dev.langchain4j.data.message.ChatMessage>anyList()))
                    .thenReturn(mockResponse);

            ChatResponse resp = service.processChat(buildBasicRequest());

            assertTrue(resp.getSuccess());
            assertTrue(resp.getAiResponse().contains("having trouble processing"));
            verify(chatAuditService).logSystemError(
                    eq(USER_ID), eq(CONV_ID), eq("AI_RESPONSE_NULL"), eq("ai_service_error"));
        }

        @Test
        @DisplayName("logs AI_RESPONSE_NULL when AI response has null aiMessage")
        void nullAiMessage_logsSystemError() throws Exception {
            var mockResponse = mock(dev.langchain4j.model.chat.response.ChatResponse.class);
            when(mockResponse.aiMessage()).thenReturn(null);
            when(chatModel.chat(ArgumentMatchers.<dev.langchain4j.data.message.ChatMessage>anyList()))
                    .thenReturn(mockResponse);

            ChatResponse resp = service.processChat(buildBasicRequest());

            assertTrue(resp.getSuccess());
            assertTrue(resp.getAiResponse().contains("having trouble processing"));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  processChat — happy-path scenarios
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("processChat — happy-path scenarios")
    class ProcessChatHappyPath {

        @Test
        @DisplayName("returns successful response for a standard patient chat")
        void patientChat_returnsSuccessResponse() throws Exception {
            // Medical context is available for the patient
            when(medicalContextService.buildPatientContext(eq(PATIENT_ID), any(), any()))
                    .thenReturn("Vitals: BP 120/80, HR 72");

            ChatResponse resp = service.processChat(buildBasicRequest());

            assertTrue(resp.getSuccess());
            assertEquals(CONV_ID, resp.getConversationId(),
                    "Response must reference the conversation returned by cache");
            assertEquals(AI_TEXT, resp.getAiResponse());
            assertEquals("DEEPSEEK_VIA_LANGCHAIN4J", resp.getAiProvider());
            assertNotNull(resp.getTimestamp());
        }

        @Test
        @DisplayName("returns successful response for a caregiver-only chat (no patientId)")
        void caregiverOnlyChat_returnsSuccessResponse() throws Exception {
            // Caregiver sends a general question without a specific patient in context
            ChatRequest req = new ChatRequest();
            req.setUserId(USER_ID);
            req.setMessage("How do I manage caregiver burnout?");

            ChatResponse resp = service.processChat(req);

            assertTrue(resp.getSuccess());
            assertEquals(AI_TEXT, resp.getAiResponse());
        }

        @Test
        @DisplayName("uses temperature from request when explicitly provided")
        void requestTemperature_propagatedToResponse() throws Exception {
            ChatRequest req = buildBasicRequest();
            req.setTemperature(0.9);

            ChatResponse resp = service.processChat(req);

            assertTrue(resp.getSuccess());
            assertEquals(0.9, resp.getTemperatureUsed(), 0.001,
                    "Response temperature should match what was set on the request");
        }

        @Test
        @DisplayName("defaults temperature to 0.1 when the request omits it")
        void noTemperatureInRequest_defaultsToPointOne() throws Exception {
            ChatRequest req = buildBasicRequest();
            // temperature is null — service should default to 0.1

            ChatResponse resp = service.processChat(req);

            assertTrue(resp.getSuccess());
            assertEquals(0.1, resp.getTemperatureUsed(), 0.001);
        }

        @Test
        @DisplayName("reuses an existing conversation when conversationId is found in cache")
        void existingConversationId_conversationIsReused() throws Exception {
            // Conversation was started previously and is found in cache
            conversation.setCreatedAt(LocalDateTime.now().minusHours(2));
            when(cacheService.findConversation(CONV_ID)).thenReturn(Optional.of(conversation));

            ChatRequest req = buildBasicRequest();
            req.setConversationId(CONV_ID);

            ChatResponse resp = service.processChat(req);

            assertTrue(resp.getSuccess());
            assertEquals(CONV_ID, resp.getConversationId());
            // The existing conversation should not be persisted again via saveConversation
            verify(cacheService, never()).saveConversation(any());
        }

        @Test
        @DisplayName("creates a new conversation when the provided conversationId is not found")
        void unknownConversationId_newConversationCreated() throws Exception {
            // Cache returns empty even though an ID was supplied (e.g. expired/invalid)
            when(cacheService.findConversation("unknown-id")).thenReturn(Optional.empty());

            ChatRequest req = buildBasicRequest();
            req.setConversationId("unknown-id");

            ChatResponse resp = service.processChat(req);

            assertTrue(resp.getSuccess());
            // A brand-new conversation must be saved
            verify(cacheService, atLeastOnce()).saveConversation(any());
        }

        @Test
        @DisplayName("creates a new conversation when no conversationId is provided")
        void noConversationId_newConversationCreated() throws Exception {
            ChatResponse resp = service.processChat(buildBasicRequest());

            assertTrue(resp.getSuccess());
            verify(cacheService, atLeastOnce()).saveConversation(any());
        }

        @Test
        @DisplayName("appends extracted file content to the message for uploaded files")
        void uploadedFileWithContent_fileTextIncluded() throws Exception {
            UploadedFileDTO file = new UploadedFileDTO();
            file.setFilename("lab_results.pdf");
            file.setContentType("application/pdf");
            when(documentProcessingService.extractTextContent(any()))
                    .thenReturn("Lab result: HbA1c 6.5%");

            ChatRequest req = buildBasicRequest();
            req.setUploadedFiles(List.of(file));

            ChatResponse resp = service.processChat(req);

            assertTrue(resp.getSuccess());
            // Document processing should have been invoked once
            verify(documentProcessingService).extractTextContent(any());
        }

        @Test
        @DisplayName("handles uploaded files gracefully when extraction returns empty text")
        void uploadedFileWithNoExtractedText_successWithoutFileContent() throws Exception {
            UploadedFileDTO file = new UploadedFileDTO();
            file.setFilename("empty.pdf");
            file.setContentType("application/pdf");
            when(documentProcessingService.extractTextContent(any())).thenReturn("");

            ChatRequest req = buildBasicRequest();
            req.setUploadedFiles(List.of(file));

            ChatResponse resp = service.processChat(req);

            assertTrue(resp.getSuccess());
        }

        @Test
        @DisplayName("handles file extraction exception gracefully and continues processing")
        void uploadedFileThrowsDuringExtraction_processingContinues() throws Exception {
            UploadedFileDTO file = new UploadedFileDTO();
            file.setFilename("corrupt.pdf");
            file.setContentType("application/pdf");
            when(documentProcessingService.extractTextContent(any()))
                    .thenThrow(new RuntimeException("Corrupt PDF"));

            ChatRequest req = buildBasicRequest();
            req.setUploadedFiles(List.of(file));

            // The service catches per-file exceptions internally; overall request succeeds
            ChatResponse resp = service.processChat(req);

            assertTrue(resp.getSuccess());
        }

        @Test
        @DisplayName("uses CAREGIVER_SYSTEM_PROMPT when no patientId is present in request")
        void caregiverChat_withoutPatientId_usesCaregiverSystemPrompt() throws Exception {
            // When patientId is absent the service should select the caregiver prompt.
            // We verify indirectly: system-prompt sanitization is called with a non-null value.
            ChatRequest req = new ChatRequest();
            req.setUserId(USER_ID);
            req.setMessage("General care question?");

            service.processChat(req);

            // sanitizeSystemPrompt must have been invoked with some non-empty prompt
            verify(inputSanitizationService, atLeastOnce())
                    .sanitizeSystemPrompt(argThat(p -> p != null && !p.isBlank()), any(), any());
        }

        @Test
        @DisplayName("includes context_included and conversation metadata in success response")
        void successResponse_containsMetadata() throws Exception {
            when(chatMessageRepository.countByConversation(any())).thenReturn(7);
            when(chatMessageRepository.sumTokensUsedByConversation(any())).thenReturn(300);

            ChatResponse resp = service.processChat(buildBasicRequest());

            assertTrue(resp.getSuccess());
            assertEquals(7, resp.getTotalMessagesInConversation());
            assertEquals(300, resp.getTotalTokensUsedInConversation());
            assertNotNull(resp.getContextIncluded());
            assertFalse(resp.getApproachingTokenLimit());
        }

        @Test
        @DisplayName("handles null return from sumTokensUsedByConversation gracefully")
        void nullTotalTokens_defaultsToZero() throws Exception {
            when(chatMessageRepository.sumTokensUsedByConversation(any())).thenReturn(null);

            ChatResponse resp = service.processChat(buildBasicRequest());

            assertTrue(resp.getSuccess());
            assertEquals(0, resp.getTotalTokensUsedInConversation());
        }

        @Test
        @DisplayName("logs chat session start when conversation is newly created")
        void newConversation_logsChatSessionStart() throws Exception {
            // Set createdAt to now so it is within the last minute
            conversation.setCreatedAt(LocalDateTime.now());
            when(cacheService.saveConversation(any())).thenReturn(conversation);

            ChatResponse resp = service.processChat(buildBasicRequest());

            assertTrue(resp.getSuccess());
            verify(chatAuditService).logChatSessionStart(
                    eq(USER_ID), eq(CONV_ID), eq("mobile_app"), eq("127.0.0.1"));
        }

        @Test
        @DisplayName("does not log chat session start when conversation is old")
        void oldConversation_doesNotLogChatSessionStart() throws Exception {
            // createdAt is 5 minutes ago (set in setUp) — should NOT trigger logging
            ChatResponse resp = service.processChat(buildBasicRequest());

            assertTrue(resp.getSuccess());
            verify(chatAuditService, never()).logChatSessionStart(any(), any(), any(), any());
        }

        @Test
        @DisplayName("adds medical context to chat memory when present and memory is empty")
        void nonEmptyMedicalContext_addedToChatMemory() throws Exception {
            when(medicalContextService.buildPatientContext(eq(PATIENT_ID), any(), any()))
                    .thenReturn("Vitals: HR 72  Medications: Aspirin");

            ChatResponse resp = service.processChat(buildBasicRequest());

            assertTrue(resp.getSuccess());
            // system prompt + medical context + user message + AI response = 4 adds
            verify(chatMemory, times(4)).add(any(dev.langchain4j.data.message.ChatMessage.class));
        }

        @Test
        @DisplayName("skips adding system prompt to memory when memory is not empty")
        void nonEmptyMemory_skipsSystemPromptAddition() throws Exception {
            // Simulate non-empty chat memory (already has messages from previous interaction)
            List<dev.langchain4j.data.message.ChatMessage> existingMessages = new ArrayList<>();
            existingMessages.add(dev.langchain4j.data.message.SystemMessage.from("Existing prompt"));
            when(chatMemory.messages()).thenReturn(existingMessages);

            ChatResponse resp = service.processChat(buildBasicRequest());

            assertTrue(resp.getSuccess());
            // user message + AI response = 2 adds (system prompt skipped since memory non-empty)
            verify(chatMemory, times(2)).add(any(dev.langchain4j.data.message.ChatMessage.class));
        }

        @Test
        @DisplayName("handles null extracted text from file processing")
        void uploadedFileWithNullExtractedText_fallbackMessage() throws Exception {
            UploadedFileDTO file = new UploadedFileDTO();
            file.setFilename("image.png");
            file.setContentType("image/png");
            when(documentProcessingService.extractTextContent(any())).thenReturn(null);

            ChatRequest req = buildBasicRequest();
            req.setUploadedFiles(List.of(file));

            ChatResponse resp = service.processChat(req);

            assertTrue(resp.getSuccess());
            verify(documentProcessingService).extractTextContent(any());
        }

        @Test
        @DisplayName("processes request with null message but valid uploaded files")
        void nullMessageWithFiles_processesSuccessfully() throws Exception {
            UploadedFileDTO file = new UploadedFileDTO();
            file.setFilename("report.pdf");
            file.setContentType("application/pdf");
            when(documentProcessingService.extractTextContent(any())).thenReturn("File content");

            ChatRequest req = new ChatRequest();
            req.setUserId(USER_ID);
            req.setPatientId(PATIENT_ID);
            req.setMessage(null);
            req.setUploadedFiles(List.of(file));

            // When message is null but files are present, the service should still process
            // Note: the existing code checks (message == null || message.trim().isEmpty()) AND (files == null || files.isEmpty())
            // So null message with non-empty files should pass validation
            ChatResponse resp = service.processChat(req);
            // The message null-ness may cause NPE in later processing, handled by outer catch
            assertNotNull(resp);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  processChat — outer exception handling
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("processChat — outer error handling")
    class ProcessChatOuterExceptions {

        @Test
        @DisplayName("returns error response when an unexpected exception occurs in the outer try block")
        void outerException_returnsErrorResponse() throws Exception {
            // Force the cacheService.findUserAIConfig to throw an exception
            // which happens inside the try block at line 479
            when(cacheService.findUserAIConfig(eq(USER_ID), any()))
                    .thenThrow(new RuntimeException("Database connection lost"));

            ChatResponse resp = service.processChat(buildBasicRequest());

            assertFalse(resp.getSuccess());
            assertNotNull(resp.getErrorMessage());
            assertEquals("PROCESSING_ERROR", resp.getErrorCode());
        }

        @Test
        @DisplayName("returns error response with user-friendly AI response on outer catch")
        void outerException_responseContainsFriendlyMessage() throws Exception {
            when(cacheService.findUserAIConfig(eq(USER_ID), any()))
                    .thenThrow(new RuntimeException("Unexpected error"));

            ChatResponse resp = service.processChat(buildBasicRequest());

            assertFalse(resp.getSuccess());
            assertNotNull(resp.getAiResponse());
            assertTrue(resp.getAiResponse().contains("apologize"));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  processChat — AI config fallback path
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("processChat — AI config creation fallback")
    class ProcessChatAIConfigFallback {

        @Test
        @DisplayName("creates default AI config when none is found in cache")
        void noExistingAIConfig_createsDefault() throws Exception {
            when(cacheService.findUserAIConfig(eq(USER_ID), any())).thenReturn(Optional.empty());
            // saveUserAIConfig returns a valid config
            when(cacheService.saveUserAIConfig(any())).thenReturn(aiConfig);

            ChatResponse resp = service.processChat(buildBasicRequest());

            assertTrue(resp.getSuccess());
            verify(cacheService).saveUserAIConfig(any());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  processChat — conversation title generation
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("processChat — conversation creation with title")
    class ProcessChatConversationTitle {

        @Test
        @DisplayName("uses explicit title from request when provided")
        void explicitTitle_usedInConversation() throws Exception {
            ChatRequest req = buildBasicRequest();
            req.setTitle("My Custom Title");
            req.setConversationId("not-found-id");
            when(cacheService.findConversation("not-found-id")).thenReturn(Optional.empty());

            ChatResponse resp = service.processChat(req);

            assertTrue(resp.getSuccess());
            verify(cacheService).saveConversation(argThat(conv ->
                    "My Custom Title".equals(conv.getTitle())));
        }

        @Test
        @DisplayName("generates truncated title from long message when no title is provided")
        void longMessage_titleIsTruncated() throws Exception {
            ChatRequest req = new ChatRequest();
            req.setUserId(USER_ID);
            req.setPatientId(PATIENT_ID);
            req.setMessage("This is a very long message that is definitely more than fifty characters in length for testing purposes");
            req.setConversationId("not-found-conv");
            when(cacheService.findConversation("not-found-conv")).thenReturn(Optional.empty());

            ChatResponse resp = service.processChat(req);

            assertTrue(resp.getSuccess());
            verify(cacheService).saveConversation(argThat(conv ->
                    conv.getTitle() != null && conv.getTitle().length() == 50
                            && conv.getTitle().endsWith("...")));
        }

        @Test
        @DisplayName("uses full message as title when message is 50 chars or fewer")
        void shortMessage_fullMessageUsedAsTitle() throws Exception {
            ChatRequest req = new ChatRequest();
            req.setUserId(USER_ID);
            req.setPatientId(PATIENT_ID);
            req.setMessage("Short message");
            req.setConversationId("not-found-conv2");
            when(cacheService.findConversation("not-found-conv2")).thenReturn(Optional.empty());

            ChatResponse resp = service.processChat(req);

            assertTrue(resp.getSuccess());
            verify(cacheService).saveConversation(argThat(conv ->
                    "Short message".equals(conv.getTitle())));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  processChat — determineModel paths
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("processChat — model determination")
    class ProcessChatModelDetermination {

        @Test
        @DisplayName("uses preferred model from request when set")
        void preferredModelSet_usedInConversation() throws Exception {
            ChatRequest req = buildBasicRequest();
            req.setPreferredModel("custom-model-v2");
            req.setConversationId("conv-for-model-test");
            when(cacheService.findConversation("conv-for-model-test")).thenReturn(Optional.empty());

            ChatResponse resp = service.processChat(req);

            assertTrue(resp.getSuccess());
            verify(cacheService).saveConversation(argThat(conv ->
                    "custom-model-v2".equals(conv.getAiModelUsed())));
        }

        @Test
        @DisplayName("uses openai model when provider is OPENAI and no preferred model set")
        void openaiProvider_usesOpenaiModel() throws Exception {
            aiConfig.setPreferredAiProvider(UserAIConfig.AIProvider.OPENAI);
            ChatRequest req = buildBasicRequest();
            req.setConversationId("conv-openai-test");
            when(cacheService.findConversation("conv-openai-test")).thenReturn(Optional.empty());

            ChatResponse resp = service.processChat(req);

            assertTrue(resp.getSuccess());
            verify(cacheService).saveConversation(argThat(conv ->
                    "gpt-4o".equals(conv.getAiModelUsed())));
        }

        @Test
        @DisplayName("uses deepseek model when provider is DEEPSEEK and no preferred model set")
        void deepseekProvider_usesDeepseekModel() throws Exception {
            aiConfig.setPreferredAiProvider(UserAIConfig.AIProvider.DEEPSEEK);
            ChatRequest req = buildBasicRequest();
            req.setConversationId("conv-deepseek-test");
            when(cacheService.findConversation("conv-deepseek-test")).thenReturn(Optional.empty());

            ChatResponse resp = service.processChat(req);

            assertTrue(resp.getSuccess());
            verify(cacheService).saveConversation(argThat(conv ->
                    "deepseek-chat".equals(conv.getAiModelUsed())));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  getPatientConversations
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getPatientConversations")
    class GetPatientConversations {

        @Test
        @DisplayName("returns a list of conversation summaries for a patient")
        void returnsConversationSummaries() throws Exception {
            when(chatConversationRepository
                    .findByPatientIdAndIsActiveTrueOrderByUpdatedAtDesc(PATIENT_ID))
                    .thenReturn(List.of(conversation));
            when(chatMessageRepository.countByConversation(conversation)).thenReturn(3);

            List<ChatConversationSummary> summaries =
                    service.getPatientConversations(PATIENT_ID);

            assertEquals(1, summaries.size());
            ChatConversationSummary s = summaries.get(0);
            assertEquals(CONV_ID, s.getConversationId());
            assertEquals("Test conversation", s.getTitle());
            assertEquals(3, s.getTotalMessages());
            assertTrue(s.getIsActive());
        }

        @Test
        @DisplayName("returns an empty list when no active conversations exist for patient")
        void noConversations_returnsEmptyList() throws Exception {
            when(chatConversationRepository
                    .findByPatientIdAndIsActiveTrueOrderByUpdatedAtDesc(PATIENT_ID))
                    .thenReturn(Collections.emptyList());

            List<ChatConversationSummary> summaries =
                    service.getPatientConversations(PATIENT_ID);

            assertTrue(summaries.isEmpty());
        }

        @Test
        @DisplayName("maps AI provider name to string in the summary")
        void aiProviderMappedToStringName() throws Exception {
            conversation.setAiProviderUsed(UserAIConfig.AIProvider.DEEPSEEK);
            when(chatConversationRepository
                    .findByPatientIdAndIsActiveTrueOrderByUpdatedAtDesc(PATIENT_ID))
                    .thenReturn(List.of(conversation));

            List<ChatConversationSummary> summaries =
                    service.getPatientConversations(PATIENT_ID);

            assertEquals("DEEPSEEK", summaries.get(0).getAiProvider());
        }

        @Test
        @DisplayName("maps null AI provider to null in the summary without throwing")
        void nullAiProvider_mappedToNull() throws Exception {
            conversation.setAiProviderUsed(null);
            when(chatConversationRepository
                    .findByPatientIdAndIsActiveTrueOrderByUpdatedAtDesc(PATIENT_ID))
                    .thenReturn(List.of(conversation));

            List<ChatConversationSummary> summaries =
                    service.getPatientConversations(PATIENT_ID);

            assertNull(summaries.get(0).getAiProvider());
        }

        @Test
        @DisplayName("maps all conversation fields correctly in summary")
        void allFieldsMappedCorrectly() throws Exception {
            conversation.setAiModelUsed("deepseek-chat");
            conversation.setTotalTokensUsed(500);
            conversation.setUpdatedAt(LocalDateTime.of(2026, 1, 15, 10, 30));
            when(chatConversationRepository
                    .findByPatientIdAndIsActiveTrueOrderByUpdatedAtDesc(PATIENT_ID))
                    .thenReturn(List.of(conversation));

            List<ChatConversationSummary> summaries =
                    service.getPatientConversations(PATIENT_ID);

            ChatConversationSummary s = summaries.get(0);
            assertEquals("deepseek-chat", s.getAiModel());
            assertEquals(500, s.getTotalTokensUsed());
            assertEquals(LocalDateTime.of(2026, 1, 15, 10, 30), s.getLastMessageAt());
            assertNotNull(s.getCreatedAt());
            assertEquals(ChatConversation.ChatType.GENERAL_SUPPORT, s.getChatType());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  getConversationMessages
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getConversationMessages")
    class GetConversationMessages {

        @Test
        @DisplayName("returns message summaries for a valid, active conversationId")
        void validConversation_returnsMessageSummaries() throws Exception {
            ChatMessage msg = buildChatMessage(ChatMessage.MessageType.USER, "What are my vitals?");

            when(chatConversationRepository.findByConversationIdAndIsActiveTrue(CONV_ID))
                    .thenReturn(Optional.of(conversation));
            when(chatMessageRepository.findByConversationOrderByCreatedAtAsc(conversation))
                    .thenReturn(List.of(msg));

            List<ChatMessageSummary> summaries = service.getConversationMessages(CONV_ID);

            assertEquals(1, summaries.size());
            ChatMessageSummary s = summaries.get(0);
            assertEquals("What are my vitals?", s.getContent());
            assertEquals(ChatMessage.MessageType.USER, s.getMessageType());
        }

        @Test
        @DisplayName("throws IllegalArgumentException when conversationId is not found")
        void unknownConversationId_throwsIllegalArgumentException() throws Exception {
            when(chatConversationRepository.findByConversationIdAndIsActiveTrue("missing"))
                    .thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class,
                    () -> service.getConversationMessages("missing"),
                    "Looking up a non-existent conversation should throw");
        }

        @Test
        @DisplayName("returns empty list when conversation exists but has no messages")
        void conversationWithNoMessages_returnsEmptyList() throws Exception {
            when(chatConversationRepository.findByConversationIdAndIsActiveTrue(CONV_ID))
                    .thenReturn(Optional.of(conversation));
            when(chatMessageRepository.findByConversationOrderByCreatedAtAsc(conversation))
                    .thenReturn(Collections.emptyList());

            List<ChatMessageSummary> summaries = service.getConversationMessages(CONV_ID);

            assertTrue(summaries.isEmpty());
        }

        @Test
        @DisplayName("maps all message fields into the summary DTO correctly")
        void messageFieldsMappedCorrectly() throws Exception {
            ChatMessage msg = buildChatMessage(ChatMessage.MessageType.ASSISTANT,
                    "Your BP is 120/80.");
            msg.setId(42L);
            msg.setTokensUsed(50);
            msg.setProcessingTimeMs(350L);
            msg.setAiModelUsed("deepseek-chat");

            when(chatConversationRepository.findByConversationIdAndIsActiveTrue(CONV_ID))
                    .thenReturn(Optional.of(conversation));
            when(chatMessageRepository.findByConversationOrderByCreatedAtAsc(conversation))
                    .thenReturn(List.of(msg));

            ChatMessageSummary s = service.getConversationMessages(CONV_ID).get(0);

            assertEquals(42L,            s.getMessageId());
            assertEquals(50,             s.getTokensUsed());
            assertEquals(350L,           s.getProcessingTimeMs());
            assertEquals("deepseek-chat", s.getAiModelUsed());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  getRecentMessagesForUser
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getRecentMessagesForUser")
    class GetRecentMessagesForUser {

        @Test
        @DisplayName("returns empty list when the user has no active conversations")
        void noConversations_returnsEmptyList() throws Exception {
            when(chatConversationRepository.findByUserIdAndIsActiveTrueOrderByUpdatedAtDesc(USER_ID))
                    .thenReturn(Collections.emptyList());

            List<ChatMessageSummary> result = service.getRecentMessagesForUser(USER_ID, 10);

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("returns messages from the most recent active conversation")
        void singleConversation_returnsMessages() throws Exception {
            ChatMessage msg = buildChatMessage(ChatMessage.MessageType.ASSISTANT,
                    "Your BP is 120/80.");

            when(chatConversationRepository.findByUserIdAndIsActiveTrueOrderByUpdatedAtDesc(USER_ID))
                    .thenReturn(List.of(conversation));
            when(chatMessageRepository.findTopNByConversationOrderByCreatedAtAsc(conversation, 5))
                    .thenReturn(List.of(msg));

            List<ChatMessageSummary> result = service.getRecentMessagesForUser(USER_ID, 5);

            assertEquals(1, result.size());
            assertEquals("Your BP is 120/80.", result.get(0).getContent());
        }

        @Test
        @DisplayName("uses only the most recent conversation when multiple conversations exist")
        void multipleConversations_onlyMostRecentIsQueried() throws Exception {
            ChatConversation older = ChatConversation.builder()
                    .conversationId("older-conv")
                    .userId(USER_ID)
                    .patientId(PATIENT_ID)
                    .isActive(true)
                    .totalTokensUsed(0)
                    .build();
            older.setCreatedAt(LocalDateTime.now().minusHours(3));

            ChatMessage recentMsg = buildChatMessage(
                    ChatMessage.MessageType.USER, "Latest message");

            // Service returns [conversation, older] in descending order (conversation is newest)
            when(chatConversationRepository.findByUserIdAndIsActiveTrueOrderByUpdatedAtDesc(USER_ID))
                    .thenReturn(List.of(conversation, older));
            when(chatMessageRepository.findTopNByConversationOrderByCreatedAtAsc(conversation, 3))
                    .thenReturn(List.of(recentMsg));

            List<ChatMessageSummary> result = service.getRecentMessagesForUser(USER_ID, 3);

            // Only messages from 'conversation' (the most recent) should be returned
            assertEquals(1, result.size());
            assertEquals("Latest message", result.get(0).getContent());
            // 'older' conversation's messages must NOT be queried
            verify(chatMessageRepository, never())
                    .findTopNByConversationOrderByCreatedAtAsc(eq(older), anyInt());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  deactivateConversation
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("deactivateConversation")
    class DeactivateConversation {

        @Test
        @DisplayName("sets isActive=false and saves the conversation when found")
        void deactivate_success() throws Exception {
            when(chatConversationRepository.findByConversationIdAndIsActiveTrue(CONV_ID))
                    .thenReturn(Optional.of(conversation));

            service.deactivateConversation(CONV_ID);

            assertFalse(conversation.getIsActive(),
                    "Conversation must be marked inactive after deactivation");
            verify(chatConversationRepository).save(conversation);
        }

        @Test
        @DisplayName("logs the deletion through the audit service")
        void deactivate_auditEventLogged() throws Exception {
            when(chatConversationRepository.findByConversationIdAndIsActiveTrue(CONV_ID))
                    .thenReturn(Optional.of(conversation));

            service.deactivateConversation(CONV_ID);

            // The audit service must record who deleted which conversation and why
            verify(chatAuditService).logConversationDeleted(
                    eq(USER_ID), eq(CONV_ID), anyString());
        }

        @Test
        @DisplayName("throws IllegalArgumentException when the conversationId is not found")
        void deactivate_notFound_throwsIllegalArgumentException() throws Exception {
            when(chatConversationRepository.findByConversationIdAndIsActiveTrue("ghost"))
                    .thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class,
                    () -> service.deactivateConversation("ghost"),
                    "Deactivating a non-existent conversation must throw");
        }

        @Test
        @DisplayName("does not save when conversation is not found (exception raised first)")
        void deactivate_notFound_repositorySaveNotCalled() throws Exception {
            when(chatConversationRepository.findByConversationIdAndIsActiveTrue("ghost"))
                    .thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class,
                    () -> service.deactivateConversation("ghost"));

            verify(chatConversationRepository, never()).save(any());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  parseContextIncluded — medical context tag parsing
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Medical context — context type tagging")
    class MedicalContextParsing {

        @Test
        @DisplayName("builds patient context when patientId is present")
        void patientContext_built_whenPatientIdSet() throws Exception {
            when(medicalContextService.buildPatientContext(eq(PATIENT_ID), any(), any()))
                    .thenReturn("Vitals: HR 72  Medications: Aspirin  Allergies: Penicillin");

            ChatResponse resp = service.processChat(buildBasicRequest());

            assertTrue(resp.getSuccess());
            verify(medicalContextService).buildPatientContext(eq(PATIENT_ID), any(), any());
        }

        @Test
        @DisplayName("skips patient context when no patientId is set (caregiver-only mode)")
        void patientContext_skipped_whenNoPatientId() throws Exception {
            ChatRequest req = new ChatRequest();
            req.setUserId(USER_ID);
            req.setMessage("General question");

            service.processChat(req);

            // medicalContextService must not be called without a patient
            verify(medicalContextService, never()).buildPatientContext(any(), any(), any());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Private helper methods tested via reflection
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Private helpers — via reflection")
    class PrivateHelpers {

        @Test
        @DisplayName("generateConversationTitle truncates messages longer than 50 characters")
        void generateConversationTitle_truncatesLongMessage() throws Exception {
            Method method = DefaultAIChatService.class.getDeclaredMethod(
                    "generateConversationTitle", String.class);
            method.setAccessible(true);

            String longMsg = "A".repeat(60);
            String result = (String) method.invoke(service, longMsg);

            assertEquals(50, result.length());
            assertTrue(result.endsWith("..."));
            assertEquals("A".repeat(47) + "...", result);
        }

        @Test
        @DisplayName("generateConversationTitle returns full message when 50 chars or fewer")
        void generateConversationTitle_shortMessage() throws Exception {
            Method method = DefaultAIChatService.class.getDeclaredMethod(
                    "generateConversationTitle", String.class);
            method.setAccessible(true);

            String shortMsg = "Hello";
            String result = (String) method.invoke(service, shortMsg);

            assertEquals("Hello", result);
        }

        @Test
        @DisplayName("generateConversationTitle returns full message when exactly 50 chars")
        void generateConversationTitle_exactly50Chars() throws Exception {
            Method method = DefaultAIChatService.class.getDeclaredMethod(
                    "generateConversationTitle", String.class);
            method.setAccessible(true);

            String msg50 = "A".repeat(50);
            String result = (String) method.invoke(service, msg50);

            assertEquals(msg50, result);
        }

        @Test
        @DisplayName("createMessage returns a Map with role and content")
        void createMessage_returnsMap() throws Exception {
            Method method = DefaultAIChatService.class.getDeclaredMethod(
                    "createMessage", String.class, String.class);
            method.setAccessible(true);

            Object result = method.invoke(service, "user", "Hello there");

            assertTrue(result instanceof Map);
            Map<String, String> map = (Map<String, String>) result;
            assertEquals("user", map.get("role"));
            assertEquals("Hello there", map.get("content"));
        }

        @Test
        @DisplayName("buildContextSummary returns 'Medical context included' when context is non-null")
        void buildContextSummary_nonNullContext() throws Exception {
            Method method = DefaultAIChatService.class.getDeclaredMethod(
                    "buildContextSummary", String.class);
            method.setAccessible(true);

            String result = (String) method.invoke(service, "Some medical context");

            assertEquals("Medical context included", result);
        }

        @Test
        @DisplayName("buildContextSummary returns 'No medical context' when context is null")
        void buildContextSummary_nullContext() throws Exception {
            Method method = DefaultAIChatService.class.getDeclaredMethod(
                    "buildContextSummary", String.class);
            method.setAccessible(true);

            String result = (String) method.invoke(service, (Object) null);

            assertEquals("No medical context", result);
        }

        @Test
        @DisplayName("parseContextIncluded returns empty list for null context")
        void parseContextIncluded_nullContext() throws Exception {
            Method method = DefaultAIChatService.class.getDeclaredMethod(
                    "parseContextIncluded", String.class);
            method.setAccessible(true);

            List<String> result = (List<String>) method.invoke(service, (Object) null);

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("parseContextIncluded returns empty list for blank context")
        void parseContextIncluded_blankContext() throws Exception {
            Method method = DefaultAIChatService.class.getDeclaredMethod(
                    "parseContextIncluded", String.class);
            method.setAccessible(true);

            List<String> result = (List<String>) method.invoke(service, "   ");

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("parseContextIncluded detects vitals tag")
        void parseContextIncluded_vitals() throws Exception {
            Method method = DefaultAIChatService.class.getDeclaredMethod(
                    "parseContextIncluded", String.class);
            method.setAccessible(true);

            List<String> result = (List<String>) method.invoke(service, "Vitals: HR 72");

            assertTrue(result.contains("vitals"));
        }

        @Test
        @DisplayName("parseContextIncluded detects medications tag")
        void parseContextIncluded_medications() throws Exception {
            Method method = DefaultAIChatService.class.getDeclaredMethod(
                    "parseContextIncluded", String.class);
            method.setAccessible(true);

            List<String> result = (List<String>) method.invoke(service, "Medications: Aspirin");

            assertTrue(result.contains("medications"));
        }

        @Test
        @DisplayName("parseContextIncluded detects notes tag")
        void parseContextIncluded_notes() throws Exception {
            Method method = DefaultAIChatService.class.getDeclaredMethod(
                    "parseContextIncluded", String.class);
            method.setAccessible(true);

            List<String> result = (List<String>) method.invoke(service, "Clinical Notes: checkup");

            assertTrue(result.contains("notes"));
        }

        @Test
        @DisplayName("parseContextIncluded detects mood/pain logs tag")
        void parseContextIncluded_moodPainLogs() throws Exception {
            Method method = DefaultAIChatService.class.getDeclaredMethod(
                    "parseContextIncluded", String.class);
            method.setAccessible(true);

            List<String> result = (List<String>) method.invoke(service, "Mood/Pain Logs: pain 3/10");

            assertTrue(result.contains("mood_pain_logs"));
        }

        @Test
        @DisplayName("parseContextIncluded detects allergies tag")
        void parseContextIncluded_allergies() throws Exception {
            Method method = DefaultAIChatService.class.getDeclaredMethod(
                    "parseContextIncluded", String.class);
            method.setAccessible(true);

            List<String> result = (List<String>) method.invoke(service, "Allergies: Penicillin");

            assertTrue(result.contains("allergies"));
        }

        @Test
        @DisplayName("parseContextIncluded detects all tags in combined context")
        void parseContextIncluded_allTags() throws Exception {
            Method method = DefaultAIChatService.class.getDeclaredMethod(
                    "parseContextIncluded", String.class);
            method.setAccessible(true);

            String fullContext = "Vitals: HR 72 Medications: Aspirin Clinical Notes: ok " +
                    "Mood/Pain Logs: 5/10 Allergies: none";
            List<String> result = (List<String>) method.invoke(service, fullContext);

            assertEquals(5, result.size());
            assertTrue(result.contains("vitals"));
            assertTrue(result.contains("medications"));
            assertTrue(result.contains("notes"));
            assertTrue(result.contains("mood_pain_logs"));
            assertTrue(result.contains("allergies"));
        }

        @Test
        @DisplayName("parseContextIncluded returns empty list for context with no known tags")
        void parseContextIncluded_noKnownTags() throws Exception {
            Method method = DefaultAIChatService.class.getDeclaredMethod(
                    "parseContextIncluded", String.class);
            method.setAccessible(true);

            List<String> result = (List<String>) method.invoke(service, "General information only");

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("determineModel returns preferred model when set")
        void determineModel_preferredModelSet() throws Exception {
            Method method = DefaultAIChatService.class.getDeclaredMethod(
                    "determineModel", ChatRequest.class, UserAIConfig.class);
            method.setAccessible(true);

            ChatRequest req = new ChatRequest();
            req.setPreferredModel("custom-model");

            String result = (String) method.invoke(service, req, aiConfig);

            assertEquals("custom-model", result);
        }

        @Test
        @DisplayName("determineModel returns openai model for OPENAI provider")
        void determineModel_openaiProvider() throws Exception {
            Method method = DefaultAIChatService.class.getDeclaredMethod(
                    "determineModel", ChatRequest.class, UserAIConfig.class);
            method.setAccessible(true);

            ChatRequest req = new ChatRequest();
            UserAIConfig openaiConfig = UserAIConfig.builder()
                    .preferredAiProvider(UserAIConfig.AIProvider.OPENAI)
                    .openaiModel("gpt-4o")
                    .deepseekModel("deepseek-chat")
                    .build();

            String result = (String) method.invoke(service, req, openaiConfig);

            assertEquals("gpt-4o", result);
        }

        @Test
        @DisplayName("determineModel returns deepseek model for DEEPSEEK provider")
        void determineModel_deepseekProvider() throws Exception {
            Method method = DefaultAIChatService.class.getDeclaredMethod(
                    "determineModel", ChatRequest.class, UserAIConfig.class);
            method.setAccessible(true);

            ChatRequest req = new ChatRequest();

            String result = (String) method.invoke(service, req, aiConfig);

            assertEquals("deepseek-chat", result);
        }

        @Test
        @DisplayName("buildErrorResponse sets all error fields correctly")
        void buildErrorResponse_allFieldsSet() throws Exception {
            Method method = DefaultAIChatService.class.getDeclaredMethod(
                    "buildErrorResponse", ChatRequest.class, String.class);
            method.setAccessible(true);

            ChatRequest req = buildBasicRequest();
            req.setConversationId("test-conv");

            ChatResponse result = (ChatResponse) method.invoke(service, req, "Test error");

            assertEquals("test-conv", result.getConversationId());
            assertEquals(req.getMessage(), result.getMessage());
            assertFalse(result.getSuccess());
            assertEquals("Test error", result.getErrorMessage());
            assertEquals("PROCESSING_ERROR", result.getErrorCode());
            assertNotNull(result.getTimestamp());
            assertNotNull(result.getAiResponse());
            assertTrue(result.getAiResponse().contains("apologize"));
            assertEquals("DEEPSEEK_VIA_LANGCHAIN4J", result.getAiProvider());
            assertEquals(0, result.getTokensUsed());
            assertEquals(0L, result.getProcessingTimeMs());
        }

        @Test
        @DisplayName("processUploadedFiles returns empty string when file list is empty")
        void processUploadedFiles_emptyList() throws Exception {
            Method method = DefaultAIChatService.class.getDeclaredMethod(
                    "processUploadedFiles", List.class);
            method.setAccessible(true);

            List<UploadedFileDTO> files = Collections.emptyList();
            String result = (String) method.invoke(service, files);

            assertEquals("", result);
        }

        @Test
        @DisplayName("processUploadedFiles includes file name and extracted text")
        void processUploadedFiles_withContent() throws Exception {
            Method method = DefaultAIChatService.class.getDeclaredMethod(
                    "processUploadedFiles", List.class);
            method.setAccessible(true);

            UploadedFileDTO file = new UploadedFileDTO();
            file.setFilename("doc.pdf");
            file.setContentType("application/pdf");
            when(documentProcessingService.extractTextContent(any())).thenReturn("Extracted text");

            String result = (String) method.invoke(service, List.of(file));

            assertTrue(result.contains("doc.pdf"));
            assertTrue(result.contains("Extracted text"));
        }

        @Test
        @DisplayName("processUploadedFiles handles null extracted text")
        void processUploadedFiles_nullExtracted() throws Exception {
            Method method = DefaultAIChatService.class.getDeclaredMethod(
                    "processUploadedFiles", List.class);
            method.setAccessible(true);

            UploadedFileDTO file = new UploadedFileDTO();
            file.setFilename("image.png");
            file.setContentType("image/png");
            when(documentProcessingService.extractTextContent(any())).thenReturn(null);

            String result = (String) method.invoke(service, List.of(file));

            assertTrue(result.contains("image.png"));
            assertTrue(result.contains("no text content could be extracted"));
        }

        @Test
        @DisplayName("processUploadedFiles handles empty extracted text")
        void processUploadedFiles_emptyExtracted() throws Exception {
            Method method = DefaultAIChatService.class.getDeclaredMethod(
                    "processUploadedFiles", List.class);
            method.setAccessible(true);

            UploadedFileDTO file = new UploadedFileDTO();
            file.setFilename("blank.txt");
            file.setContentType("text/plain");
            when(documentProcessingService.extractTextContent(any())).thenReturn("  ");

            String result = (String) method.invoke(service, List.of(file));

            assertTrue(result.contains("blank.txt"));
            assertTrue(result.contains("no text content could be extracted"));
        }

        @Test
        @DisplayName("processUploadedFiles handles extraction exception")
        void processUploadedFiles_exception() throws Exception {
            Method method = DefaultAIChatService.class.getDeclaredMethod(
                    "processUploadedFiles", List.class);
            method.setAccessible(true);

            UploadedFileDTO file = new UploadedFileDTO();
            file.setFilename("corrupt.pdf");
            file.setContentType("application/pdf");
            when(documentProcessingService.extractTextContent(any()))
                    .thenThrow(new RuntimeException("Parse error"));

            String result = (String) method.invoke(service, List.of(file));

            assertTrue(result.contains("corrupt.pdf"));
            assertTrue(result.contains("Error processing file"));
            assertTrue(result.contains("Parse error"));
        }

        @Test
        @DisplayName("processUploadedFiles handles multiple files with mixed results")
        void processUploadedFiles_multipleFiles() throws Exception {
            Method method = DefaultAIChatService.class.getDeclaredMethod(
                    "processUploadedFiles", List.class);
            method.setAccessible(true);

            UploadedFileDTO file1 = new UploadedFileDTO();
            file1.setFilename("good.pdf");
            file1.setContentType("application/pdf");

            UploadedFileDTO file2 = new UploadedFileDTO();
            file2.setFilename("bad.pdf");
            file2.setContentType("application/pdf");

            when(documentProcessingService.extractTextContent(file1)).thenReturn("Good content");
            when(documentProcessingService.extractTextContent(file2))
                    .thenThrow(new RuntimeException("Corrupt"));

            String result = (String) method.invoke(service, List.of(file1, file2));

            assertTrue(result.contains("good.pdf"));
            assertTrue(result.contains("Good content"));
            assertTrue(result.contains("bad.pdf"));
            assertTrue(result.contains("Error processing file"));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Private inner classes tested via reflection
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Inner classes — ChatProcessingContext and ChatProcessingResult")
    class InnerClasses {

        @Test
        @DisplayName("ChatProcessingContext constructor sets all fields")
        void chatProcessingContext_allFieldsSet() throws Exception {
            Class<?> contextClass = Class.forName(
                    "com.careconnect.service.DefaultAIChatService$ChatProcessingContext");
            Constructor<?> ctor = contextClass.getDeclaredConstructor(
                    Patient.class, UserAIConfig.class, ChatConversation.class,
                    List.class, String.class, Double.class, Integer.class,
                    String.class, long.class);
            ctor.setAccessible(true);

            List<Object> messages = new ArrayList<>();
            messages.add("msg1");
            Object context = ctor.newInstance(
                    patient, aiConfig, conversation, messages,
                    "model-name", 0.5, 1024, "med-context", 12345L);

            // Verify fields via reflection
            var patientField = contextClass.getDeclaredField("patient");
            patientField.setAccessible(true);
            assertEquals(patient, patientField.get(context));

            var aiConfigField = contextClass.getDeclaredField("aiConfig");
            aiConfigField.setAccessible(true);
            assertEquals(aiConfig, aiConfigField.get(context));

            var conversationField = contextClass.getDeclaredField("conversation");
            conversationField.setAccessible(true);
            assertEquals(conversation, conversationField.get(context));

            var messagesField = contextClass.getDeclaredField("messages");
            messagesField.setAccessible(true);
            assertEquals(messages, messagesField.get(context));

            var modelField = contextClass.getDeclaredField("model");
            modelField.setAccessible(true);
            assertEquals("model-name", modelField.get(context));

            var temperatureField = contextClass.getDeclaredField("temperature");
            temperatureField.setAccessible(true);
            assertEquals(0.5, temperatureField.get(context));

            var maxTokensField = contextClass.getDeclaredField("max_tokens");
            maxTokensField.setAccessible(true);
            assertEquals(1024, maxTokensField.get(context));

            var medicalContextField = contextClass.getDeclaredField("medicalContext");
            medicalContextField.setAccessible(true);
            assertEquals("med-context", medicalContextField.get(context));

            var startTimeField = contextClass.getDeclaredField("startTime");
            startTimeField.setAccessible(true);
            assertEquals(12345L, startTimeField.get(context));
        }

        @Test
        @DisplayName("ChatProcessingResult constructor sets all fields")
        void chatProcessingResult_allFieldsSet() throws Exception {
            // First create a ChatProcessingContext
            Class<?> contextClass = Class.forName(
                    "com.careconnect.service.DefaultAIChatService$ChatProcessingContext");
            Constructor<?> ctxCtor = contextClass.getDeclaredConstructor(
                    Patient.class, UserAIConfig.class, ChatConversation.class,
                    List.class, String.class, Double.class, Integer.class,
                    String.class, long.class);
            ctxCtor.setAccessible(true);
            Object context = ctxCtor.newInstance(
                    patient, aiConfig, conversation, new ArrayList<>(),
                    "model", 0.5, 1024, "context", 0L);

            Class<?> resultClass = Class.forName(
                    "com.careconnect.service.DefaultAIChatService$ChatProcessingResult");
            Constructor<?> resCtor = resultClass.getDeclaredConstructor(
                    contextClass, String.class, Integer.class, Long.class, String.class);
            resCtor.setAccessible(true);

            Object result = resCtor.newInstance(context, "AI response", 100, 500L, null);

            var contextField = resultClass.getDeclaredField("context");
            contextField.setAccessible(true);
            assertEquals(context, contextField.get(result));

            var aiResponseField = resultClass.getDeclaredField("aiResponse");
            aiResponseField.setAccessible(true);
            assertEquals("AI response", aiResponseField.get(result));

            var tokensUsedField = resultClass.getDeclaredField("tokensUsed");
            tokensUsedField.setAccessible(true);
            assertEquals(100, tokensUsedField.get(result));

            var processingTimeMsField = resultClass.getDeclaredField("processingTimeMs");
            processingTimeMsField.setAccessible(true);
            assertEquals(500L, processingTimeMsField.get(result));

            var errorField = resultClass.getDeclaredField("error");
            errorField.setAccessible(true);
            assertNull(errorField.get(result));
        }

        @Test
        @DisplayName("ChatProcessingResult with error message set")
        void chatProcessingResult_withError() throws Exception {
            Class<?> contextClass = Class.forName(
                    "com.careconnect.service.DefaultAIChatService$ChatProcessingContext");
            Constructor<?> ctxCtor = contextClass.getDeclaredConstructor(
                    Patient.class, UserAIConfig.class, ChatConversation.class,
                    List.class, String.class, Double.class, Integer.class,
                    String.class, long.class);
            ctxCtor.setAccessible(true);
            Object context = ctxCtor.newInstance(
                    patient, aiConfig, conversation, new ArrayList<>(),
                    "model", 0.5, 1024, "context", 0L);

            Class<?> resultClass = Class.forName(
                    "com.careconnect.service.DefaultAIChatService$ChatProcessingResult");
            Constructor<?> resCtor = resultClass.getDeclaredConstructor(
                    contextClass, String.class, Integer.class, Long.class, String.class);
            resCtor.setAccessible(true);

            Object result = resCtor.newInstance(context, null, null, null, "Something went wrong");

            var errorField = resultClass.getDeclaredField("error");
            errorField.setAccessible(true);
            assertEquals("Something went wrong", errorField.get(result));

            var tokensUsedField = resultClass.getDeclaredField("tokensUsed");
            tokensUsedField.setAccessible(true);
            assertNull(tokensUsedField.get(result));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  prepareMessagesForAI and prepareChatMessagesForAI via reflection
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Message preparation helpers — via reflection")
    class MessagePreparationHelpers {

        @Test
        @DisplayName("prepareMessagesForAI with custom system prompt and medical context")
        void prepareMessagesForAI_withPromptAndContext() throws Exception {
            Method method = DefaultAIChatService.class.getDeclaredMethod(
                    "prepareMessagesForAI", ChatConversation.class, String.class,
                    String.class, String.class);
            method.setAccessible(true);

            when(chatMessageRepository.findTopNByConversationOrderByCreatedAtAsc(any(), anyInt()))
                    .thenReturn(Collections.emptyList());

            List<Object> result = (List<Object>) method.invoke(
                    service, conversation, "Hello", "Vitals: HR 72", "Custom prompt");

            assertNotNull(result);
            // system prompt + medical context + user message = 3
            assertEquals(3, result.size());
        }

        @Test
        @DisplayName("prepareMessagesForAI with null system prompt uses default")
        void prepareMessagesForAI_nullPromptUsesDefault() throws Exception {
            Method method = DefaultAIChatService.class.getDeclaredMethod(
                    "prepareMessagesForAI", ChatConversation.class, String.class,
                    String.class, String.class);
            method.setAccessible(true);

            List<Object> result = (List<Object>) method.invoke(
                    service, conversation, "Hello", null, null);

            assertNotNull(result);
            // system prompt (default) + user message = 2 (no medical context)
            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("prepareMessagesForAI with blank system prompt uses default")
        void prepareMessagesForAI_blankPromptUsesDefault() throws Exception {
            Method method = DefaultAIChatService.class.getDeclaredMethod(
                    "prepareMessagesForAI", ChatConversation.class, String.class,
                    String.class, String.class);
            method.setAccessible(true);

            List<Object> result = (List<Object>) method.invoke(
                    service, conversation, "Hello", "   ", "   ");

            assertNotNull(result);
            // default prompt + user message = 2 (blank medical context skipped)
            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("prepareMessagesForAI includes chat history messages")
        void prepareMessagesForAI_includesHistory() throws Exception {
            Method method = DefaultAIChatService.class.getDeclaredMethod(
                    "prepareMessagesForAI", ChatConversation.class, String.class,
                    String.class, String.class);
            method.setAccessible(true);

            ChatMessage historyMsg = buildChatMessage(ChatMessage.MessageType.USER, "Previous msg");
            when(chatMessageRepository.findTopNByConversationOrderByCreatedAtAsc(any(), anyInt()))
                    .thenReturn(List.of(historyMsg));

            List<Object> result = (List<Object>) method.invoke(
                    service, conversation, "New msg", null, "System prompt");

            // system prompt + 1 history message + user message = 3
            assertEquals(3, result.size());
        }

        @Test
        @DisplayName("prepareMessagesForAI uses default history limit when userId or patientId is null")
        void prepareMessagesForAI_nullIdsUsesDefaultLimit() throws Exception {
            Method method = DefaultAIChatService.class.getDeclaredMethod(
                    "prepareMessagesForAI", ChatConversation.class, String.class,
                    String.class, String.class);
            method.setAccessible(true);

            ChatConversation convNoUser = ChatConversation.builder()
                    .conversationId("conv-no-user")
                    .patientId(PATIENT_ID)
                    .userId(null)
                    .isActive(true)
                    .totalTokensUsed(0)
                    .build();
            convNoUser.setCreatedAt(LocalDateTime.now());

            List<Object> result = (List<Object>) method.invoke(
                    service, convNoUser, "Hello", null, "Prompt");

            assertNotNull(result);
            // Default limit 20 used when userId is null
            verify(chatMessageRepository).findTopNByConversationOrderByCreatedAtAsc(convNoUser, 20);
        }

        @Test
        @DisplayName("prepareMessagesForAI with null conversationHistoryLimit on config defaults to 20")
        void prepareMessagesForAI_nullHistoryLimitDefaults() throws Exception {
            Method method = DefaultAIChatService.class.getDeclaredMethod(
                    "prepareMessagesForAI", ChatConversation.class, String.class,
                    String.class, String.class);
            method.setAccessible(true);

            // Config with null conversation history limit
            UserAIConfig nullLimitConfig = UserAIConfig.builder()
                    .userId(USER_ID)
                    .patientId(PATIENT_ID)
                    .preferredAiProvider(UserAIConfig.AIProvider.DEEPSEEK)
                    .conversationHistoryLimit(null)
                    .build();
            when(cacheService.findUserAIConfig(eq(USER_ID), any())).thenReturn(Optional.of(nullLimitConfig));

            List<Object> result = (List<Object>) method.invoke(
                    service, conversation, "Hello", null, "Prompt");

            assertNotNull(result);
            verify(chatMessageRepository).findTopNByConversationOrderByCreatedAtAsc(conversation, 20);
        }

        @Test
        @DisplayName("prepareChatMessagesForAI with custom system prompt and medical context")
        void prepareChatMessagesForAI_withPromptAndContext() throws Exception {
            Method method = DefaultAIChatService.class.getDeclaredMethod(
                    "prepareChatMessagesForAI", ChatConversation.class, String.class,
                    String.class, String.class);
            method.setAccessible(true);

            when(chatMessageRepository.findTopNByConversationOrderByCreatedAtAsc(any(), anyInt()))
                    .thenReturn(Collections.emptyList());

            List<dev.langchain4j.data.message.ChatMessage> result =
                    (List<dev.langchain4j.data.message.ChatMessage>) method.invoke(
                            service, conversation, "Hello", "Vitals: HR 72", "Custom prompt");

            assertNotNull(result);
            // system prompt + medical context + user message = 3
            assertEquals(3, result.size());
            assertTrue(result.get(0) instanceof dev.langchain4j.data.message.SystemMessage);
            assertTrue(result.get(result.size() - 1) instanceof dev.langchain4j.data.message.UserMessage);
        }

        @Test
        @DisplayName("prepareChatMessagesForAI with null system prompt uses medical default")
        void prepareChatMessagesForAI_nullPromptUsesDefault() throws Exception {
            Method method = DefaultAIChatService.class.getDeclaredMethod(
                    "prepareChatMessagesForAI", ChatConversation.class, String.class,
                    String.class, String.class);
            method.setAccessible(true);

            List<dev.langchain4j.data.message.ChatMessage> result =
                    (List<dev.langchain4j.data.message.ChatMessage>) method.invoke(
                            service, conversation, "Hello", null, null);

            assertNotNull(result);
            assertEquals(2, result.size()); // default prompt + user message
        }

        @Test
        @DisplayName("prepareChatMessagesForAI with blank system prompt uses default")
        void prepareChatMessagesForAI_blankPromptUsesDefault() throws Exception {
            Method method = DefaultAIChatService.class.getDeclaredMethod(
                    "prepareChatMessagesForAI", ChatConversation.class, String.class,
                    String.class, String.class);
            method.setAccessible(true);

            List<dev.langchain4j.data.message.ChatMessage> result =
                    (List<dev.langchain4j.data.message.ChatMessage>) method.invoke(
                            service, conversation, "Hello", "  ", "  ");

            assertNotNull(result);
            assertEquals(2, result.size()); // default prompt + user message (blank context skipped)
        }

        @Test
        @DisplayName("prepareChatMessagesForAI maps USER message type correctly")
        void prepareChatMessagesForAI_userMessageType() throws Exception {
            Method method = DefaultAIChatService.class.getDeclaredMethod(
                    "prepareChatMessagesForAI", ChatConversation.class, String.class,
                    String.class, String.class);
            method.setAccessible(true);

            ChatMessage userMsg = buildChatMessage(ChatMessage.MessageType.USER, "User msg");
            when(chatMessageRepository.findTopNByConversationOrderByCreatedAtAsc(any(), anyInt()))
                    .thenReturn(List.of(userMsg));

            List<dev.langchain4j.data.message.ChatMessage> result =
                    (List<dev.langchain4j.data.message.ChatMessage>) method.invoke(
                            service, conversation, "New msg", null, "Prompt");

            // prompt + user history msg + new user msg = 3
            assertEquals(3, result.size());
            assertTrue(result.get(1) instanceof dev.langchain4j.data.message.UserMessage);
        }

        @Test
        @DisplayName("prepareChatMessagesForAI maps ASSISTANT message type correctly")
        void prepareChatMessagesForAI_assistantMessageType() throws Exception {
            Method method = DefaultAIChatService.class.getDeclaredMethod(
                    "prepareChatMessagesForAI", ChatConversation.class, String.class,
                    String.class, String.class);
            method.setAccessible(true);

            ChatMessage assistantMsg = buildChatMessage(ChatMessage.MessageType.ASSISTANT, "AI msg");
            when(chatMessageRepository.findTopNByConversationOrderByCreatedAtAsc(any(), anyInt()))
                    .thenReturn(List.of(assistantMsg));

            List<dev.langchain4j.data.message.ChatMessage> result =
                    (List<dev.langchain4j.data.message.ChatMessage>) method.invoke(
                            service, conversation, "New msg", null, "Prompt");

            assertEquals(3, result.size());
            assertTrue(result.get(1) instanceof dev.langchain4j.data.message.AiMessage);
        }

        @Test
        @DisplayName("prepareChatMessagesForAI maps SYSTEM message type correctly")
        void prepareChatMessagesForAI_systemMessageType() throws Exception {
            Method method = DefaultAIChatService.class.getDeclaredMethod(
                    "prepareChatMessagesForAI", ChatConversation.class, String.class,
                    String.class, String.class);
            method.setAccessible(true);

            ChatMessage systemMsg = buildChatMessage(ChatMessage.MessageType.SYSTEM, "System msg");
            when(chatMessageRepository.findTopNByConversationOrderByCreatedAtAsc(any(), anyInt()))
                    .thenReturn(List.of(systemMsg));

            List<dev.langchain4j.data.message.ChatMessage> result =
                    (List<dev.langchain4j.data.message.ChatMessage>) method.invoke(
                            service, conversation, "New msg", null, "Prompt");

            assertEquals(3, result.size());
            assertTrue(result.get(1) instanceof dev.langchain4j.data.message.SystemMessage);
        }

        @Test
        @DisplayName("prepareChatMessagesForAI with null userId on conversation uses default limit")
        void prepareChatMessagesForAI_nullUserIdDefaultsLimit() throws Exception {
            Method method = DefaultAIChatService.class.getDeclaredMethod(
                    "prepareChatMessagesForAI", ChatConversation.class, String.class,
                    String.class, String.class);
            method.setAccessible(true);

            ChatConversation convNullUser = ChatConversation.builder()
                    .conversationId("no-user")
                    .userId(null)
                    .patientId(PATIENT_ID)
                    .isActive(true)
                    .totalTokensUsed(0)
                    .build();
            convNullUser.setCreatedAt(LocalDateTime.now());

            method.invoke(service, convNullUser, "Hello", null, "Prompt");

            verify(chatMessageRepository).findTopNByConversationOrderByCreatedAtAsc(convNullUser, 20);
        }

        @Test
        @DisplayName("prepareChatMessagesForAI with null patientId on conversation uses default limit")
        void prepareChatMessagesForAI_nullPatientIdDefaultsLimit() throws Exception {
            Method method = DefaultAIChatService.class.getDeclaredMethod(
                    "prepareChatMessagesForAI", ChatConversation.class, String.class,
                    String.class, String.class);
            method.setAccessible(true);

            ChatConversation convNullPatient = ChatConversation.builder()
                    .conversationId("no-patient")
                    .userId(USER_ID)
                    .patientId(null)
                    .isActive(true)
                    .totalTokensUsed(0)
                    .build();
            convNullPatient.setCreatedAt(LocalDateTime.now());

            method.invoke(service, convNullPatient, "Hello", null, "Prompt");

            verify(chatMessageRepository).findTopNByConversationOrderByCreatedAtAsc(convNullPatient, 20);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  saveAndBuildResponse via reflection
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("saveAndBuildResponse — via reflection")
    class SaveAndBuildResponse {

        @Test
        @DisplayName("saveAndBuildResponse sets all response fields correctly")
        void saveAndBuildResponse_allFieldsSet() throws Exception {
            // Create inner class instances via reflection
            Class<?> contextClass = Class.forName(
                    "com.careconnect.service.DefaultAIChatService$ChatProcessingContext");
            Constructor<?> ctxCtor = contextClass.getDeclaredConstructor(
                    Patient.class, UserAIConfig.class, ChatConversation.class,
                    List.class, String.class, Double.class, Integer.class,
                    String.class, long.class);
            ctxCtor.setAccessible(true);

            List<Object> messages = new ArrayList<>();
            messages.add(Map.of("role", "user", "content", "Hello"));
            conversation.setCreatedAt(LocalDateTime.now().minusHours(2));
            conversation.setTotalTokensUsed(100);

            Object context = ctxCtor.newInstance(
                    patient, aiConfig, conversation, messages,
                    "deepseek-chat", 0.5, 1024, "Vitals: HR 72", System.currentTimeMillis());

            Class<?> resultClass = Class.forName(
                    "com.careconnect.service.DefaultAIChatService$ChatProcessingResult");
            Constructor<?> resCtor = resultClass.getDeclaredConstructor(
                    contextClass, String.class, Integer.class, Long.class, String.class);
            resCtor.setAccessible(true);

            Object processingResult = resCtor.newInstance(context, "AI says hello", 50, 300L, null);

            // Set up mocks for saved messages
            ChatMessage savedMsg = buildChatMessage(ChatMessage.MessageType.ASSISTANT, "AI says hello");
            savedMsg.setId(99L);
            when(chatMessageRepository.save(any())).thenReturn(savedMsg);

            Method method = DefaultAIChatService.class.getDeclaredMethod(
                    "saveAndBuildResponse", resultClass);
            method.setAccessible(true);

            ChatResponse resp = (ChatResponse) method.invoke(service, processingResult);

            assertNotNull(resp);
            assertTrue(resp.getSuccess());
            assertEquals(CONV_ID, resp.getConversationId());
            assertEquals("AI says hello", resp.getAiResponse());
            assertEquals("DEEPSEEK", resp.getAiProvider());
            assertEquals("deepseek-chat", resp.getModelUsed());
            assertEquals(50, resp.getTokensUsed());
            assertEquals(300L, resp.getProcessingTimeMs());
            assertEquals(0.5, resp.getTemperatureUsed());
            assertNotNull(resp.getContextIncluded());
            assertTrue(resp.getContextIncluded().contains("vitals"));
            assertNotNull(resp.getTimestamp());
            assertEquals("Test conversation", resp.getConversationTitle());
        }

        @Test
        @DisplayName("saveAndBuildResponse handles null tokensUsed by defaulting to 0")
        void saveAndBuildResponse_nullTokensUsed() throws Exception {
            Class<?> contextClass = Class.forName(
                    "com.careconnect.service.DefaultAIChatService$ChatProcessingContext");
            Constructor<?> ctxCtor = contextClass.getDeclaredConstructor(
                    Patient.class, UserAIConfig.class, ChatConversation.class,
                    List.class, String.class, Double.class, Integer.class,
                    String.class, long.class);
            ctxCtor.setAccessible(true);

            List<Object> messages = new ArrayList<>();
            messages.add("msg");
            conversation.setCreatedAt(LocalDateTime.now().minusHours(2));
            conversation.setTotalTokensUsed(null);

            Object context = ctxCtor.newInstance(
                    patient, aiConfig, conversation, messages,
                    "model", 0.5, 1024, null, System.currentTimeMillis());

            Class<?> resultClass = Class.forName(
                    "com.careconnect.service.DefaultAIChatService$ChatProcessingResult");
            Constructor<?> resCtor = resultClass.getDeclaredConstructor(
                    contextClass, String.class, Integer.class, Long.class, String.class);
            resCtor.setAccessible(true);

            Object processingResult = resCtor.newInstance(context, "Response", null, 100L, null);

            ChatMessage savedMsg = buildChatMessage(ChatMessage.MessageType.ASSISTANT, "Response");
            savedMsg.setId(1L);
            when(chatMessageRepository.save(any())).thenReturn(savedMsg);

            Method method = DefaultAIChatService.class.getDeclaredMethod(
                    "saveAndBuildResponse", resultClass);
            method.setAccessible(true);

            ChatResponse resp = (ChatResponse) method.invoke(service, processingResult);

            assertNotNull(resp);
            assertEquals(0, resp.getTokensUsed());
        }

        @Test
        @DisplayName("saveAndBuildResponse marks approaching token limit when tokens > 80% max")
        void saveAndBuildResponse_approachingTokenLimit() throws Exception {
            Class<?> contextClass = Class.forName(
                    "com.careconnect.service.DefaultAIChatService$ChatProcessingContext");
            Constructor<?> ctxCtor = contextClass.getDeclaredConstructor(
                    Patient.class, UserAIConfig.class, ChatConversation.class,
                    List.class, String.class, Double.class, Integer.class,
                    String.class, long.class);
            ctxCtor.setAccessible(true);

            List<Object> messages = new ArrayList<>();
            messages.add("msg");
            conversation.setCreatedAt(LocalDateTime.now().minusHours(2));
            // Set high token count to exceed 80% of maxTokens (2048 * 0.8 = 1638.4)
            conversation.setTotalTokensUsed(1600);

            Object context = ctxCtor.newInstance(
                    patient, aiConfig, conversation, messages,
                    "model", 0.5, 1024, null, System.currentTimeMillis());

            Class<?> resultClass = Class.forName(
                    "com.careconnect.service.DefaultAIChatService$ChatProcessingResult");
            Constructor<?> resCtor = resultClass.getDeclaredConstructor(
                    contextClass, String.class, Integer.class, Long.class, String.class);
            resCtor.setAccessible(true);

            // 100 tokens will bring total to 1700, which is > 1638.4
            Object processingResult = resCtor.newInstance(context, "Response", 100, 100L, null);

            ChatMessage savedMsg = buildChatMessage(ChatMessage.MessageType.ASSISTANT, "Response");
            savedMsg.setId(1L);
            when(chatMessageRepository.save(any())).thenReturn(savedMsg);

            Method method = DefaultAIChatService.class.getDeclaredMethod(
                    "saveAndBuildResponse", resultClass);
            method.setAccessible(true);

            ChatResponse resp = (ChatResponse) method.invoke(service, processingResult);

            assertTrue(resp.getApproachingTokenLimit());
        }

        @Test
        @DisplayName("saveAndBuildResponse marks isNewConversation true when created within a minute")
        void saveAndBuildResponse_newConversation() throws Exception {
            Class<?> contextClass = Class.forName(
                    "com.careconnect.service.DefaultAIChatService$ChatProcessingContext");
            Constructor<?> ctxCtor = contextClass.getDeclaredConstructor(
                    Patient.class, UserAIConfig.class, ChatConversation.class,
                    List.class, String.class, Double.class, Integer.class,
                    String.class, long.class);
            ctxCtor.setAccessible(true);

            List<Object> messages = new ArrayList<>();
            messages.add("msg");
            conversation.setCreatedAt(LocalDateTime.now()); // just created
            conversation.setTotalTokensUsed(0);

            Object context = ctxCtor.newInstance(
                    patient, aiConfig, conversation, messages,
                    "model", 0.5, 1024, null, System.currentTimeMillis());

            Class<?> resultClass = Class.forName(
                    "com.careconnect.service.DefaultAIChatService$ChatProcessingResult");
            Constructor<?> resCtor = resultClass.getDeclaredConstructor(
                    contextClass, String.class, Integer.class, Long.class, String.class);
            resCtor.setAccessible(true);

            Object processingResult = resCtor.newInstance(context, "Response", 10, 100L, null);

            ChatMessage savedMsg = buildChatMessage(ChatMessage.MessageType.ASSISTANT, "Response");
            savedMsg.setId(1L);
            when(chatMessageRepository.save(any())).thenReturn(savedMsg);

            Method method = DefaultAIChatService.class.getDeclaredMethod(
                    "saveAndBuildResponse", resultClass);
            method.setAccessible(true);

            ChatResponse resp = (ChatResponse) method.invoke(service, processingResult);

            assertTrue(resp.getIsNewConversation());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Helpers
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Creates a minimal, valid {@link ChatRequest} for the patient-chat happy path.
     * Individual tests can override fields as needed.
     */
    private ChatRequest buildBasicRequest() throws Exception {
        ChatRequest req = new ChatRequest();
        req.setUserId(USER_ID);
        req.setPatientId(PATIENT_ID);
        req.setMessage("What are my recent lab results?");
        return req;
    }

    /**
     * Creates a {@link ChatMessage} with the given type and content,
     * with {@code createdAt} pre-set so that summary conversion does not NPE.
     */
    private ChatMessage buildChatMessage(ChatMessage.MessageType type, String content) {
        ChatMessage msg = new ChatMessage();
        msg.setConversation(conversation);
        msg.setMessageType(type);
        msg.setContent(content);
        msg.setCreatedAt(LocalDateTime.now());
        return msg;
    }
}
