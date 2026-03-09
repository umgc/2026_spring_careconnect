package com.careconnect.service;

import com.careconnect.model.CallTranscriptSegment;
import com.careconnect.repository.CallTranscriptSegmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CallTranscriptService {

    private static final int MAX_SEGMENTS_PER_REQUEST = 200;
    private static final int MAX_SEGMENT_TEXT_CHARS = 1200;
    private static final int MAX_SPEAKER_LABEL_CHARS = 60;
    private static final int MAX_SOURCE_CHARS = 80;
    private static final int MAX_TRANSCRIPT_CHARS = 16000;
    private static final String DEFAULT_SOURCE = "CLIENT_TRANSCRIPT";

    private final CallTranscriptSegmentRepository callTranscriptSegmentRepository;
    private final CallTranscriptArchiveService callTranscriptArchiveService;

    public int recordSegments(String callId, Long actorUserId, List<TranscriptSegmentInput> segments) {
        String normalizedCallId = trim(callId);
        if (normalizedCallId == null || segments == null || segments.isEmpty()) {
            return 0;
        }
        if (segments.size() > MAX_SEGMENTS_PER_REQUEST) {
            throw new IllegalArgumentException("Too many transcript segments in one request");
        }

        int saved = 0;
        for (TranscriptSegmentInput input : segments) {
            if (input == null) {
                continue;
            }
            String text = trim(input.text());
            if (text == null) {
                continue;
            }
            if (text.length() > MAX_SEGMENT_TEXT_CHARS) {
                text = text.substring(0, MAX_SEGMENT_TEXT_CHARS);
            }

            CallTranscriptSegment segment = new CallTranscriptSegment();
            segment.setCallId(normalizedCallId);
            segment.setActorUserId(actorUserId);
            segment.setSpeakerLabel(normalizeSpeaker(input.speakerLabel()));
            segment.setText(text);
            segment.setStartMs(input.startMs());
            segment.setEndMs(input.endMs());
            segment.setSource(normalizeSource(input.source()));
            segment.setOccurredAt(LocalDateTime.now());
            callTranscriptSegmentRepository.save(segment);
            saved += 1;
        }

        return saved;
    }

    public List<CallTranscriptSegment> getSegmentsForCall(String callId) {
        String normalizedCallId = trim(callId);
        if (normalizedCallId == null) {
            return List.of();
        }
        List<CallTranscriptSegment> dbSegments =
                callTranscriptSegmentRepository.findByCallIdOrderByStartMsAscOccurredAtAsc(normalizedCallId);
        List<CallTranscriptSegment> archivedSegments = callTranscriptArchiveService.getArchivedSegments(normalizedCallId);
        if (archivedSegments.isEmpty()) {
            return dbSegments;
        }
        if (dbSegments.isEmpty()) {
            return archivedSegments;
        }
        return mergeSegments(archivedSegments, dbSegments);
    }

    public long countSegments(String callId) {
        String normalizedCallId = trim(callId);
        if (normalizedCallId == null) {
            return 0;
        }
        long dbCount = callTranscriptSegmentRepository.countByCallId(normalizedCallId);
        if (dbCount <= 0) {
            return callTranscriptArchiveService.getArchivedSegmentCount(normalizedCallId);
        }
        if (!callTranscriptArchiveService.isArchived(normalizedCallId)) {
            return dbCount;
        }
        return getSegmentsForCall(normalizedCallId).size();
    }

    public boolean hasTranscriptAccess(String callId, Long userId) {
        String normalizedCallId = trim(callId);
        if (normalizedCallId == null || userId == null) {
            return false;
        }
        if (callTranscriptSegmentRepository.existsByCallIdAndActorUserId(normalizedCallId, userId)) {
            return true;
        }
        return callTranscriptArchiveService.hasArchivedTranscriptAccess(normalizedCallId, userId);
    }

    public String buildTranscriptTextForSummary(String callId) {
        List<CallTranscriptSegment> segments = getSegmentsForCall(callId);
        if (segments.isEmpty()) {
            return "";
        }

        StringBuilder out = new StringBuilder();
        for (CallTranscriptSegment segment : segments) {
            String text = trim(segment.getText());
            if (text == null) {
                continue;
            }

            String speaker = trim(segment.getSpeakerLabel());
            if (speaker == null) {
                speaker = "UNKNOWN";
            }

            String line = "[" + speaker + "] " + text + "\n";
            if (out.length() + line.length() > MAX_TRANSCRIPT_CHARS) {
                break;
            }
            out.append(line);
        }
        return out.toString().trim();
    }

    public boolean archiveIfEligible(String callId) {
        String normalizedCallId = trim(callId);
        if (normalizedCallId == null) {
            return false;
        }
        List<CallTranscriptSegment> dbSegments =
                callTranscriptSegmentRepository.findByCallIdOrderByStartMsAscOccurredAtAsc(normalizedCallId);
        return callTranscriptArchiveService.archiveIfEligible(normalizedCallId, dbSegments);
    }

    public boolean isArchived(String callId) {
        return callTranscriptArchiveService.isArchived(callId);
    }

    private List<CallTranscriptSegment> mergeSegments(
            List<CallTranscriptSegment> archivedSegments,
            List<CallTranscriptSegment> dbSegments
    ) {
        List<CallTranscriptSegment> merged = new ArrayList<>(archivedSegments.size() + dbSegments.size());
        Set<String> seen = new HashSet<>();

        for (CallTranscriptSegment segment : archivedSegments) {
            if (segment == null) {
                continue;
            }
            merged.add(segment);
            seen.add(segmentKey(segment));
        }

        for (CallTranscriptSegment segment : dbSegments) {
            if (segment == null) {
                continue;
            }
            if (seen.add(segmentKey(segment))) {
                merged.add(segment);
            }
        }

        merged.sort(Comparator
                .comparing(CallTranscriptSegment::getStartMs, Comparator.nullsLast(Long::compareTo))
                .thenComparing(CallTranscriptSegment::getOccurredAt, Comparator.nullsLast(LocalDateTime::compareTo)));
        return merged;
    }

    private String segmentKey(CallTranscriptSegment segment) {
        return (segment.getSpeakerLabel() == null ? "" : segment.getSpeakerLabel().trim()) + "|" +
                (segment.getText() == null ? "" : segment.getText().trim()) + "|" +
                (segment.getStartMs() == null ? "" : segment.getStartMs()) + "|" +
                (segment.getEndMs() == null ? "" : segment.getEndMs()) + "|" +
                (segment.getSource() == null ? "" : segment.getSource().trim());
    }

    private String normalizeSpeaker(String speakerLabel) {
        String normalized = trim(speakerLabel);
        if (normalized == null) {
            return "UNKNOWN";
        }
        normalized = normalized.toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9_\\- ]", "");
        if (normalized.isBlank()) {
            return "UNKNOWN";
        }
        if (normalized.length() > MAX_SPEAKER_LABEL_CHARS) {
            normalized = normalized.substring(0, MAX_SPEAKER_LABEL_CHARS);
        }
        return normalized;
    }

    private String normalizeSource(String source) {
        String normalized = defaultIfBlank(source, DEFAULT_SOURCE);
        normalized = normalized.replaceAll("[^A-Za-z0-9_\\-./ ]", "");
        if (normalized.length() > MAX_SOURCE_CHARS) {
            normalized = normalized.substring(0, MAX_SOURCE_CHARS);
        }
        return normalized;
    }

    private String defaultIfBlank(String value, String fallback) {
        String trimmed = trim(value);
        return trimmed == null ? fallback : trimmed;
    }

    private String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record TranscriptSegmentInput(
            String speakerLabel,
            String text,
            Long startMs,
            Long endMs,
            String source
    ) {
    }
}
