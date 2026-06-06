package com.careconnect.service;

import com.careconnect.ai.AIService;
import com.careconnect.dto.ChatConversationSummary;
import com.careconnect.dto.ChatMessageSummary;
import com.careconnect.dto.ChatRequest;
import com.careconnect.dto.ChatResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

@Service
@ConditionalOnProperty(name = "careconnect.ai.provider", havingValue = "bedrock")
public class BedrockAIChatService implements AIService {

    private static final Logger log = LoggerFactory.getLogger(BedrockAIChatService.class);

    private final BedrockRuntimeClient client;

    public BedrockAIChatService() {
        this.client = BedrockRuntimeClient.builder()
                .region(Region.US_EAST_1)
                .build();
  }

  @Override
    public ChatResponse processChat(ChatRequest request) {

    log.info("Using Bedrock AI provider");

    String payload = """
        {
          "messages": [
            {
                "role": "user",
                "content": [
                  {
                    "text": "%s"
                  }
                ]
            }
          ],
            "inferenceConfig": {
            "maxTokens": 500,
            "temperature": 0.5,
            "topP": 0.9
          }
        }
        """.formatted(
            request.getMessage()
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
        );

        InvokeModelRequest invokeRequest = InvokeModelRequest.builder()
                .modelId("amazon.nova-lite-v1:0")
                .contentType("application/json")
                .accept("application/json")
                .body(SdkBytes.fromString(payload, StandardCharsets.UTF_8))
                .build();

    InvokeModelResponse response = client.invokeModel(invokeRequest);

        ObjectMapper mapper = new ObjectMapper();

        String raw = response.body().asUtf8String();

        JsonNode root;
        
        try {
            root = mapper.readTree(raw);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Bedrock response", e);
        }

        String aiText = root
                .path("output")
                .path("message")
                .path("content")
                .get(0)
                .path("text")
                .asText();

        ChatResponse chatResponse = new ChatResponse();
        chatResponse.setAiResponse(aiText);
        chatResponse.setSuccess(true);
        chatResponse.setTimestamp(LocalDateTime.now());

    return chatResponse;
  }

    // ===== Stubbed methods =====

    @Override
    public List<ChatConversationSummary> getPatientConversations(Long patientId) {
        throw new UnsupportedOperationException();
    }

  @Override
    public List<ChatMessageSummary> getConversationMessages(String conversationId) {
        throw new UnsupportedOperationException();
    }

  @Override
    public List<ChatMessageSummary> getRecentMessagesForUser(Long userId, int limit) {
        throw new UnsupportedOperationException();
    }

  @Override
    public void deactivateConversation(String conversationId) {
        throw new UnsupportedOperationException();
    }
}