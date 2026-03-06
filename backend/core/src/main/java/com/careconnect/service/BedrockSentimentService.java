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

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * BedrockSentimentService — real-time sentiment analysis during video calls.
 *
 * Three analysis modes:
 *
 *   TEXT  — analyzes transcript text with local heuristics
 *            Input: plain text string
 *
 *   VOICE — analyzes voice activity metrics from Chime
 *            Input: average level, speech ratio, variability
 *
 *   VIDEO — analyzes facial expressions and visual emotional cues
 *            Input: base64-encoded image frame (JPEG/PNG)
 *            Uses: Nova Pro model (image-capable, already validated)
 *
 * All three return a SentimentResult with:
 *   - score:    0.0 (very negative) to 1.0 (very positive)
 *   - label:    POSITIVE / NEUTRAL / NEGATIVE / DISTRESSED / ANXIOUS / CALM
 *   - notes:    brief clinical observation from the model
 *   - channel:  TEXT / VOICE / VIDEO / COMBINED
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

    // ================================================================
    // TEXT SENTIMENT
    // Analyzes transcript text captured during the call
    // ================================================================

    /**
     * Analyzes the emotional tone of a text message.
     * Called whenever a chat message is sent during a call.
     */
    public SentimentResult analyzeText(String text, String callId) {
        log.debug("Analyzing text sentiment for callId: {}", callId);
        String input = text == null ? "" : text.trim();
        if (input.isBlank()) {
            return SentimentResult.neutral("TEXT", callId, "Empty transcript");
        }

        if (!isBedrockAvailable()) {
            return analyzeTranscriptHeuristic(input, callId);
        }

        String prompt = """
            You are a clinical transcript sentiment analyzer for a healthcare video call.
            Assess patient emotional state from this transcript text.

            Transcript: "%s"

            Scoring guidance:
            - 0.00-0.24: severe distress / crisis language
            - 0.25-0.39: anxious / worsening / strong negative symptoms
            - 0.40-0.52: neutral / mixed / unclear sentiment
            - 0.53-0.64: calm / stable / mild positive recovery language
            - 0.65-1.00: clearly positive / improving / reassured

            Return ONLY JSON:
            {
              "score": <0.0-1.0>,
              "label": "<POSITIVE|NEUTRAL|NEGATIVE|DISTRESSED|ANXIOUS|CALM>",
              "notes": "<max 10 words>"
            }
            """.formatted(input);

        try {
            String responseBody = invokeNovaModel(prompt, null, null);
            SentimentResult parsed = parseSentimentResponse(responseBody, "TEXT", callId);
            if (parsed != null && !parsed.fallback()) {
                return parsed;
            }
            return analyzeTranscriptHeuristic(input, callId);
        } catch (Exception e) {
            log.warn("Bedrock text sentiment failed, using heuristic fallback for callId {}: {}",
                    callId, e.getMessage());
            return analyzeTranscriptHeuristic(input, callId);
        }
    }

    public SentimentResult analyzeVoiceFromChimeMetrics(
            String callId,
            Double averageLevel,
            Double speechRatio,
            Double variability) {
        if (averageLevel == null || speechRatio == null || variability == null) {
            return SentimentResult.neutral("VOICE", callId, "Insufficient Chime voice metrics");
        }

        double level = clamp(averageLevel, 0.0, 1.0);
        double speaking = clamp(speechRatio, 0.0, 1.0);
        double jitter = clamp(variability, 0.0, 1.0);

        // Raw mode: plot direct Chime voice activity without heuristic filtering.
        // We use speechRatio as the voice score source-of-truth for trending.
        double score = speaking;

        score = clamp(score, 0.0, 1.0);
        String label = voiceActivityLabel(score);
        String notes = String.format(
                Locale.ROOT,
                "Raw Chime metrics level=%.2f speech=%.2f var=%.2f",
                level,
                speaking,
                jitter
        );

        return new SentimentResult(
                Math.round(score * 100.0) / 100.0,
                label,
                notes,
                "VOICE",
                callId,
                System.currentTimeMillis(),
                false
        );
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
    * Weights: text 20%, voice 40%, video 40%
     */
    public Map<String, Object> buildCombinedSentiment(
            SentimentResult textResult,
            SentimentResult voiceResult,
            SentimentResult videoResult,
            String callId) {

        final double textWeight = 0.20;
        final double voiceWeight = 0.40;
        final double videoWeight = 0.40;

        boolean hasTextSample = textResult != null && !textResult.fallback();
        boolean hasVoiceSample = voiceResult != null && !voiceResult.fallback();
        boolean hasVideoSample = videoResult != null && !videoResult.fallback();

        if (textResult == null) {
            textResult = SentimentResult.neutral("TEXT", callId, "No text sample");
        }
        if (voiceResult == null) {
            voiceResult = SentimentResult.neutral("VOICE", callId, "No voice sample");
        }
        if (videoResult == null) {
            videoResult = SentimentResult.neutral("VIDEO", callId, "No video sample");
        }

        double activeWeightSum =
                (hasTextSample ? textWeight : 0.0)
                + (hasVoiceSample ? voiceWeight : 0.0)
                + (hasVideoSample ? videoWeight : 0.0);

        double effectiveTextWeight = 0.0;
        double effectiveVoiceWeight = 0.0;
        double effectiveVideoWeight = 0.0;
        if (activeWeightSum > 0.0) {
            effectiveTextWeight = hasTextSample ? (textWeight / activeWeightSum) : 0.0;
            effectiveVoiceWeight = hasVoiceSample ? (voiceWeight / activeWeightSum) : 0.0;
            effectiveVideoWeight = hasVideoSample ? (videoWeight / activeWeightSum) : 0.0;
        }

        double textContribution = textResult.score() * effectiveTextWeight;
        double voiceContribution = voiceResult.score() * effectiveVoiceWeight;
        double videoContribution = videoResult.score() * effectiveVideoWeight;

        // Missing/fallback channels are excluded from combined math.
        double combined = activeWeightSum > 0.0
                ? (textContribution + voiceContribution + videoContribution)
                : 0.5;

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

        // Temporary debug fields to tune score calibration from real call data.
        result.put("dbgTs", round2(textResult.score()));
        result.put("dbgVs", round2(voiceResult.score()));
        result.put("dbgIs", round2(videoResult.score()));
        result.put("dbgTw", round3(effectiveTextWeight));
        result.put("dbgVw", round3(effectiveVoiceWeight));
        result.put("dbgIw", round3(effectiveVideoWeight));
        result.put("dbgTc", round3(textContribution));
        result.put("dbgVc", round3(voiceContribution));
        result.put("dbgIc", round3(videoContribution));
        result.put("dbgCf", round2(activeWeightSum));

        return result;
    }

    public SentimentResult analyzeFinalOverallSentiment(String callId, Map<String, SentimentResult> channelResults) {
        SentimentResult text = safeChannelResult(channelResults, "TEXT", callId);
        SentimentResult voice = safeChannelResult(channelResults, "VOICE", callId);
        SentimentResult video = safeChannelResult(channelResults, "VIDEO", callId);

        if (!isBedrockAvailable()) {
            return localFinalOverall(text, voice, video, callId);
        }

        String prompt = """
            You are a clinical sentiment aggregator for a healthcare call summary.
            Use the channel scores and notes below to compute one final overall sentiment.

            TEXT: score=%s, label=%s, notes=%s
            VOICE: score=%s, label=%s, notes=%s
            VIDEO: score=%s, label=%s, notes=%s

            Return ONLY JSON with keys score,label,notes.
            score must be 0.0 to 1.0 and represent the overall patient state.
            label must be one of POSITIVE, NEUTRAL, NEGATIVE, DISTRESSED, ANXIOUS, CALM.
            notes must be concise (max 12 words).
            """.formatted(
                text.score(), text.label(), safeNotes(text.notes()),
                voice.score(), voice.label(), safeNotes(voice.notes()),
                video.score(), video.label(), safeNotes(video.notes())
        );

        try {
            String responseBody = invokeNovaModel(prompt, null, null);
            SentimentResult parsed = parseSentimentResponse(responseBody, "COMBINED", callId);
            if (parsed == null || parsed.fallback()) {
                return localFinalOverall(text, voice, video, callId);
            }
            return new SentimentResult(
                    parsed.score(),
                    normalizeCombinedLabel(parsed.label()),
                    parsed.notes(),
                    "COMBINED",
                    callId,
                    System.currentTimeMillis(),
                    false
            );
        } catch (Exception ex) {
            log.warn("Final overall Bedrock analysis failed for callId {}: {}", callId, ex.getMessage());
            return localFinalOverall(text, voice, video, callId);
        }
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


    private SentimentResult safeChannelResult(Map<String, SentimentResult> channelResults, String channel, String callId) {
        if (channelResults == null) {
            return SentimentResult.neutral(channel, callId, "No channel sample");
        }
        SentimentResult result = channelResults.get(channel);
        if (result == null) {
            return SentimentResult.neutral(channel, callId, "No channel sample");
        }
        return result;
    }

    private SentimentResult localFinalOverall(
            SentimentResult text,
            SentimentResult voice,
            SentimentResult video,
            String callId) {
        double textWeight = text.fallback() ? 0.0 : 0.20;
        double voiceWeight = voice.fallback() ? 0.0 : 0.40;
        double videoWeight = video.fallback() ? 0.0 : 0.40;
        double weightSum = textWeight + voiceWeight + videoWeight;

        double score = weightSum > 0.0
            ? ((text.score() * textWeight) + (voice.score() * voiceWeight) + (video.score() * videoWeight)) / weightSum
            : 0.5;
        score = clamp(score, 0.0, 1.0);
        return new SentimentResult(
                Math.round(score * 100.0) / 100.0,
                normalizeCombinedLabel(scoreToLabel(score)),
                "Final overall sentiment from end-of-call channels",
                "COMBINED",
                callId,
                System.currentTimeMillis(),
                false
        );
    }

    private String safeNotes(String notes) {
        if (notes == null || notes.isBlank()) {
            return "none";
        }
        String cleaned = notes.replaceAll("\\s+", " ").trim();
        return cleaned.length() > 120 ? cleaned.substring(0, 120) : cleaned;
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private double round3(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private String normalizeCombinedLabel(String label) {
        String normalized = label == null ? "" : label.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "POSITIVE", "NEUTRAL", "NEGATIVE", "DISTRESSED", "ANXIOUS", "CALM" -> normalized;
            default -> "NEUTRAL";
        };
    }

    private SentimentResult analyzeTranscriptHeuristic(String text, String callId) {
        String input = text == null ? "" : text.trim();
        if (input.isBlank()) {
            return SentimentResult.neutral("TEXT", callId, "Empty transcript");
        }

        String normalized = input.toLowerCase();
        List<String> positive = List.of(
            "better", "okay", "good", "calm", "fine", "improving", "stable", "relieved",
            "comfortable", "rested", "sleeping better", "manageable", "recovering",
            "great", "happy", "thankful", "grateful", "much better", "doing well", "feeling well"
        );
        List<String> negative = List.of(
            "pain", "hurt", "anxious", "worried", "panic", "dizzy", "nausea", "depressed", "tired",
            "can't", "cannot", "worse", "bad", "awful", "terrible", "shortness of breath",
            "breathless", "struggling", "crying", "afraid", "scared", "not sleeping", "exhausted"
        );
        List<String> severe = List.of(
            "severe pain", "chest pain", "can't breathe", "cannot breathe", "panic attack",
            "very dizzy", "vomiting", "faint", "hopeless", "suicidal"
        );

        int pos = 0;
        int neg = 0;
        for (String token : positive) {
            if (normalized.contains(token)) {
                pos += 1;
            }
        }
        for (String token : negative) {
            if (normalized.contains(token)) {
                neg += 1;
            }
        }

        int severeHits = 0;
        for (String token : severe) {
            if (normalized.contains(token)) {
                severeHits += 1;
            }
        }

        double score = 0.50 + (pos * 0.09) - (neg * 0.08) - (severeHits * 0.12);

        // Keep strong directional intent visible in the final score.
        if (severeHits == 0 && pos >= neg + 2) {
            score = Math.max(score, 0.58);
        }
        if (neg >= pos + 2) {
            score = Math.min(score, 0.45);
        }

        // Increase contrast so clearly positive/negative language moves off center.
        score = amplifyAwayFromNeutral(score, 1.45);

        score = clamp(score, 0.0, 1.0);

        String label = scoreToLabel(score);
        String notes = neg > pos
                ? "Distress-oriented terms detected"
                : (pos > neg ? "Positive recovery terms detected" : "Neutral transcript tone");

        return new SentimentResult(
                Math.round(score * 100.0) / 100.0,
                label,
                notes,
                "TEXT",
                callId,
                System.currentTimeMillis(),
                false
        );
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private double amplifyAwayFromNeutral(double score, double factor) {
        double centered = score - 0.5;
        return 0.5 + (centered * factor);
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
     */
    private SentimentResult parseSentimentResponse(String responseBody, String channel, String callId) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);

            // Some models may return the sentiment object directly.
            if (root.has("score") && root.has("label")) {
                return sentimentNodeToResult(root, channel, callId);
            }

            String contentText = extractModelContentText(root);

            if (contentText == null || contentText.isBlank()) {
                log.warn("Empty content from Bedrock for channel: {}", channel);
                return SentimentResult.neutral(channel, callId, "Empty response");
            }

            String cleaned = stripCodeFences(contentText);
            JsonNode sentiment;
            try {
                sentiment = objectMapper.readTree(cleaned);
            } catch (Exception firstParseEx) {
                String embeddedJson = extractSentimentJsonObject(cleaned);
                if (embeddedJson == null || embeddedJson.isBlank()) {
                    throw firstParseEx;
                }
                sentiment = objectMapper.readTree(embeddedJson);
            }

            return sentimentNodeToResult(sentiment, channel, callId);

        } catch (Exception e) {
            log.error("Failed to parse Bedrock sentiment response for channel {}: {}",
                channel, e.getMessage());
            return SentimentResult.neutral(channel, callId, "Parse error");
        }
    }

    private SentimentResult sentimentNodeToResult(JsonNode sentiment, String channel, String callId) {
        double score = sentiment.path("score").asDouble(0.5);
        String label = sentiment.path("label").asText("NEUTRAL");
        String notes = sentiment.path("notes").asText("");

        score = Math.max(0.0, Math.min(1.0, score));

        return new SentimentResult(
                score,
                label,
                notes,
                channel,
                callId,
                System.currentTimeMillis(),
                false
        );
    }

    private String extractModelContentText(JsonNode root) {
        // Nova-style: output.message.content[].text
        JsonNode novaContent = root.path("output").path("message").path("content");
        String text = extractTextFromContentNode(novaContent);
        if (!text.isBlank()) {
            return text;
        }

        // OpenAI/Mistral-style: choices[0].message.content or choices[0].text
        JsonNode firstChoice = root.path("choices").isArray() && root.path("choices").size() > 0
                ? root.path("choices").get(0)
                : null;
        if (firstChoice != null && !firstChoice.isMissingNode()) {
            text = extractTextFromContentNode(firstChoice.path("message").path("content"));
            if (!text.isBlank()) {
                return text;
            }
            text = firstChoice.path("text").asText("");
            if (!text.isBlank()) {
                return text;
            }
        }

        // Common fallback fields
        text = root.path("output_text").asText("");
        if (!text.isBlank()) {
            return text;
        }
        text = root.path("completion").asText("");
        if (!text.isBlank()) {
            return text;
        }

        return "";
    }

    private String extractTextFromContentNode(JsonNode contentNode) {
        if (contentNode == null || contentNode.isMissingNode() || contentNode.isNull()) {
            return "";
        }

        if (contentNode.isTextual()) {
            return contentNode.asText("");
        }

        if (contentNode.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode item : contentNode) {
                if (item == null || item.isNull() || item.isMissingNode()) {
                    continue;
                }
                if (item.isTextual()) {
                    String value = item.asText("");
                    if (!value.isBlank()) {
                        if (sb.length() > 0) {
                            sb.append('\n');
                        }
                        sb.append(value);
                    }
                    continue;
                }

                String nestedText = item.path("text").asText("");
                if (nestedText.isBlank()) {
                    nestedText = item.path("output_text").asText("");
                }
                if (!nestedText.isBlank()) {
                    if (sb.length() > 0) {
                        sb.append('\n');
                    }
                    sb.append(nestedText);
                }
            }
            return sb.toString().trim();
        }

        if (contentNode.isObject()) {
            String text = contentNode.path("text").asText("");
            if (!text.isBlank()) {
                return text;
            }
            text = contentNode.path("output_text").asText("");
            if (!text.isBlank()) {
                return text;
            }
        }

        return "";
    }

    private String stripCodeFences(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("```(?:json)?", "").replace("```", "").trim();
    }

    private String extractFirstJsonObject(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end < 0 || end <= start) {
            return "";
        }
        return text.substring(start, end + 1);
    }

    private String extractSentimentJsonObject(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String scoreToken = "\"score\"";
        int scoreIndex = text.indexOf(scoreToken);
        if (scoreIndex < 0) {
            return extractFirstJsonObject(text);
        }

        int start = text.lastIndexOf('{', scoreIndex);
        if (start < 0) {
            return extractFirstJsonObject(text);
        }

        boolean inString = false;
        boolean escaping = false;
        int depth = 0;

        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);

            if (escaping) {
                escaping = false;
                continue;
            }

            if (c == '\\') {
                escaping = true;
                continue;
            }

            if (c == '"') {
                inString = !inString;
                continue;
            }

            if (inString) {
                continue;
            }

            if (c == '{') {
                depth += 1;
            } else if (c == '}') {
                depth -= 1;
                if (depth == 0) {
                    return text.substring(start, i + 1);
                }
            }
        }

        return extractFirstJsonObject(text);
    }

    private boolean containsParseableSentimentJson(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            if (root.has("score") && root.has("label")) {
                return true;
            }

            String contentText = extractModelContentText(root);
            if (contentText == null || contentText.isBlank()) {
                return false;
            }

            String cleaned = stripCodeFences(contentText);
            try {
                JsonNode parsed = objectMapper.readTree(cleaned);
                return parsed.has("score") && parsed.has("label");
            } catch (Exception firstEx) {
                String embeddedJson = extractSentimentJsonObject(cleaned);
                if (embeddedJson == null || embeddedJson.isBlank()) {
                    return false;
                }
                JsonNode parsed = objectMapper.readTree(embeddedJson);
                return parsed.has("score") && parsed.has("label");
            }
        } catch (Exception ignored) {
            return false;
        }
    }

    private String scoreToLabel(double score) {
        if (score >= 0.65) return "POSITIVE";
        if (score >= 0.53) return "CALM";
        if (score >= 0.40) return "NEUTRAL";
        if (score >= 0.25) return "ANXIOUS";
        return "DISTRESSED";
    }

    private String voiceActivityLabel(double score) {
        if (score >= 0.75) return "VERY_HIGH_ACTIVITY";
        if (score >= 0.55) return "HIGH_ACTIVITY";
        if (score >= 0.30) return "MODERATE_ACTIVITY";
        return "LOW_ACTIVITY";
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
            long timestamp,
            boolean fallback
    ) {
        /** Factory method for when analysis is unavailable */
        public static SentimentResult neutral(String channel, String callId, String reason) {
            return new SentimentResult(
                0.5,
                "NEUTRAL",
                reason,
                channel,
                callId,
                System.currentTimeMillis(),
                true
            );
        }
    }
}
