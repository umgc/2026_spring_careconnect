package com.careconnect.service;

import com.careconnect.model.CallTelemetryEvent;
import com.careconnect.repository.CallTelemetryEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class CallTelemetryService {

    private static final Set<String> LARGE_OR_SENSITIVE_KEYS = Set.of(
            "audioBase64",
            "imageBase64",
            "token",
            "joinToken"
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

    public List<CallTelemetryEvent> getTelemetryForUser(Long userId) {
        return callTelemetryEventRepository.findTop500ByActorUserIdOrTargetUserIdOrderByOccurredAtDesc(userId, userId);
    }

    private Map<String, Object> sanitizePayload(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return Map.of();
        }

        Map<String, Object> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            if (LARGE_OR_SENSITIVE_KEYS.contains(entry.getKey())) {
                Object value = entry.getValue();
                if (value == null) {
                    sanitized.put(entry.getKey(), null);
                } else {
                    int length = value.toString().length();
                    sanitized.put(entry.getKey(), "[REDACTED:" + length + " chars]");
                }
                continue;
            }
            sanitized.put(entry.getKey(), entry.getValue());
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
