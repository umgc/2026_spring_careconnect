package com.careconnect.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * BedrockSentimentService — real-time sentiment analysis during video calls.
 *
 * Three analysis modes:
 *
 *   TEXT  — analyzes chat messages typed during the call
 *            Input: plain text string
 *
 *   VOICE — analyzes tone, stress, energy, and pace of speech
 *            Input: base64-encoded audio chunk (WAV/PCM, ~10-15 seconds)
 *            Uses: Voxtral model (audio-capable)
 *
 *   VIDEO — analyzes facial expressions and visual emotional cues
 *            Input: base64-encoded image frame (JPEG/PNG)
 *            Uses: Nova Pro model (image-capable, already validated)
 *
 * All three return a SentimentResult with:
 *   - score:    0.0 (very negative) to 1.0 (very positive)
 *   - label:    POSITIVE / NEUTRAL / NEGATIVE / DISTRESSED / ANXIOUS / CALM
 *   - notes:    brief clinical observation from the model
 *   - channel:  TEXT / VOICE / VIDEO
 */
@Slf4j
@Service
public class BedrockSentimentService {

    private final BedrockRuntimeClient bedrockRuntimeClient;
    private final ObjectMapper objectMapper;
    private final boolean awsEnabled;

    @Autowired
    public BedrockSentimentService(
            @Autowired(required = false) BedrockRuntimeClient bedrockRuntimeClient,
            ObjectMapper objectMapper,
            @Value("${careconnect.aws.enabled:true}") boolean awsEnabled) {
        this.bedrockRuntimeClient = bedrockRuntimeClient;
        this.objectMapper = objectMapper;
        this.awsEnabled = awsEnabled;
    }

    // Model IDs — configured in application-prod.properties
    // Nova Pro handles text + image (video frames)
    @Value("${aws.bedrock.sentiment.model-id:amazon.nova-pro-v1:0}")
    private String novaProModelId;

    // Voxtral handles audio (voice tone analysis)
    @Value("${aws.bedrock.voice.model-id:mistral.voxtral-small-24b-2507}")
    private String voxtralModelId;

    // ================================================================
    // TEXT SENTIMENT
    // Analyzes chat messages typed during the call
    // ================================================================

    /**
     * Analyzes the emotional tone of a text message.
     * Called whenever a chat message is sent during a call.
     */
    public SentimentResult analyzeText(String text, String callId) {
        log.debug("Analyzing text sentiment for callId: {}", callId);

        if (!isBedrockAvailable()) {
            return SentimentResult.neutral("TEXT", callId, "Bedrock disabled in local mode");
        }

        String prompt = """
            You are a clinical sentiment analyzer for a healthcare communication platform.
            Analyze the emotional state expressed in this message from a healthcare call.
            
            Message: "%s"
            
            Respond with ONLY a JSON object in this exact format, no other text:
            {
              "score": <number between 0.0 and 1.0 where 0=very negative, 0.5=neutral, 1.0=very positive>,
              "label": "<one of: POSITIVE, NEUTRAL, NEGATIVE, DISTRESSED, ANXIOUS, CALM>",
              "notes": "<one brief clinical observation, max 10 words>"
            }
            """.formatted(text);

        try {
            String responseBody = invokeNovaModel(prompt, null, null);
            return parseSentimentResponse(responseBody, "TEXT", callId);
        } catch (Exception e) {
            log.error("Text sentiment analysis failed for callId: {}", callId, e);
            return SentimentResult.neutral("TEXT", callId, "Analysis unavailable");
        }
    }

    // ================================================================
    // VOICE SENTIMENT
    // Analyzes tone, stress, energy, and pace of speech audio
    // ================================================================

    /**
     * Analyzes vocal tone and emotional cues from an audio chunk.
     *
     * @param audioBase64 base64-encoded audio data (WAV format, 10-15 seconds)
     * @param callId      the active call session ID
     */
    public SentimentResult analyzeVoice(String audioBase64, String callId) {
        log.debug("Analyzing voice sentiment for callId: {}", callId);

        if (!isBedrockAvailable()) {
            return SentimentResult.neutral("VOICE", callId, "Bedrock disabled in local mode");
        }

        String prompt = """
            You are a clinical voice tone analyzer for a healthcare platform.
            Analyze this audio clip for emotional and physiological vocal cues.
            
            Focus on:
            - Tone (warm, cold, flat, expressive)
            - Stress indicators (voice cracking, tension, strain)
            - Speech pace (rushed = anxious, slow = depressed or fatigued)
            - Energy level (high, normal, low)
            - Emotional state (calm, distressed, anxious, content, sad)
            
            Respond with ONLY a JSON object in this exact format, no other text:
            {
              "score": <number between 0.0 and 1.0 where 0=very distressed, 0.5=neutral, 1.0=very calm/positive>,
              "label": "<one of: POSITIVE, NEUTRAL, NEGATIVE, DISTRESSED, ANXIOUS, CALM>",
              "notes": "<one brief clinical observation about vocal quality, max 10 words>"
            }
            """;

        try {
            String responseBody = invokeVoxtralModel(prompt, audioBase64);
            return parseSentimentResponse(responseBody, "VOICE", callId);
        } catch (Exception e) {
            log.error("Voice sentiment analysis failed for callId: {}", callId, e);
            return SentimentResult.neutral("VOICE", callId, "Audio analysis unavailable");
        }
    }

    // ================================================================
    // VIDEO SENTIMENT
    // Analyzes facial expressions and visual emotional cues from a frame
    // ================================================================

    /**
     * Analyzes facial expressions and visual emotional cues from a video frame.
     *
     * @param imageBase64  base64-encoded image (JPEG or PNG, single frame)
     * @param imageFormat  "jpeg" or "png"
     * @param callId       the active call session ID
     */
    public SentimentResult analyzeVideoFrame(String imageBase64, String imageFormat, String callId) {
        log.debug("Analyzing video frame sentiment for callId: {}", callId);

        if (!isBedrockAvailable()) {
            return SentimentResult.neutral("VIDEO", callId, "Bedrock disabled in local mode");
        }

        String prompt = """
            You are a clinical facial expression analyzer for a healthcare platform.
            Analyze this video frame for emotional and wellbeing cues.
            
            Focus on:
            - Facial expression (smile, frown, neutral, grimace, tense)
            - Eye contact and engagement
            - Visible signs of discomfort, pain, fatigue, or distress
            - Overall emotional state
            
            This is for clinical monitoring — be precise and objective.
            
            Respond with ONLY a JSON object in this exact format, no other text:
            {
              "score": <number between 0.0 and 1.0 where 0=very distressed, 0.5=neutral, 1.0=very positive>,
              "label": "<one of: POSITIVE, NEUTRAL, NEGATIVE, DISTRESSED, ANXIOUS, CALM>",
              "notes": "<one brief clinical observation about visible emotional state, max 10 words>"
            }
            """;

        try {
            String responseBody = invokeNovaModel(prompt, imageBase64, imageFormat);
            return parseSentimentResponse(responseBody, "VIDEO", callId);
        } catch (Exception e) {
            log.error("Video sentiment analysis failed for callId: {}", callId, e);
            return SentimentResult.neutral("VIDEO", callId, "Video analysis unavailable");
        }
    }

    // ================================================================
    // COMBINED SENTIMENT
    // Aggregates all three channels into a single overall score
    // Called periodically during a call to update the live graph
    // ================================================================

    /**
     * Combines text, voice, and video sentiment into an overall score.
     * Weights: voice 40%, video 40%, text 20%
     * (Voice and video are more reliable indicators than text alone)
     */
    public Map<String, Object> buildCombinedSentiment(
            SentimentResult textResult,
            SentimentResult voiceResult,
            SentimentResult videoResult,
            String callId) {

        if (textResult == null) {
            textResult = SentimentResult.neutral("TEXT", callId, "No text sample");
        }
        if (voiceResult == null) {
            voiceResult = SentimentResult.neutral("VOICE", callId, "No voice sample");
        }
        if (videoResult == null) {
            videoResult = SentimentResult.neutral("VIDEO", callId, "No video sample");
        }

        // Weighted average — voice and video carry more clinical weight
        double combined = (textResult.score() * 0.2)
                        + (voiceResult.score() * 0.4)
                        + (videoResult.score() * 0.4);

        String overallLabel = scoreToLabel(combined);

        Map<String, Object> result = new HashMap<>();
        result.put("callId",        callId);
        result.put("timestamp",     System.currentTimeMillis());
        result.put("overall",       Map.of(
            "score", Math.round(combined * 100.0) / 100.0,
            "label", overallLabel
        ));
        result.put("text",  Map.of("score", textResult.score(),  "label", textResult.label(),  "notes", textResult.notes()));
        result.put("voice", Map.of("score", voiceResult.score(), "label", voiceResult.label(), "notes", voiceResult.notes()));
        result.put("video", Map.of("score", videoResult.score(), "label", videoResult.label(), "notes", videoResult.notes()));

        return result;
    }

    // ================================================================
    // PRIVATE — AWS BEDROCK INVOCATION
    // ================================================================

    /**
     * Invokes Amazon Nova Pro for text or image analysis.
     * Nova Pro supports: text input, image input, or both together.
     */
    private String invokeNovaModel(String prompt, String imageBase64, String imageFormat) throws Exception {
        Map<String, Object> requestBody = new HashMap<>();

        // Build content array — text only, or text + image
        Map<String, Object> userMessage = new HashMap<>();
        if (imageBase64 != null && imageFormat != null) {
            // Image + text request
            userMessage.put("role", "user");
            userMessage.put("content", List.of(
                Map.of("image", Map.of(
                    "format", imageFormat,
                    "source", Map.of(
                        "bytes", imageBase64
                    )
                )),
                Map.of("text", prompt)
            ));
        } else {
            // Text only request
            userMessage.put("role", "user");
            userMessage.put("content", List.of(
                Map.of("text", prompt)
            ));
        }

        requestBody.put("messages", List.of(userMessage));
        requestBody.put("inferenceConfig", Map.of("maxTokens", 200));

        return invokeModel(novaProModelId, requestBody);
    }

    /**
     * Invokes Mistral Voxtral for audio/voice analysis.
     * Voxtral supports audio input natively.
     */
    private String invokeVoxtralModel(String prompt, String audioBase64) throws Exception {
        Map<String, Object> requestBody = new HashMap<>();

        Map<String, Object> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", List.of(
            Map.of("type", "audio", "audio", Map.of(
                "data",   audioBase64,
                "format", "wav"
            )),
            Map.of("type", "text", "text", prompt)
        ));

        requestBody.put("messages", List.of(userMessage));
        requestBody.put("max_tokens", 200);

        return invokeModel(voxtralModelId, requestBody);
    }

    /**
     * Low-level Bedrock invocation — same pattern as the POC we validated.
     */
    private String invokeModel(String modelId, Map<String, Object> requestBody) throws Exception {
        String requestJson = objectMapper.writeValueAsString(requestBody);

        InvokeModelRequest request = InvokeModelRequest.builder()
                .modelId(modelId)
                .contentType("application/json")
                .accept("application/json")
                .body(SdkBytes.fromUtf8String(requestJson))
                .build();

        InvokeModelResponse response = bedrockRuntimeClient.invokeModel(request);
        return response.body().asUtf8String();
    }

    // ================================================================
    // PRIVATE — RESPONSE PARSING
    // ================================================================

    /**
     * Parses the JSON response from Bedrock into a SentimentResult.
     * Handles both Nova Pro and Voxtral response formats.
     */
    private SentimentResult parseSentimentResponse(String responseBody, String channel, String callId) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);

            // Extract the text content from the model response
            // Nova Pro format: output.message.content[0].text
            String contentText = null;
            if (root.has("output")) {
                contentText = root.path("output")
                                  .path("message")
                                  .path("content")
                                  .get(0)
                                  .path("text")
                                  .asText();
            } else if (root.has("choices")) {
                // Voxtral format: choices[0].message.content
                contentText = root.path("choices")
                                  .get(0)
                                  .path("message")
                                  .path("content")
                                  .asText();
            }

            if (contentText == null || contentText.isBlank()) {
                log.warn("Empty content from Bedrock for channel: {}", channel);
                return SentimentResult.neutral(channel, callId, "Empty response");
            }

            // Clean up any markdown fences the model might add
            contentText = contentText.replaceAll("```json|```", "").trim();

            // Parse the inner JSON sentiment object
            JsonNode sentiment = objectMapper.readTree(contentText);
            double score = sentiment.path("score").asDouble(0.5);
            String label = sentiment.path("label").asText("NEUTRAL");
            String notes = sentiment.path("notes").asText("");

            // Clamp score to valid range
            score = Math.max(0.0, Math.min(1.0, score));

            return new SentimentResult(score, label, notes, channel, callId,
                    System.currentTimeMillis());

        } catch (Exception e) {
            log.error("Failed to parse Bedrock sentiment response for channel {}: {}",
                channel, e.getMessage());
            return SentimentResult.neutral(channel, callId, "Parse error");
        }
    }

    private String scoreToLabel(double score) {
        if (score >= 0.7) return "POSITIVE";
        if (score >= 0.55) return "CALM";
        if (score >= 0.4) return "NEUTRAL";
        if (score >= 0.25) return "ANXIOUS";
        return "DISTRESSED";
    }

    private boolean isBedrockAvailable() {
        return awsEnabled && bedrockRuntimeClient != null;
    }

    // ================================================================
    // RESULT RECORD
    // Immutable data object returned by all analysis methods
    // ================================================================

    public record SentimentResult(
            double score,       // 0.0 - 1.0
            String label,       // POSITIVE / NEUTRAL / NEGATIVE / DISTRESSED / ANXIOUS / CALM
            String notes,       // brief clinical observation
            String channel,     // TEXT / VOICE / VIDEO
            String callId,
            long timestamp
    ) {
        /** Factory method for when analysis is unavailable */
        public static SentimentResult neutral(String channel, String callId, String reason) {
            return new SentimentResult(0.5, "NEUTRAL", reason, channel, callId,
                    System.currentTimeMillis());
        }
    }
}
