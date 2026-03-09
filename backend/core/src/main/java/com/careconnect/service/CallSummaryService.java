package com.careconnect.service;

import com.careconnect.model.CallSummary;
import com.careconnect.model.CallTelemetryEvent;
import com.careconnect.repository.CallSummaryRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CallSummaryService {

    private final CallSummaryRepository callSummaryRepository;
    private final CallTranscriptService callTranscriptService;
    private final BedrockSentimentService bedrockSentimentService;
    private final ObjectMapper objectMapper;

    public Optional<CallSummary> getLatestSummaryEntity(String callId) {
        if (callId == null || callId.isBlank()) {
            return Optional.empty();
        }
        return callSummaryRepository.findTopByCallIdOrderByGeneratedAtDesc(callId.trim());
    }

    public Optional<Map<String, Object>> getLatestSummary(String callId) {
        return getLatestSummaryEntity(callId).map(this::toResponse);
    }

    public Map<String, Object> generateAndStoreSummary(
            String callId,
            Long generatedByUserId,
            Map<String, CallTelemetryEvent> latestByChannel
    ) {
        String normalizedCallId = normalize(callId);
        if (normalizedCallId == null) {
            throw new IllegalArgumentException("callId is required");
        }

        String transcript = callTranscriptService.buildTranscriptTextForSummary(normalizedCallId);
        long segmentCount = callTranscriptService.countSegments(normalizedCallId);

        if (transcript.isBlank()) {
            CallSummary emptySummary = new CallSummary();
            emptySummary.setCallId(normalizedCallId);
            emptySummary.setStatus("NO_TRANSCRIPT");
            emptySummary.setTranscriptSegmentCount((int) segmentCount);
            emptySummary.setGeneratedByUserId(generatedByUserId);
            emptySummary.setErrorMessage("No transcript segments were available.");
            emptySummary.setGeneratedAt(LocalDateTime.now());
            emptySummary.setSummaryJson(toJsonSafe(Map.of(
                    "headline", "No transcript captured",
                    "overallAssessment", "Call transcript was not available for summarization.",
                    "keyConcerns", List.of(),
                    "recommendedActions", List.of(),
                    "followUpQuestions", List.of()
            )));
            Map<String, Object> response = toResponse(callSummaryRepository.save(emptySummary));
            response.put("transcriptArchived", callTranscriptService.isArchived(normalizedCallId));
            return response;
        }

        Map<String, BedrockSentimentService.SentimentResult> channelScores = toChannelScores(normalizedCallId, latestByChannel);

        try {
            Map<String, Object> summaryPayload = bedrockSentimentService.summarizeTranscript(
                    normalizedCallId,
                    transcript,
                    channelScores
            );
            CallSummary stored = new CallSummary();
            stored.setCallId(normalizedCallId);
            stored.setStatus("SUCCESS");
            stored.setTranscriptSegmentCount((int) segmentCount);
            stored.setGeneratedByUserId(generatedByUserId);
            stored.setGeneratedAt(LocalDateTime.now());
            stored.setSummaryJson(toJsonSafe(summaryPayload));
            callTranscriptService.archiveIfEligible(normalizedCallId);
            Map<String, Object> response = toResponse(callSummaryRepository.save(stored));
            response.put("transcriptArchived", callTranscriptService.isArchived(normalizedCallId));
            return response;
        } catch (Exception ex) {
            log.warn("Call summary generation failed for callId {}: {}", normalizedCallId, ex.getMessage());
            CallSummary failed = new CallSummary();
            failed.setCallId(normalizedCallId);
            failed.setStatus("ERROR");
            failed.setTranscriptSegmentCount((int) segmentCount);
            failed.setGeneratedByUserId(generatedByUserId);
            failed.setGeneratedAt(LocalDateTime.now());
            failed.setErrorMessage(ex.getMessage());
            failed.setSummaryJson(toJsonSafe(Map.of(
                    "headline", "Summary unavailable",
                    "overallAssessment", "Automated summary could not be generated.",
                    "keyConcerns", List.of(),
                    "recommendedActions", List.of(),
                    "followUpQuestions", List.of()
            )));
            callTranscriptService.archiveIfEligible(normalizedCallId);
            Map<String, Object> response = toResponse(callSummaryRepository.save(failed));
            response.put("transcriptArchived", callTranscriptService.isArchived(normalizedCallId));
            return response;
        }
    }

    private Map<String, BedrockSentimentService.SentimentResult> toChannelScores(
            String callId,
            Map<String, CallTelemetryEvent> latestByChannel
    ) {
        if (latestByChannel == null || latestByChannel.isEmpty()) {
            return Map.of();
        }
        return latestByChannel.entrySet().stream()
                .filter(e -> e.getValue() != null && e.getValue().getSentimentScore() != null)
                .collect(Collectors.toMap(
                        e -> e.getKey().trim().toUpperCase(),
                        e -> new BedrockSentimentService.SentimentResult(
                                e.getValue().getSentimentScore(),
                                e.getValue().getSentimentLabel() == null ? "ANXIOUS" : e.getValue().getSentimentLabel(),
                                e.getValue().getSentimentNotes() == null ? "" : e.getValue().getSentimentNotes(),
                                e.getKey().trim().toUpperCase(),
                                callId,
                                e.getValue().getAnalysisTimestamp() == null ? System.currentTimeMillis() : e.getValue().getAnalysisTimestamp(),
                                false
                        ),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private Map<String, Object> toResponse(CallSummary summary) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (summary.getSummaryJson() != null && !summary.getSummaryJson().isBlank()) {
            try {
                payload = objectMapper.readValue(
                        summary.getSummaryJson(),
                        new TypeReference<Map<String, Object>>() {}
                );
            } catch (Exception ignored) {
                payload = new LinkedHashMap<>();
            }
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("callId", summary.getCallId());
        response.put("status", summary.getStatus());
        response.put("generatedAt", summary.getGeneratedAt());
        response.put("transcriptSegmentCount", summary.getTranscriptSegmentCount());
        response.put("generatedByUserId", summary.getGeneratedByUserId());
        if (summary.getErrorMessage() != null && !summary.getErrorMessage().isBlank()) {
            response.put("errorMessage", summary.getErrorMessage());
        }
        response.put("transcriptArchived", callTranscriptService.isArchived(summary.getCallId()));
        response.put("summary", payload);
        return response;
    }

    private String toJsonSafe(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private String normalize(String callId) {
        if (callId == null) {
            return null;
        }
        String trimmed = callId.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
