package com.careconnect.service;

import com.careconnect.model.CallTelemetryEvent;
import com.careconnect.repository.CallTelemetryEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;

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
}
