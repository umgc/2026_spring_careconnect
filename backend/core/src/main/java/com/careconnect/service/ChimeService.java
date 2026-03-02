package com.careconnect.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.chimesdkmediapipelines.ChimeSdkMediaPipelinesClient;
import software.amazon.awssdk.services.chimesdkmediapipelines.model.CreateMediaCapturePipelineRequest;
import software.amazon.awssdk.services.chimesdkmediapipelines.model.CreateMediaCapturePipelineResponse;
import software.amazon.awssdk.services.chimesdkmediapipelines.model.DeleteMediaCapturePipelineRequest;
import software.amazon.awssdk.services.chimesdkmediapipelines.model.MediaPipelineSinkType;
import software.amazon.awssdk.services.chimesdkmediapipelines.model.MediaPipelineSourceType;
import software.amazon.awssdk.services.chimesdkmeetings.ChimeSdkMeetingsClient;
import software.amazon.awssdk.services.chimesdkmeetings.model.*;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.HashMap;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ChimeService — manages AWS Chime SDK video call meetings.
 *
 * Flow:
 *   1. Caller sends call invitation via WebSocket (CallNotificationHandler)
 *   2. Recipient accepts — frontend calls POST /api/v3/calls/{callId}/meeting
 *   3. This service creates a Chime meeting and adds both users as attendees
 *   4. Both users receive meeting credentials and join via Jitsi/Chime SDK in Flutter
 *   5. When call ends, DELETE /api/v3/calls/{callId}/meeting cleans up
 */
@Slf4j
@Service
public class ChimeService {

    private final ChimeSdkMeetingsClient chimeSdkMeetingsClient;
    private final ChimeSdkMediaPipelinesClient chimeSdkMediaPipelinesClient;
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final boolean awsEnabled;
    private final boolean recordingEnabled;
    private final String recordingBucket;
    private final String recordingPrefix;

    @Autowired
    public ChimeService(
            @Autowired(required = false) ChimeSdkMeetingsClient chimeSdkMeetingsClient,
            @Autowired(required = false) ChimeSdkMediaPipelinesClient chimeSdkMediaPipelinesClient,
            @Autowired(required = false) S3Client s3Client,
            @Autowired(required = false) S3Presigner s3Presigner,
            @Value("${careconnect.aws.enabled:true}") boolean awsEnabled,
            @Value("${careconnect.recording.enabled:false}") boolean recordingEnabled,
            @Value("${careconnect.recording.s3-bucket:}") String recordingBucket,
            @Value("${careconnect.recording.s3-prefix:call-recordings}") String recordingPrefix) {
        this.chimeSdkMeetingsClient = chimeSdkMeetingsClient;
        this.chimeSdkMediaPipelinesClient = chimeSdkMediaPipelinesClient;
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.awsEnabled = awsEnabled;
        this.recordingEnabled = recordingEnabled;
        this.recordingBucket = recordingBucket == null ? "" : recordingBucket.trim();
        this.recordingPrefix = recordingPrefix == null || recordingPrefix.isBlank()
                ? "call-recordings"
                : recordingPrefix.trim();
    }

    // In-memory store of active meetings: callId -> MeetingResponse
    // On ECS single-instance this is sufficient — no distributed cache needed
    private final Map<String, Meeting> activeMeetings = new ConcurrentHashMap<>();
    private final Map<String, RecordingSession> activeRecordings = new ConcurrentHashMap<>();

    // ================================================================
    // CREATE MEETING
    // Called when a call is accepted — creates the Chime meeting room
    // ================================================================

    /**
     * Creates a new Chime meeting for the given callId.
     * Returns the meeting details needed by both parties to join.
     */
    public Map<String, Object> createMeeting(String callId) {
        log.info("Creating Chime meeting for callId: {}", callId);

        // Check if meeting already exists (e.g. both parties called this simultaneously)
        if (activeMeetings.containsKey(callId)) {
            log.info("Meeting already exists for callId: {}", callId);
            return buildMeetingResponse(activeMeetings.get(callId));
        }

        if (!isAwsChimeAvailable()) {
            Meeting localMeeting = Meeting.builder()
                    .meetingId("local-" + UUID.randomUUID())
                    .externalMeetingId(callId)
                    .mediaRegion("us-east-1")
                    .build();
            activeMeetings.put(callId, localMeeting);
            log.warn("AWS Chime unavailable/disabled; created local mock meeting for callId: {}", callId);
            return buildMeetingResponse(localMeeting);
        }

        try {
            CreateMeetingRequest request = CreateMeetingRequest.builder()
                    .clientRequestToken(UUID.randomUUID().toString())
                    .mediaRegion("us-east-1")
                    .externalMeetingId(callId)
                    .build();

            CreateMeetingResponse response = chimeSdkMeetingsClient.createMeeting(request);
            Meeting meeting = response.meeting();

            // Store for later attendee creation and cleanup
            activeMeetings.put(callId, meeting);

            log.info("Chime meeting created: {} for callId: {}", meeting.meetingId(), callId);
            return buildMeetingResponse(meeting);

        } catch (Exception e) {
            log.error("Failed to create Chime meeting for callId: {}", callId, e);
            throw new RuntimeException("Failed to create video call meeting: " + e.getMessage());
        }
    }

    // ================================================================
    // CREATE ATTENDEE
    // Called for each user joining the meeting — returns join credentials
    // ================================================================

    /**
     * Adds a user to an existing Chime meeting.
     * Must be called for both the caller and the recipient.
     * Returns the attendee credentials the Flutter app needs to join.
     */
    public Map<String, Object> createAttendee(String callId, String userId) {
        log.info("Creating Chime attendee for userId: {} in callId: {}", userId, callId);

        Meeting meeting = activeMeetings.get(callId);
        if (meeting == null) {
            throw new RuntimeException("No active meeting found for callId: " + callId
                + ". Create the meeting first.");
        }

        if (!isAwsChimeAvailable()) {
            String externalUserId = toChimeExternalUserId(userId);
            return Map.of(
                "meetingId",        meeting.meetingId(),
                "externalMeetingId", meeting.externalMeetingId(),
                "mediaRegion",      meeting.mediaRegion() == null ? "us-east-1" : meeting.mediaRegion(),
                "mediaPlacement",   Map.of(
                    "audioHostUrl",      "",
                    "audioFallbackUrl",  "",
                    "screenDataUrl",     "",
                    "screenSharingUrl",  "",
                    "screenViewingUrl",  "",
                    "signalingUrl",      "",
                    "turnControlUrl",    "",
                    "eventIngestionUrl", ""
                ),
                "attendeeId",       "local-attendee-" + UUID.randomUUID(),
                "externalUserId",   externalUserId,
                "joinToken",        "local-join-token-" + UUID.randomUUID()
            );
        }

        try {
            String externalUserId = toChimeExternalUserId(userId);
            CreateAttendeeRequest request = CreateAttendeeRequest.builder()
                    .meetingId(meeting.meetingId())
                .externalUserId(externalUserId)
                    .build();

            CreateAttendeeResponse response = chimeSdkMeetingsClient.createAttendee(request);
            Attendee attendee = response.attendee();

            log.info("Chime attendee created: {} for userId: {}", attendee.attendeeId(), userId);

            // Return everything Flutter needs to join the meeting
            return Map.of(
                "meetingId",        meeting.meetingId(),
                "externalMeetingId", meeting.externalMeetingId(),
                "mediaRegion",      meeting.mediaRegion(),
                "mediaPlacement",   Map.of(
                    "audioHostUrl",             meeting.mediaPlacement().audioHostUrl(),
                    "audioFallbackUrl",         meeting.mediaPlacement().audioFallbackUrl(),
                    "screenDataUrl",            meeting.mediaPlacement().screenDataUrl(),
                    "screenSharingUrl",         meeting.mediaPlacement().screenSharingUrl(),
                    "screenViewingUrl",         meeting.mediaPlacement().screenViewingUrl(),
                    "signalingUrl",             meeting.mediaPlacement().signalingUrl(),
                    "turnControlUrl",           meeting.mediaPlacement().turnControlUrl(),
                    "eventIngestionUrl",        meeting.mediaPlacement().eventIngestionUrl() != null
                                                    ? meeting.mediaPlacement().eventIngestionUrl() : ""
                ),
                "attendeeId",       attendee.attendeeId(),
                "externalUserId",   attendee.externalUserId(),
                "joinToken",        attendee.joinToken()
            );

        } catch (Exception e) {
            log.error("Failed to create attendee for userId: {} in callId: {}", userId, callId, e);
            throw new RuntimeException("Failed to join video call: " + e.getMessage());
        }
    }

    // ================================================================
    // JOIN MEETING (convenience method)
    // Creates meeting if needed, then creates attendee — one call from Flutter
    // ================================================================

    /**
     * Convenience method — creates the meeting (if not already created)
     * and immediately adds the user as an attendee.
     *
     * Flutter calls this once per user when a call is accepted.
     */
    public Map<String, Object> joinMeeting(String callId, String userId) {
        // Ensure meeting exists
        if (!activeMeetings.containsKey(callId)) {
            createMeeting(callId);
        }
        // Add user as attendee and return join credentials
        return createAttendee(callId, userId);
    }

    // ================================================================
    // END MEETING
    // Called when either party hangs up
    // ================================================================

    /**
     * Deletes the Chime meeting and cleans up local state.
     * Called automatically when either party sends end-call via WebSocket.
     */
    public void endMeeting(String callId) {
        log.info("Ending Chime meeting for callId: {}", callId);

        Meeting meeting = activeMeetings.remove(callId);
        if (meeting == null) {
            log.warn("No active meeting found for callId: {} — may have already ended", callId);
            return;
        }

        if (!isAwsChimeAvailable()) {
            log.info("Ended local mock meeting for callId: {}", callId);
            return;
        }

        try {
            DeleteMeetingRequest request = DeleteMeetingRequest.builder()
                    .meetingId(meeting.meetingId())
                    .build();

            chimeSdkMeetingsClient.deleteMeeting(request);
            log.info("Chime meeting deleted: {} for callId: {}", meeting.meetingId(), callId);

        } catch (Exception e) {
            // Log but don't throw — if Chime already cleaned it up, that's fine
            log.warn("Could not delete Chime meeting {} — may have already expired: {}",
                meeting.meetingId(), e.getMessage());
        }

        try {
            stopRecording(callId, null, null, "Meeting ended");
        } catch (Exception ex) {
            log.warn("Could not stop recording for call {} during meeting end: {}", callId, ex.getMessage());
        }
    }

    public Map<String, Object> startRecording(
            String callId,
            Long caregiverUserId,
            Long patientUserId,
            boolean consentConfirmed,
            String consentNote
    ) {
        if (!recordingEnabled) {
            throw new RuntimeException("Call recording is disabled");
        }
        if (!consentConfirmed) {
            throw new RuntimeException("Recording requires explicit participant consent");
        }

        RecordingSession existing = activeRecordings.get(callId);
        if (existing != null && existing.active) {
            return existing.toResponse();
        }

        Meeting meeting = activeMeetings.get(callId);
        if (meeting == null) {
            throw new RuntimeException("Cannot start recording: meeting is not active");
        }

        String normalizedConsentNote = sanitizeConsentNote(consentNote);
        LocalDateTime startedAt = LocalDateTime.now();

        if (!isAwsRecordingAvailable()) {
            RecordingSession local = new RecordingSession(
                    callId,
                    "local-recording-" + UUID.randomUUID(),
                    buildRecordingPrefix(callId),
                    caregiverUserId,
                    patientUserId,
                    true,
                    normalizedConsentNote,
                    true,
                    startedAt,
                    null,
                    "MOCK_ACTIVE"
            );
            activeRecordings.put(callId, local);
            return local.toResponse();
        }

        if (recordingBucket.isEmpty()) {
            throw new RuntimeException("Recording bucket is not configured");
        }

        try {
            String sourceArn = meeting.meetingArn();
            if (sourceArn == null || sourceArn.isBlank()) {
                throw new RuntimeException("Meeting ARN is unavailable for recording");
            }

            CreateMediaCapturePipelineRequest request = CreateMediaCapturePipelineRequest.builder()
                    .clientRequestToken(UUID.randomUUID().toString())
                    .sourceType(MediaPipelineSourceType.CHIME_SDK_MEETING)
                    .sourceArn(sourceArn)
                    .sinkType(MediaPipelineSinkType.S3_BUCKET)
                    .sinkArn("arn:aws:s3:::" + recordingBucket + "/" + buildRecordingPrefix(callId))
                    .build();

            CreateMediaCapturePipelineResponse response =
                    chimeSdkMediaPipelinesClient.createMediaCapturePipeline(request);

            String mediaPipelineId = response.mediaCapturePipeline().mediaPipelineId();
            RecordingSession session = new RecordingSession(
                    callId,
                    mediaPipelineId,
                    buildRecordingPrefix(callId),
                    caregiverUserId,
                    patientUserId,
                    true,
                    normalizedConsentNote,
                    false,
                    startedAt,
                    null,
                    "ACTIVE"
            );

            activeRecordings.put(callId, session);
            return session.toResponse();
        } catch (Exception ex) {
            throw new RuntimeException("Failed to start recording: " + ex.getMessage());
        }
    }

    public Map<String, Object> stopRecording(
            String callId,
            Long caregiverUserId,
            Long patientUserId,
            String reason
    ) {
        RecordingSession session = activeRecordings.get(callId);
        if (session == null || !session.active) {
            return Map.of(
                    "callId", callId,
                    "recordingActive", false,
                    "status", "NOT_RECORDING"
            );
        }

        if (!session.mock && isAwsRecordingAvailable()) {
            try {
                chimeSdkMediaPipelinesClient.deleteMediaCapturePipeline(
                        DeleteMediaCapturePipelineRequest.builder()
                                .mediaPipelineId(session.pipelineId)
                                .build()
                );
            } catch (Exception ex) {
                throw new RuntimeException("Failed to stop recording: " + ex.getMessage());
            }
        }

        session.active = false;
        session.stoppedAt = LocalDateTime.now();
        session.status = "STOPPED";
        if (caregiverUserId != null) {
            session.stoppedByUserId = caregiverUserId;
        }
        if (patientUserId != null) {
            session.patientUserId = patientUserId;
        }
        if (reason != null && !reason.isBlank()) {
            session.stopReason = reason.trim();
        }

        return session.toResponse();
    }

    public Map<String, Object> getRecordingStatus(String callId) {
        if (!recordingEnabled) {
            return Map.of(
                    "callId", callId,
                    "recordingActive", false,
                    "recordingEnabled", false,
                    "status", "RECORDING_DISABLED",
                    "message", "Recording is disabled in this environment"
            );
        }

        RecordingSession session = activeRecordings.get(callId);
        if (session == null) {
            return Map.of(
                    "callId", callId,
                    "recordingActive", false,
                    "recordingEnabled", true,
                    "status", "NOT_RECORDING"
            );
        }
        Map<String, Object> response = new HashMap<>(session.toResponse());
        response.put("recordingEnabled", true);
        return response;
    }

        public Map<String, Object> getRecordingExtractInfo(String callId) {
        RecordingSession session = activeRecordings.get(callId);
        if (session == null) {
            return Map.of(
                "callId", callId,
                "recordingAvailable", false,
                "status", "NOT_RECORDING",
                "message", "No recording metadata found for this call"
            );
        }

        if (session.mock) {
            return Map.of(
                "callId", callId,
                "recordingAvailable", false,
                "status", session.status,
                "mock", true,
                "message", "Local mock recording has no media file to extract"
            );
        }

        if (recordingBucket.isEmpty() || s3Client == null || s3Presigner == null) {
            return Map.of(
                "callId", callId,
                "recordingAvailable", false,
                "status", session.status,
                "message", "Recording storage is not fully configured"
            );
        }

        String prefix = session.s3Prefix == null || session.s3Prefix.isBlank()
            ? buildRecordingPrefix(callId)
            : session.s3Prefix;

        S3Object latestObject = findLatestRecordingObject(prefix);
        if (latestObject == null) {
            return Map.of(
                "callId", callId,
                "recordingAvailable", false,
                "status", session.status,
                "s3Prefix", prefix,
                "message", "Recording file is not available yet"
            );
        }

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
            .bucket(recordingBucket)
            .key(latestObject.key())
            .build();

        PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(
            GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(20))
                .getObjectRequest(getObjectRequest)
                .build()
        );

        String fileName = latestObject.key();
        int slashIndex = fileName.lastIndexOf('/');
        if (slashIndex >= 0 && slashIndex < fileName.length() - 1) {
            fileName = fileName.substring(slashIndex + 1);
        }

        return Map.of(
            "callId", callId,
            "recordingAvailable", true,
            "status", session.status,
            "s3Prefix", prefix,
            "bucket", recordingBucket,
            "objectKey", latestObject.key(),
            "fileName", fileName,
            "downloadUrl", presigned.url().toString(),
            "expiresInSeconds", 1200
        );
        }

    // ================================================================
    // GET MEETING INFO
    // Used by sentiment service to confirm meeting is still active
    // ================================================================

    public boolean isMeetingActive(String callId) {
        return activeMeetings.containsKey(callId);
    }

    public String getMeetingId(String callId) {
        Meeting meeting = activeMeetings.get(callId);
        return meeting != null ? meeting.meetingId() : null;
    }

    // ================================================================
    // PRIVATE HELPERS
    // ================================================================

    private Map<String, Object> buildMeetingResponse(Meeting meeting) {
        return Map.of(
            "meetingId",         meeting.meetingId(),
            "externalMeetingId", meeting.externalMeetingId(),
            "mediaRegion",       meeting.mediaRegion()
        );
    }

    private String toChimeExternalUserId(String userId) {
        String normalized = userId == null ? "u0" : userId.trim();
        if (normalized.isEmpty()) {
            normalized = "u0";
        }
        normalized = normalized.replaceAll("[^A-Za-z0-9_-]", "_");
        if (normalized.length() < 2) {
            normalized = "u" + normalized;
        }
        if (normalized.length() > 64) {
            normalized = normalized.substring(0, 64);
        }
        return normalized;
    }

    private boolean isAwsChimeAvailable() {
        return awsEnabled && chimeSdkMeetingsClient != null;
    }

    private boolean isAwsRecordingAvailable() {
        return awsEnabled && chimeSdkMediaPipelinesClient != null;
    }

    private S3Object findLatestRecordingObject(String prefix) {
        ListObjectsV2Response response = s3Client.listObjectsV2(
                ListObjectsV2Request.builder()
                        .bucket(recordingBucket)
                        .prefix(prefix)
                        .build()
        );

        List<S3Object> objects = response.contents();
        if (objects == null || objects.isEmpty()) {
            return null;
        }

        return objects.stream()
                .filter(object -> object.key() != null && !object.key().endsWith("/"))
                .max(Comparator.comparing(S3Object::lastModified))
                .orElse(null);
    }

    private String buildRecordingPrefix(String callId) {
        return recordingPrefix + "/" + callId;
    }

    private String sanitizeConsentNote(String consentNote) {
        if (consentNote == null) {
            return null;
        }
        String trimmed = consentNote.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > 500) {
            return trimmed.substring(0, 500);
        }
        return trimmed;
    }

    private static final class RecordingSession {
        private final String callId;
        private final String pipelineId;
        private final String s3Prefix;
        private final Long startedByUserId;
        private Long stoppedByUserId;
        private Long patientUserId;
        private final boolean consentConfirmed;
        private final String consentNote;
        private final boolean mock;
        private final LocalDateTime startedAt;
        private LocalDateTime stoppedAt;
        private String stopReason;
        private boolean active;
        private String status;

        private RecordingSession(
                String callId,
                String pipelineId,
                String s3Prefix,
                Long startedByUserId,
                Long patientUserId,
                boolean consentConfirmed,
                String consentNote,
                boolean mock,
                LocalDateTime startedAt,
                LocalDateTime stoppedAt,
                String status
        ) {
            this.callId = callId;
            this.pipelineId = pipelineId;
            this.s3Prefix = s3Prefix;
            this.startedByUserId = startedByUserId;
            this.patientUserId = patientUserId;
            this.consentConfirmed = consentConfirmed;
            this.consentNote = consentNote;
            this.mock = mock;
            this.startedAt = startedAt;
            this.stoppedAt = stoppedAt;
            this.active = true;
            this.status = status;
        }

        private Map<String, Object> toResponse() {
            Map<String, Object> response = new HashMap<>();
            response.put("callId", callId);
            response.put("recordingActive", active);
            response.put("status", status);
            response.put("pipelineId", pipelineId);
            response.put("s3Prefix", s3Prefix);
            response.put("consentConfirmed", consentConfirmed);
            response.put("startedByUserId", startedByUserId);
            response.put("patientUserId", patientUserId);
            response.put("startedAt", startedAt);
            response.put("stoppedAt", stoppedAt);
            response.put("mock", mock);
            if (stopReason != null) {
                response.put("stopReason", stopReason);
            }
            if (consentNote != null) {
                response.put("consentNote", consentNote);
            }
            if (stoppedByUserId != null) {
                response.put("stoppedByUserId", stoppedByUserId);
            }
            return response;
        }
    }
}
