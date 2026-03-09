package com.careconnect.service;

import com.careconnect.model.CallTelemetryEvent;
import com.careconnect.repository.CallTelemetryEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CallTelemetryService {

    private static final Set<String> LARGE_OR_SENSITIVE_KEYS = Set.of(
            "audioBase64",
            "imageBase64",
            "token",
        "joinToken",
        "text",
        "transcript",
        "notes",
        "message",
        "email",
        "phone",
        "address",
        "name"
    );

    private static final Set<String> ALLOWED_TELEMETRY_KEYS = Set.of(
        "callId",
        "captureMode",
        "audioFormat",
        "imageFormat",
        "meetingActive",
        "notifiedOtherParty",
        "status",
        "type",
        "textLength",
        "overallScore",
        "overallLabel",
        "timestamp",
        "dbgTs",
        "dbgVs",
        "dbgIs",
        "dbgTw",
        "dbgVw",
        "dbgIw",
        "dbgTc",
        "dbgVc",
        "dbgIc",
        "dbgCf"
    );

    private final CallTelemetryEventRepository callTelemetryEventRepository;
    private final ObjectMapper objectMapper;

    public void recordCallEvent(
            String callId,
            String eventType,
            Long actorUserId,
            Long targetUserId,
            String status,
            Map<String, Object> metadata,
            String errorMessage
    ) {
        CallTelemetryEvent event = new CallTelemetryEvent();
        event.setCallId(trim(callId));
        event.setEventType(eventType);
        event.setEventSource("REST");
        event.setActorUserId(actorUserId);
        event.setTargetUserId(targetUserId);
        event.setStatus(defaultStatus(status));
        event.setMetadataJson(toJsonSafe(metadata));
        event.setErrorMessage(trim(errorMessage));
        event.setOccurredAt(LocalDateTime.now());
        callTelemetryEventRepository.save(event);
    }

    public void recordSentimentEvent(
            String callId,
            String eventType,
            String channel,
            Long actorUserId,
            Long targetUserId,
            String captureMode,
            BedrockSentimentService.SentimentResult result,
            Map<String, Object> payload,
            String status,
            String errorMessage
    ) {
        CallTelemetryEvent event = new CallTelemetryEvent();
        event.setCallId(trim(callId));
        event.setEventType(eventType);
        event.setEventSource("REST");
        event.setChannel(trim(channel));
        event.setActorUserId(actorUserId);
        event.setTargetUserId(targetUserId);
        event.setCaptureMode(trim(captureMode));
        event.setStatus(defaultStatus(status));

        if (result != null) {
            event.setSentimentScore(result.score());
            event.setSentimentLabel(trim(result.label()));
            event.setSentimentNotes(trim(result.notes()));
            event.setAnalysisTimestamp(result.timestamp());
        }

        event.setPayloadJson(toJsonSafe(sanitizePayload(payload)));
        event.setErrorMessage(trim(errorMessage));
        event.setOccurredAt(LocalDateTime.now());
        callTelemetryEventRepository.save(event);
    }

    public void recordWebSocketEvent(
            String callId,
            String eventType,
            Long actorUserId,
            Long targetUserId,
            Map<String, Object> payload,
            String status,
            String errorMessage
    ) {
        CallTelemetryEvent event = new CallTelemetryEvent();
        event.setCallId(trim(callId));
        event.setEventType(eventType);
        event.setEventSource("WEBSOCKET");
        event.setActorUserId(actorUserId);
        event.setTargetUserId(targetUserId);
        event.setStatus(defaultStatus(status));
        event.setPayloadJson(toJsonSafe(sanitizePayload(payload)));
        event.setErrorMessage(trim(errorMessage));
        event.setOccurredAt(LocalDateTime.now());
        callTelemetryEventRepository.save(event);
    }

    public List<CallTelemetryEvent> getTelemetryForCall(String callId) {
        return callTelemetryEventRepository.findByCallIdOrderByOccurredAtDesc(callId);
    }

    public Map<String, CallTelemetryEvent> getLatestSentimentByChannel(String callId) {
        List<CallTelemetryEvent> events = callTelemetryEventRepository.findByCallIdOrderByOccurredAtDesc(callId);
        Map<String, CallTelemetryEvent> latestByChannel = new LinkedHashMap<>();
        for (CallTelemetryEvent event : events) {
            if (event == null || event.getChannel() == null || event.getSentimentScore() == null) {
                continue;
            }
            String channel = event.getChannel().trim().toUpperCase(Locale.ROOT);
            if (channel.isEmpty()) {
                continue;
            }
            if (!latestByChannel.containsKey(channel)) {
                latestByChannel.put(channel, event);
            }
            if (latestByChannel.containsKey("TEXT")
                    && latestByChannel.containsKey("VOICE")
                    && latestByChannel.containsKey("VIDEO")) {
                break;
            }
        }
        return latestByChannel;
    }

    public List<CallTelemetryEvent> getTelemetryForUser(Long userId) {
        return callTelemetryEventRepository.findTop500ByActorUserIdOrTargetUserIdOrderByOccurredAtDesc(userId, userId);
    }

    public List<Map<String, Object>> getSentimentHistoryForUser(Long userId) {
        if (userId == null) {
            return List.of();
        }

        List<CallTelemetryEvent> events = callTelemetryEventRepository
                .findByActorUserIdOrTargetUserIdOrderByOccurredAtAsc(userId, userId);
        if (events.isEmpty()) {
            return List.of();
        }

        Map<String, List<CallTelemetryEvent>> byCall = events.stream()
                .filter(event -> event.getCallId() != null && !event.getCallId().isBlank())
                .collect(Collectors.groupingBy(
                        CallTelemetryEvent::getCallId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<Map<String, Object>> summaries = new ArrayList<>();
        for (Map.Entry<String, List<CallTelemetryEvent>> entry : byCall.entrySet()) {
            Map<String, Object> summary = summarizeCall(entry.getKey(), entry.getValue());
            if (!summary.isEmpty()) {
                summaries.add(summary);
            }
        }

        summaries.sort((a, b) -> {
            LocalDateTime left = (LocalDateTime) a.getOrDefault("_sortDate", LocalDateTime.MIN);
            LocalDateTime right = (LocalDateTime) b.getOrDefault("_sortDate", LocalDateTime.MIN);
            return right.compareTo(left);
        });
        summaries.forEach(item -> item.remove("_sortDate"));
        return summaries;
    }

    @Transactional
    public long deleteTelemetryForCall(String callId) {
        String normalizedCallId = trim(callId);
        if (normalizedCallId == null) {
            return 0;
        }
        return callTelemetryEventRepository.deleteByCallId(normalizedCallId);
    }

    private Map<String, Object> sanitizePayload(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return Map.of();
        }

        Map<String, Object> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            String key = entry.getKey();
            String normalizedKey = key == null ? "" : key.trim();
            String lowerKey = normalizedKey.toLowerCase();

            if (lowerKey.isEmpty()) {
                continue;
            }

            if (LARGE_OR_SENSITIVE_KEYS.contains(normalizedKey)
                    || lowerKey.contains("token")
                    || lowerKey.contains("secret")
                    || lowerKey.contains("password")
                    || lowerKey.contains("audio")
                    || lowerKey.contains("image")
                    || lowerKey.contains("transcript")
                    || lowerKey.contains("email")
                    || lowerKey.contains("phone")
                    || lowerKey.contains("address")
                    || lowerKey.contains("name")) {
                Object value = entry.getValue();
                if (value == null) {
                    sanitized.put(normalizedKey, null);
                } else {
                    int length = value.toString().length();
                    sanitized.put(normalizedKey, "[REDACTED:" + length + " chars]");
                }
                continue;
            }

            if (!ALLOWED_TELEMETRY_KEYS.contains(normalizedKey)) {
                sanitized.put(normalizedKey, "[OMITTED]");
                continue;
            }

            Object value = entry.getValue();
            if (value instanceof String textValue && textValue.length() > 180) {
                sanitized.put(normalizedKey, "[TRUNCATED:" + textValue.length() + " chars]");
                continue;
            }

            sanitized.put(normalizedKey, value);
        }
        return sanitized;
    }

    private String toJsonSafe(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to serialize telemetry payload", ex);
            return "{}";
        }
    }

    private String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String defaultStatus(String status) {
        String normalized = trim(status);
        return normalized == null ? "SUCCESS" : normalized;
    }

    private Map<String, Object> summarizeCall(String callId, List<CallTelemetryEvent> allEvents) {
        if (allEvents == null || allEvents.isEmpty()) {
            return Map.of();
        }

        List<CallTelemetryEvent> sorted = allEvents.stream()
                .filter(e -> e.getOccurredAt() != null)
                .sorted(Comparator.comparing(CallTelemetryEvent::getOccurredAt))
                .toList();
        if (sorted.isEmpty()) {
            return Map.of();
        }

        LocalDateTime callStart = sorted.stream()
                .filter(e -> "CALL_JOIN".equalsIgnoreCase(e.getEventType()))
                .map(CallTelemetryEvent::getOccurredAt)
                .findFirst()
                .orElse(sorted.get(0).getOccurredAt());

        LocalDateTime callEnd = sorted.stream()
                .filter(e -> "CALL_END".equalsIgnoreCase(e.getEventType()))
                .map(CallTelemetryEvent::getOccurredAt)
                .reduce((first, second) -> second)
                .orElse(sorted.get(sorted.size() - 1).getOccurredAt());

        long totalSeconds = Math.max(1L, Duration.between(callStart, callEnd).getSeconds());

        List<CallTelemetryEvent> timelineSamples = sorted.stream()
                .filter(e -> e.getSentimentScore() != null && e.getSentimentLabel() != null)
                .filter(e -> {
                    String channel = e.getChannel() == null ? "" : e.getChannel().trim().toUpperCase(Locale.ROOT);
                    if (channel.isEmpty() || "COMBINED".equals(channel)) {
                        return false;
                    }
                    String eventType = e.getEventType() == null ? "" : e.getEventType().trim().toUpperCase(Locale.ROOT);
                    return eventType.startsWith("SENTIMENT_") && !"SENTIMENT_FINAL".equals(eventType);
                })
                .toList();

        Map<String, Long> durationByBucket = new HashMap<>();
        durationByBucket.put("CALM", 0L);
        durationByBucket.put("ANXIOUS", 0L);
        durationByBucket.put("DISTRESSED", 0L);

        if (!timelineSamples.isEmpty()) {
            for (int i = 0; i < timelineSamples.size(); i++) {
                CallTelemetryEvent current = timelineSamples.get(i);
                LocalDateTime from = current.getOccurredAt();
                LocalDateTime to = (i < timelineSamples.size() - 1)
                        ? timelineSamples.get(i + 1).getOccurredAt()
                        : callEnd;
                long segmentSeconds = Math.max(1L, Duration.between(from, to).getSeconds());
                String bucket = normalizeLabel(current.getSentimentLabel());
                durationByBucket.put(bucket, durationByBucket.getOrDefault(bucket, 0L) + segmentSeconds);
            }
        }

        CallTelemetryEvent finalEvent = sorted.stream()
                .filter(e -> "SENTIMENT_FINAL".equalsIgnoreCase(e.getEventType()))
                .filter(e -> e.getSentimentScore() != null)
                .reduce((first, second) -> second)
                .orElse(null);

        double overallScore;
        String overallLabel;
        if (finalEvent != null) {
            overallScore = clamp(finalEvent.getSentimentScore());
            overallLabel = normalizeLabel(finalEvent.getSentimentLabel());
        } else if (!timelineSamples.isEmpty()) {
            overallScore = clamp(timelineSamples.stream()
                    .map(CallTelemetryEvent::getSentimentScore)
                    .filter(v -> v != null)
                    .mapToDouble(Double::doubleValue)
                    .average()
                    .orElse(0.5));
            overallLabel = labelFromScore(overallScore);
        } else {
            return Map.of();
        }

        List<Double> sampleScores = timelineSamples.stream()
                .map(CallTelemetryEvent::getSentimentScore)
                .filter(v -> v != null)
                .map(this::clamp)
                .toList();
        if (sampleScores.isEmpty()) {
            sampleScores = List.of(overallScore);
        }

        double stabilityScore = computeStability(sampleScores);
        double calmPct = percent(durationByBucket.get("CALM"), totalSeconds);
        double anxiousPct = percent(durationByBucket.get("ANXIOUS"), totalSeconds);
        double distressedPct = percent(durationByBucket.get("DISTRESSED"), totalSeconds);

        if (timelineSamples.isEmpty()) {
            if ("CALM".equals(overallLabel)) {
                calmPct = 1.0;
            } else if ("DISTRESSED".equals(overallLabel)) {
                distressedPct = 1.0;
            } else {
                anxiousPct = 1.0;
            }
        }

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("callId", callId);
        output.put("callDate", callStart);
        output.put("durationMinutes", round(totalSeconds / 60.0));
        output.put("overallScore", round(overallScore));
        output.put("overallLabel", overallLabel);
        output.put("positiveTimePct", round(calmPct));
        output.put("neutralTimePct", round(anxiousPct));
        output.put("negativeTimePct", round(distressedPct));
        output.put("stabilityScore", round(stabilityScore));
        output.put("_sortDate", callStart);
        return output;
    }

    private String normalizeLabel(String label) {
        String normalized = label == null ? "" : label.trim().toUpperCase(Locale.ROOT);
        if (normalized.contains("CALM") || normalized.contains("POSITIVE")) {
            return "CALM";
        }
        if (normalized.contains("DISTRESS") || normalized.contains("NEGATIVE")) {
            return "DISTRESSED";
        }
        return "ANXIOUS";
    }

    private String labelFromScore(double score) {
        if (score >= 0.67) {
            return "CALM";
        }
        if (score < 0.34) {
            return "DISTRESSED";
        }
        return "ANXIOUS";
    }

    private double computeStability(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return 0.0;
        }
        if (values.size() == 1) {
            return 1.0;
        }
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double variance = values.stream()
                .mapToDouble(v -> Math.pow(v - mean, 2))
                .average()
                .orElse(0.0);
        double stdDev = Math.sqrt(variance);
        return clamp(1.0 - stdDev);
    }

    private double percent(long part, long total) {
        if (total <= 0) {
            return 0.0;
        }
        return clamp((double) part / (double) total);
    }

    private double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private double clamp(double value) {
        if (value < 0.0) {
            return 0.0;
        }
        if (value > 1.0) {
            return 1.0;
        }
        return value;
    }
}
