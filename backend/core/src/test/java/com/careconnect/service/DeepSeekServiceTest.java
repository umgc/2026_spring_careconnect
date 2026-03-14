package com.careconnect.service;

import com.careconnect.service.DeepSeekService.Choice;
import com.careconnect.service.DeepSeekService.DeepSeekChatRequest;
import com.careconnect.service.DeepSeekService.DeepSeekException;
import com.careconnect.service.DeepSeekService.DeepSeekResponse;
import com.careconnect.service.DeepSeekService.Message;
import com.careconnect.service.DeepSeekService.Usage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.RETURNS_SELF;

/**
 * Unit tests for {@link DeepSeekService}.
 *
 * <p>The service creates a {@link RestClient} in its constructor.
 * Tests inject a mock {@code RestClient} (with deep stubs for the fluent API)
 * via {@link ReflectionTestUtils#setField} after construction.
 */
class DeepSeekServiceTest {

    private DeepSeekService service;
    private RestClient mockRestClient;
    private RestClient.RequestBodyUriSpec mockPost;
    private RestClient.RequestBodySpec mockBodySpec;
    private RestClient.ResponseSpec mockRetrieve;

    @BeforeEach
    void setUp() throws Exception {
        // Construct with a dummy key/url so RestClient.builder() succeeds
        service = new DeepSeekService("test-api-key", "https://api.deepseek.com/v1");

        // Build mock chain for RestClient fluent API.
        // Use a single RequestBodySpec mock for the entire fluent chain
        // (uri -> contentType -> body all return RequestBodySpec).
        mockRestClient = mock(RestClient.class);
        mockPost = mock(RestClient.RequestBodyUriSpec.class);
        mockBodySpec = mock(RestClient.RequestBodySpec.class, RETURNS_SELF);
        mockRetrieve = mock(RestClient.ResponseSpec.class);

        when(mockRestClient.post()).thenReturn(mockPost);
        when(mockPost.uri(anyString())).thenReturn(mockBodySpec);
        when(mockBodySpec.retrieve()).thenReturn(mockRetrieve);

        // Inject mock RestClient
        ReflectionTestUtils.setField(service, "restClient", mockRestClient);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Helper methods
    // ═══════════════════════════════════════════════════════════════════════

    private DeepSeekChatRequest basicRequest() throws Exception {
        final DeepSeekChatRequest req = new DeepSeekChatRequest();
        req.setModel("deepseek-chat");
        req.setMessages(List.of(new Message("user", "hello")));
        req.setTemperature(0.2);
        req.setMaxTokens(256);
        req.setStream(false);
        return req;
    }

    private DeepSeekResponse buildResponse() throws Exception {
        final Message msg = new Message("assistant", "Hello, how can I help?");
        final Choice choice = new Choice();
        choice.setIndex(0);
        choice.setMessage(msg);
        choice.setFinishReason("stop");

        final Usage usage = new Usage();
        usage.setPromptTokens(10);
        usage.setCompletionTokens(5);
        usage.setTotalTokens(15);

        final DeepSeekResponse response = new DeepSeekResponse();
        response.setId("resp-1");
        response.setObject("chat.completion");
        response.setCreated(1700000000L);
        response.setModel("deepseek-chat");
        response.setChoices(List.of(choice));
        response.setUsage(usage);
        return response;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // sendChatRequest — API key validation
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("sendChatRequest — API key validation")
    class ApiKeyValidation {

        @Test
        @DisplayName("sendChatRequest_nullApiKey_throwsIllegalStateException")
        void sendChatRequest_nullApiKey_throwsIllegalStateException() throws Exception {
            ReflectionTestUtils.setField(service, "apiKey", null);

            assertThatThrownBy(() -> service.sendChatRequest(basicRequest()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("DeepSeek API key is not configured");
        }

        @Test
        @DisplayName("sendChatRequest_emptyApiKey_throwsIllegalStateException")
        void sendChatRequest_emptyApiKey_throwsIllegalStateException() throws Exception {
            ReflectionTestUtils.setField(service, "apiKey", "");

            assertThatThrownBy(() -> service.sendChatRequest(basicRequest()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("DeepSeek API key is not configured");
        }

        @Test
        @DisplayName("sendChatRequest_blankApiKey_throwsIllegalStateException")
        void sendChatRequest_blankApiKey_throwsIllegalStateException() throws Exception {
            ReflectionTestUtils.setField(service, "apiKey", "   ");

            assertThatThrownBy(() -> service.sendChatRequest(basicRequest()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("DeepSeek API key is not configured");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // sendChatRequest — successful call
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("sendChatRequest — successful call")
    class SuccessfulCall {

        @Test
        @DisplayName("sendChatRequest_validRequest_returnsDeepSeekResponse")
        void sendChatRequest_validRequest_returnsDeepSeekResponse() throws Exception {
            final DeepSeekResponse expected = buildResponse();
            when(mockRetrieve.body(DeepSeekResponse.class)).thenReturn(expected);

            final DeepSeekResponse result = service.sendChatRequest(basicRequest());

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo("resp-1");
            assertThat(result.getModel()).isEqualTo("deepseek-chat");
            assertThat(result.getChoices()).hasSize(1);
            assertThat(result.getChoices().get(0).getMessage().getContent())
                    .isEqualTo("Hello, how can I help?");
            assertThat(result.getUsage().getTotalTokens()).isEqualTo(15);
        }

        @Test
        @DisplayName("sendChatRequest_validRequest_callsRestClientPost")
        void sendChatRequest_validRequest_callsRestClientPost() throws Exception {
            when(mockRetrieve.body(DeepSeekResponse.class)).thenReturn(buildResponse());

            service.sendChatRequest(basicRequest());

            verify(mockRestClient).post();
            verify(mockPost).uri("/chat/completions");
            verify(mockBodySpec).contentType(MediaType.APPLICATION_JSON);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // sendChatRequest — RestClientResponseException handling
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("sendChatRequest — RestClientResponseException handling")
    class RestClientResponseExceptionHandling {

        @Test
        @DisplayName("sendChatRequest_httpError_throwsDeepSeekExceptionWithStatusCode")
        void sendChatRequest_httpError_throwsDeepSeekExceptionWithStatusCode() throws Exception {
            final RestClientResponseException httpEx = new RestClientResponseException(
                    "Bad Request",
                    HttpStatusCode.valueOf(400),
                    "Bad Request",
                    null,
                    "Invalid model parameter".getBytes(StandardCharsets.UTF_8),
                    StandardCharsets.UTF_8
            );
            when(mockRetrieve.body(DeepSeekResponse.class)).thenThrow(httpEx);

            assertThatThrownBy(() -> service.sendChatRequest(basicRequest()))
                    .isInstanceOf(DeepSeekException.class)
                    .hasMessageContaining("DeepSeek call failed: 400")
                    .hasCause(httpEx);
        }

        @Test
        @DisplayName("sendChatRequest_serverError_throwsDeepSeekExceptionWith500")
        void sendChatRequest_serverError_throwsDeepSeekExceptionWith500() throws Exception {
            final RestClientResponseException serverEx = new RestClientResponseException(
                    "Internal Server Error",
                    HttpStatusCode.valueOf(500),
                    "Internal Server Error",
                    null,
                    "Server crashed".getBytes(StandardCharsets.UTF_8),
                    StandardCharsets.UTF_8
            );
            when(mockRetrieve.body(DeepSeekResponse.class)).thenThrow(serverEx);

            assertThatThrownBy(() -> service.sendChatRequest(basicRequest()))
                    .isInstanceOf(DeepSeekException.class)
                    .hasMessageContaining("DeepSeek call failed: 500")
                    .hasCause(serverEx);
        }

        @Test
        @DisplayName("sendChatRequest_unauthorizedError_throwsDeepSeekExceptionWith401")
        void sendChatRequest_unauthorizedError_throwsDeepSeekExceptionWith401() throws Exception {
            final RestClientResponseException authEx = new RestClientResponseException(
                    "Unauthorized",
                    HttpStatusCode.valueOf(401),
                    "Unauthorized",
                    null,
                    "Invalid API key".getBytes(StandardCharsets.UTF_8),
                    StandardCharsets.UTF_8
            );
            when(mockRetrieve.body(DeepSeekResponse.class)).thenThrow(authEx);

            assertThatThrownBy(() -> service.sendChatRequest(basicRequest()))
                    .isInstanceOf(DeepSeekException.class)
                    .hasMessageContaining("DeepSeek call failed: 401");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // sendChatRequest — generic Exception handling
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("sendChatRequest — generic Exception handling")
    class GenericExceptionHandling {

        @Test
        @DisplayName("sendChatRequest_unexpectedException_throwsDeepSeekException")
        void sendChatRequest_unexpectedException_throwsDeepSeekException() throws Exception {
            final RuntimeException unexpected = new RuntimeException("connection timeout");
            when(mockRetrieve.body(DeepSeekResponse.class)).thenThrow(unexpected);

            assertThatThrownBy(() -> service.sendChatRequest(basicRequest()))
                    .isInstanceOf(DeepSeekException.class)
                    .hasMessageContaining("DeepSeek call error")
                    .hasCause(unexpected);
        }

        @Test
        @DisplayName("sendChatRequest_nullPointerException_throwsDeepSeekException")
        void sendChatRequest_nullPointerException_throwsDeepSeekException() throws Exception {
            final NullPointerException npe = new NullPointerException("null response");
            when(mockRetrieve.body(DeepSeekResponse.class)).thenThrow(npe);

            assertThatThrownBy(() -> service.sendChatRequest(basicRequest()))
                    .isInstanceOf(DeepSeekException.class)
                    .hasMessageContaining("DeepSeek call error")
                    .hasCause(npe);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // buildChatRequest
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("buildChatRequest")
    class BuildChatRequest {

        @Test
        @DisplayName("buildChatRequest_validPrompts_returnsCorrectlyConfiguredRequest")
        void buildChatRequest_validPrompts_returnsCorrectlyConfiguredRequest() throws Exception {
            final DeepSeekChatRequest result = service.buildChatRequest(
                    "You are a medical assistant.",
                    "What causes headaches?"
            );

            assertThat(result.getModel()).isEqualTo("deepseek-chat");
            assertThat(result.getTemperature()).isEqualTo(0.2);
            assertThat(result.getMaxTokens()).isEqualTo(256);
            assertThat(result.getStream()).isFalse();
            assertThat(result.getMessages()).hasSize(2);
        }

        @Test
        @DisplayName("buildChatRequest_validPrompts_systemMessageFirst")
        void buildChatRequest_validPrompts_systemMessageFirst() throws Exception {
            final DeepSeekChatRequest result = service.buildChatRequest(
                    "system prompt here",
                    "user prompt here"
            );

            final Message systemMsg = result.getMessages().get(0);
            assertThat(systemMsg.getRole()).isEqualTo("system");
            assertThat(systemMsg.getContent()).isEqualTo("system prompt here");
        }

        @Test
        @DisplayName("buildChatRequest_validPrompts_userMessageSecond")
        void buildChatRequest_validPrompts_userMessageSecond() throws Exception {
            final DeepSeekChatRequest result = service.buildChatRequest(
                    "system prompt",
                    "What should I eat?"
            );

            final Message userMsg = result.getMessages().get(1);
            assertThat(userMsg.getRole()).isEqualTo("user");
            assertThat(userMsg.getContent()).isEqualTo("What should I eat?");
        }

        @Test
        @DisplayName("buildChatRequest_emptyPrompts_setsEmptyStrings")
        void buildChatRequest_emptyPrompts_setsEmptyStrings() throws Exception {
            final DeepSeekChatRequest result = service.buildChatRequest("", "");

            assertThat(result.getMessages().get(0).getContent()).isEmpty();
            assertThat(result.getMessages().get(1).getContent()).isEmpty();
        }

        @Test
        @DisplayName("buildChatRequest_nullPrompts_setsNullContent")
        void buildChatRequest_nullPrompts_setsNullContent() throws Exception {
            final DeepSeekChatRequest result = service.buildChatRequest(null, null);

            assertThat(result.getMessages().get(0).getContent()).isNull();
            assertThat(result.getMessages().get(1).getContent()).isNull();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // DTO tests
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("DTO coverage")
    class DtoCoverage {

        @Test
        @DisplayName("deepSeekChatRequest_defaultStream_isFalse")
        void deepSeekChatRequest_defaultStream_isFalse() throws Exception {
            final DeepSeekChatRequest req = new DeepSeekChatRequest();
            assertThat(req.getStream()).isFalse();
        }

        @Test
        @DisplayName("deepSeekChatRequest_settersAndGetters_workCorrectly")
        void deepSeekChatRequest_settersAndGetters_workCorrectly() throws Exception {
            final DeepSeekChatRequest req = new DeepSeekChatRequest();
            req.setModel("test-model");
            req.setMessages(List.of(new Message("user", "hi")));
            req.setTemperature(0.5);
            req.setMaxTokens(100);
            req.setStream(true);

            assertThat(req.getModel()).isEqualTo("test-model");
            assertThat(req.getMessages()).hasSize(1);
            assertThat(req.getTemperature()).isEqualTo(0.5);
            assertThat(req.getMaxTokens()).isEqualTo(100);
            assertThat(req.getStream()).isTrue();
        }

        @Test
        @DisplayName("message_noArgsConstructor_createsEmptyMessage")
        void message_noArgsConstructor_createsEmptyMessage() throws Exception {
            final Message msg = new Message();
            assertThat(msg.getRole()).isNull();
            assertThat(msg.getContent()).isNull();
        }

        @Test
        @DisplayName("message_allArgsConstructor_setsFields")
        void message_allArgsConstructor_setsFields() throws Exception {
            final Message msg = new Message("assistant", "Hello");
            assertThat(msg.getRole()).isEqualTo("assistant");
            assertThat(msg.getContent()).isEqualTo("Hello");
        }

        @Test
        @DisplayName("message_settersAndGetters_workCorrectly")
        void message_settersAndGetters_workCorrectly() throws Exception {
            final Message msg = new Message();
            msg.setRole("system");
            msg.setContent("Be helpful");
            assertThat(msg.getRole()).isEqualTo("system");
            assertThat(msg.getContent()).isEqualTo("Be helpful");
        }

        @Test
        @DisplayName("deepSeekResponse_noArgsConstructor_createsEmptyResponse")
        void deepSeekResponse_noArgsConstructor_createsEmptyResponse() throws Exception {
            final DeepSeekResponse resp = new DeepSeekResponse();
            assertThat(resp.getId()).isNull();
            assertThat(resp.getObject()).isNull();
            assertThat(resp.getCreated()).isNull();
            assertThat(resp.getModel()).isNull();
            assertThat(resp.getChoices()).isNull();
            assertThat(resp.getUsage()).isNull();
        }

        @Test
        @DisplayName("deepSeekResponse_settersAndGetters_workCorrectly")
        void deepSeekResponse_settersAndGetters_workCorrectly() throws Exception {
            final DeepSeekResponse resp = new DeepSeekResponse();
            resp.setId("r-1");
            resp.setObject("chat.completion");
            resp.setCreated(1234567890L);
            resp.setModel("deepseek-chat");
            resp.setChoices(List.of());
            resp.setUsage(new Usage());

            assertThat(resp.getId()).isEqualTo("r-1");
            assertThat(resp.getObject()).isEqualTo("chat.completion");
            assertThat(resp.getCreated()).isEqualTo(1234567890L);
            assertThat(resp.getModel()).isEqualTo("deepseek-chat");
            assertThat(resp.getChoices()).isEmpty();
            assertThat(resp.getUsage()).isNotNull();
        }

        @Test
        @DisplayName("choice_settersAndGetters_workCorrectly")
        void choice_settersAndGetters_workCorrectly() throws Exception {
            final Choice choice = new Choice();
            choice.setIndex(0);
            choice.setMessage(new Message("assistant", "OK"));
            choice.setFinishReason("stop");

            assertThat(choice.getIndex()).isZero();
            assertThat(choice.getMessage().getContent()).isEqualTo("OK");
            assertThat(choice.getFinishReason()).isEqualTo("stop");
        }

        @Test
        @DisplayName("usage_settersAndGetters_workCorrectly")
        void usage_settersAndGetters_workCorrectly() throws Exception {
            final Usage usage = new Usage();
            usage.setPromptTokens(10);
            usage.setCompletionTokens(20);
            usage.setTotalTokens(30);

            assertThat(usage.getPromptTokens()).isEqualTo(10);
            assertThat(usage.getCompletionTokens()).isEqualTo(20);
            assertThat(usage.getTotalTokens()).isEqualTo(30);
        }

        @Test
        @DisplayName("deepSeekException_constructorWithCause_setsMessageAndCause")
        void deepSeekException_constructorWithCause_setsMessageAndCause() throws Exception {
            final RuntimeException cause = new RuntimeException("root cause");
            final DeepSeekException ex = new DeepSeekException("test message", cause);

            assertThat(ex.getMessage()).isEqualTo("test message");
            assertThat(ex.getCause()).isEqualTo(cause);
        }

        @Test
        @DisplayName("deepSeekException_isRuntimeException")
        void deepSeekException_isRuntimeException() throws Exception {
            final DeepSeekException ex = new DeepSeekException("msg", new RuntimeException());
            assertThat(ex).isInstanceOf(RuntimeException.class);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Constructor
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {

        @Test
        @DisplayName("constructor_withValidKeyAndUrl_createsServiceSuccessfully")
        void constructor_withValidKeyAndUrl_createsServiceSuccessfully() throws Exception {
            final DeepSeekService svc = new DeepSeekService("my-key", "https://api.example.com/v1");
            assertThat(svc).isNotNull();
        }

        @Test
        @DisplayName("constructor_withNullApiKey_createsServiceWithEmptyBearerToken")
        void constructor_withNullApiKey_createsServiceWithEmptyBearerToken() throws Exception {
            // The constructor handles null apiKey by using empty string in the header
            final DeepSeekService svc = new DeepSeekService(null, "https://api.example.com/v1");
            assertThat(svc).isNotNull();
        }

        @Test
        @DisplayName("constructor_withEmptyApiKey_createsServiceSuccessfully")
        void constructor_withEmptyApiKey_createsServiceSuccessfully() throws Exception {
            final DeepSeekService svc = new DeepSeekService("", "https://api.example.com/v1");
            assertThat(svc).isNotNull();
        }
    }
}
