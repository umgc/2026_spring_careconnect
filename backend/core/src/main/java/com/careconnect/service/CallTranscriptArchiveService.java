package com.careconnect.service;

import com.careconnect.model.CallTranscriptArchive;
import com.careconnect.model.CallTranscriptSegment;
import com.careconnect.repository.CallTranscriptArchiveRepository;
import com.careconnect.repository.CallTranscriptSegmentRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CallTranscriptArchiveService {

    private static final DateTimeFormatter KEY_DATE = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    private static final DateTimeFormatter KEY_TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final CallTranscriptArchiveRepository archiveRepository;
    private final CallTranscriptSegmentRepository segmentRepository;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private S3StorageService s3StorageService;

    @Value("${app.call.transcript.archive.enabled:true}")
    private boolean archiveEnabled;

    @Value("${app.call.transcript.archive.min-segments:600}")
    private int minSegmentsForArchive;

    @Value("${app.call.transcript.archive.min-chars:120000}")
    private int minCharsForArchive;

    @Value("${app.call.transcript.archive.delete-db-after-archive:true}")
    private boolean deleteDbAfterArchive;

    public boolean isArchived(String callId) {
        String normalizedCallId = normalize(callId);
        return normalizedCallId != null && archiveRepository.existsByCallId(normalizedCallId);
    }

    public boolean hasArchivedTranscriptAccess(String callId, Long userId) {
        String normalizedCallId = normalize(callId);
        if (normalizedCallId == null || userId == null) {
            return false;
        }
        return archiveRepository.findTopByCallIdOrderByArchivedAtDesc(normalizedCallId)
                .map(archive -> containsParticipant(archive.getParticipantUserIds(), userId))
                .orElse(false);
    }

    public long getArchivedSegmentCount(String callId) {
        String normalizedCallId = normalize(callId);
        if (normalizedCallId == null) {
            return 0;
        }
        return archiveRepository.findTopByCallIdOrderByArchivedAtDesc(normalizedCallId)
                .map(CallTranscriptArchive::getSegmentCount)
                .orElse(0)
                .longValue();
    }

    public List<CallTranscriptSegment> getArchivedSegments(String callId) {
        String normalizedCallId = normalize(callId);
        if (normalizedCallId == null || s3StorageService == null) {
            return List.of();
        }

        Optional<CallTranscriptArchive> latest = archiveRepository.findTopByCallIdOrderByArchivedAtDesc(normalizedCallId);
        if (latest.isEmpty()) {
            return List.of();
        }

        try {
            byte[] bytes = s3StorageService.download(latest.get().getStorageKey());
            List<ArchivedTranscriptSegment> payload = objectMapper.readValue(
                    bytes,
                    new TypeReference<List<ArchivedTranscriptSegment>>() {}
            );
            List<CallTranscriptSegment> segments = new ArrayList<>(payload.size());
            for (ArchivedTranscriptSegment item : payload) {
                if (item == null || item.text() == null || item.text().isBlank()) {
                    continue;
                }
                CallTranscriptSegment segment = new CallTranscriptSegment();
                segment.setCallId(normalizedCallId);
                segment.setSpeakerLabel(item.speakerLabel());
                segment.setText(item.text());
                segment.setStartMs(item.startMs());
                segment.setEndMs(item.endMs());
                segment.setSource(item.source());
                segment.setActorUserId(item.actorUserId());
                segment.setOccurredAt(parseDate(item.occurredAt()));
                segments.add(segment);
            }
            return segments;
        } catch (Exception ex) {
            log.warn("Failed to load archived transcript for callId {}: {}", normalizedCallId, ex.getMessage());
            return List.of();
        }
    }

    public boolean archiveIfEligible(String callId, List<CallTranscriptSegment> currentSegments) {
        String normalizedCallId = normalize(callId);
        if (normalizedCallId == null || !archiveEnabled || s3StorageService == null) {
            return false;
        }
        if (archiveRepository.existsByCallId(normalizedCallId)) {
            return false;
        }
        if (currentSegments == null || currentSegments.isEmpty()) {
            return false;
        }

        int transcriptChars = currentSegments.stream()
                .map(CallTranscriptSegment::getText)
                .filter(text -> text != null && !text.isBlank())
                .mapToInt(String::length)
                .sum();

        if (currentSegments.size() < minSegmentsForArchive && transcriptChars < minCharsForArchive) {
            return false;
        }

        List<ArchivedTranscriptSegment> payload = currentSegments.stream()
                .map(segment -> new ArchivedTranscriptSegment(
                        segment.getSpeakerLabel(),
                        segment.getText(),
                        segment.getStartMs(),
                        segment.getEndMs(),
                        segment.getSource(),
                        segment.getActorUserId(),
                        segment.getOccurredAt() == null ? null : segment.getOccurredAt().toString()
                ))
                .toList();

        try {
            byte[] bytes = objectMapper.writeValueAsBytes(payload);
            String storageKey = buildStorageKey(normalizedCallId);
            s3StorageService.upload(storageKey, bytes, "application/json");

            CallTranscriptArchive archive = new CallTranscriptArchive();
            archive.setCallId(normalizedCallId);
            archive.setStorageProvider("S3");
            archive.setStorageKey(storageKey);
            archive.setSegmentCount(currentSegments.size());
            archive.setTranscriptChars(transcriptChars);
            archive.setParticipantUserIds(buildParticipantUserIds(currentSegments));
            archive.setSha256Checksum(sha256(bytes));
            archive.setArchivedAt(LocalDateTime.now());
            archiveRepository.save(archive);

            if (deleteDbAfterArchive) {
                segmentRepository.deleteByCallId(normalizedCallId);
            }

            log.info(
                    "Archived transcript for callId={} segments={} chars={} dbDeleted={}",
                    normalizedCallId,
                    currentSegments.size(),
                    transcriptChars,
                    deleteDbAfterArchive
            );
            return true;
        } catch (Exception ex) {
            log.warn("Transcript archival failed for callId {}: {}", normalizedCallId, ex.getMessage());
            return false;
        }
    }

    private String buildStorageKey(String callId) {
        LocalDateTime now = LocalDateTime.now();
        String safeCallId = callId.replaceAll("[^A-Za-z0-9_\\-]", "_");
        return "calls/transcripts/" +
                KEY_DATE.format(now) + "/" +
                safeCallId + "/transcript_" +
                KEY_TS.format(now) + "_" +
                UUID.randomUUID().toString().substring(0, 8) + ".json";
    }

    private String buildParticipantUserIds(List<CallTranscriptSegment> segments) {
        return segments.stream()
                .map(CallTranscriptSegment::getActorUserId)
                .filter(id -> id != null && id > 0)
                .map(String::valueOf)
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .collect(Collectors.joining(","));
    }

    private boolean containsParticipant(String participantUserIds, Long userId) {
        if (participantUserIds == null || participantUserIds.isBlank()) {
            return false;
        }
        String token = String.valueOf(userId);
        for (String id : participantUserIds.split(",")) {
            if (token.equals(id.trim())) {
                return true;
            }
        }
        return false;
    }

    private String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder out = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                out.append(String.format("%02x", b));
            }
            return out.toString();
        } catch (Exception ex) {
            return "";
        }
    }

    private LocalDateTime parseDate(String value) {
        try {
            if (value == null || value.isBlank()) {
                return LocalDateTime.now();
            }
            return LocalDateTime.parse(value);
        } catch (Exception ignored) {
            return LocalDateTime.now();
        }
    }

    private String normalize(String callId) {
        if (callId == null) {
            return null;
        }
        String trimmed = callId.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record ArchivedTranscriptSegment(
            String speakerLabel,
            String text,
            Long startMs,
            Long endMs,
            String source,
            Long actorUserId,
            String occurredAt
    ) {}
}
