package com.careconnect.service;

import com.careconnect.dto.ChatConversationSummary;
import com.careconnect.dto.ChatMessageSummary;
import com.careconnect.dto.ChatRequest;
import com.careconnect.dto.ChatResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

@Service
@ConditionalOnProperty(name = "careconnect.ai.provider", havingValue = "bedrock")
public class BedrockAIChatService implements AIChatService {

    private static final Logger log = LoggerFactory.getLogger(BedrockAIChatService.class);
    
    private final BedrockRuntimeClient client;

    public BedrockAIChatService() {
        this.client = BedrockRuntimeClient.builder()
                .region(Region.US_EAST_2) // match your region
                .build();
    }

    @Override
    public ChatResponse processChat(ChatRequest request) {

        log.info("Using Bedrock AI provider");

        String payload = """
        {
          "inputText": "%s"
        }
        """.formatted(request.getMessage());

        InvokeModelRequest invokeRequest = InvokeModelRequest.builder()
                .modelId("meta.llama3-8b-instruct-v1:0") // or titan
                .body(software.amazon.awssdk.core.SdkBytes.fromString(payload, StandardCharsets.UTF_8))
                .build();

        InvokeModelResponse response = client.invokeModel(invokeRequest);

        String result = response.body().asUtf8String();

        ChatResponse chatResponse = new ChatResponse();
        chatResponse.setAiResponse(result);
        chatResponse.setSuccess(true);
        chatResponse.setTimestamp(LocalDateTime.now());

        return chatResponse;
    }

    // You can stub other methods for now
    @Override
    public List<ChatConversationSummary> getPatientConversations(Long patientId) {
        throw new UnsupportedOperationException("Conversation history not supported in Bedrock mode yet.");
    }

    @Override
    public List<ChatMessageSummary> getConversationMessages(String conversationId) {
        throw new UnsupportedOperationException("Conversation history not supported in Bedrock mode yet.");
    }

    @Override
    public List<ChatMessageSummary> getRecentMessagesForUser(Long userId, int limit) {
        throw new UnsupportedOperationException("Conversation history not supported in Bedrock mode yet.");
    }

    @Override
    public void deactivateConversation(String conversationId) {
        throw new UnsupportedOperationException("Deactivate not supported in Bedrock mode yet.");
    }
}