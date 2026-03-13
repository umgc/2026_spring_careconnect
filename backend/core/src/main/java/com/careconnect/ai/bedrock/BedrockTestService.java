package com.careconnect.ai.bedrock;

import org.springframework.stereotype.Service;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

/**
 * Service responsible for invoking Meta Llama 3 via AWS Bedrock.
 */
@Service
public class BedrockTestService {

    private final BedrockRuntimeClient client;

    /**
     * Initialize Bedrock client using default AWS credentials
     * and the us-east-1 region.
     */
    public BedrockTestService() {
        this.client = BedrockRuntimeClient.builder()
                .region(Region.US_EAST_1)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    /**
     * Sends a prompt to Llama 3 and extracts the generated text.
     *
     * @param prompt User input
     * @return Generated model response
     */
    public String testPrompt(String prompt) {

        // Llama 3 request body format
        String body = """
        {
          "prompt": "<|begin_of_text|><|start_header_id|>user<|end_header_id|>\\n%s\\n<|eot_id|><|start_header_id|>assistant<|end_header_id|>",
          "max_gen_len": 200,
          "temperature": 0.5
        }
        """.formatted(prompt);

        //Log incoming log
        System.out.println("Calling Bedrock with prompt: " + prompt);

        InvokeModelRequest request = InvokeModelRequest.builder()
                .modelId("meta.llama3-8b-instruct-v1:0")
                .contentType("application/json")
                .accept("application/json")
                .body(
                        software.amazon.awssdk.core.SdkBytes
                                .fromString(body, StandardCharsets.UTF_8)
                )
                .build();

        InvokeModelResponse response = client.invokeModel(request);

        String json = response.body().asUtf8String();

        //Log raw JSON returned from bedrock
        System.out.println("Raw Bedrock JSON response: " + json);

        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(json);

            // Llama returns:
            // { "generation": "model output text" }
            return root.path("generation").asText().trim();

        } catch (Exception e) {
            return "Failed to parse model response: " + json;
        }
    }
}