package com.careconnect.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.chimesdkmeetings.ChimeSdkMeetingsClient;
import software.amazon.awssdk.services.chimesdkmeetings.model.*;

import java.util.HashMap;
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
    private final boolean awsEnabled;
    private final boolean transcriptionEnabled;
    private final String transcriptionLanguageCode;
    private final String transcriptionRegion;

    @Autowired
    public ChimeService(
            @Autowired(required = false) ChimeSdkMeetingsClient chimeSdkMeetingsClient,
            @Value("${careconnect.aws.enabled:true}") boolean awsEnabled,
            @Value("${careconnect.chime.transcription.enabled:true}") boolean transcriptionEnabled,
            @Value("${careconnect.chime.transcription.language-code:en-US}") String transcriptionLanguageCode,
            @Value("${careconnect.chime.transcription.region:us-east-1}") String transcriptionRegion) {
        this.chimeSdkMeetingsClient = chimeSdkMeetingsClient;
        this.awsEnabled = awsEnabled;
        this.transcriptionEnabled = transcriptionEnabled;
        this.transcriptionLanguageCode = transcriptionLanguageCode;
        this.transcriptionRegion = transcriptionRegion;
    }

    // In-memory store of active meetings: callId -> MeetingResponse
    // On ECS single-instance this is sufficient — no distributed cache needed
    private final Map<String, Meeting> activeMeetings = new ConcurrentHashMap<>();
    private final Map<String, Boolean> transcriptionStarted = new ConcurrentHashMap<>();
    private final Map<String, String> transcriptionLastSource = new ConcurrentHashMap<>();
    private final Map<String, Long> transcriptionLastAttemptAtMs = new ConcurrentHashMap<>();
    private final Map<String, String> transcriptionLastStatus = new ConcurrentHashMap<>();
    private final Map<String, String> transcriptionLastDetail = new ConcurrentHashMap<>();
    private final Map<String, String> transcriptionLastMeetingId = new ConcurrentHashMap<>();
    private final Map<String, String> transcriptionLastStartSource = new ConcurrentHashMap<>();
    private final Map<String, Long> transcriptionLastStartAtMs = new ConcurrentHashMap<>();
    private final Map<String, String> transcriptionLastStartStatus = new ConcurrentHashMap<>();
    private final Map<String, String> transcriptionLastStartDetail = new ConcurrentHashMap<>();

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
            transcriptionLastMeetingId.put(callId, meeting.meetingId());

            ensureMeetingTranscriptionStarted(callId, meeting, "createMeeting");

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

            // Retry transcription startup after attendee creation in case createMeeting
            // happened before media signaling was fully ready.
            ensureMeetingTranscriptionStarted(callId, meeting, "createAttendee");

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
            recordTranscriptionAttempt(callId, "endMeeting", "MEETING_ENDED", "no-active-meeting");
            log.warn("No active meeting found for callId: {} — may have already ended", callId);
            return;
        }

        transcriptionLastMeetingId.put(callId, meeting.meetingId());
        recordTranscriptionAttempt(callId, "endMeeting", "MEETING_ENDED", "meetingId=" + meeting.meetingId());

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

    public Map<String, Object> getTranscriptionDebugStatus(String callId) {
        Map<String, Object> out = new HashMap<>();
        Meeting meeting = activeMeetings.get(callId);
        String meetingId = meeting != null ? meeting.meetingId() : transcriptionLastMeetingId.get(callId);

        out.put("callId", callId);
        out.put("meetingActive", meeting != null);
        out.put("meetingId", meetingId);
        out.put("awsEnabled", awsEnabled);
        out.put("transcriptionEnabled", transcriptionEnabled);
        out.put("transcriptionStarted", Boolean.TRUE.equals(transcriptionStarted.get(callId)));
        out.put("transcriptionLanguageCode", transcriptionLanguageCode);
        out.put("transcriptionRegion", transcriptionRegion);
        out.put("lastAttemptSource", transcriptionLastSource.get(callId));
        out.put("lastAttemptAtMs", transcriptionLastAttemptAtMs.get(callId));
        out.put("lastStatus", transcriptionLastStatus.get(callId));
        out.put("lastDetail", transcriptionLastDetail.get(callId));
        out.put("lastStartSource", transcriptionLastStartSource.get(callId));
        out.put("lastStartAtMs", transcriptionLastStartAtMs.get(callId));
        out.put("lastStartStatus", transcriptionLastStartStatus.get(callId));
        out.put("lastStartDetail", transcriptionLastStartDetail.get(callId));

        if (meetingId != null && !meetingId.isBlank()) {
            String liveStatus = queryMeetingTranscriptionStatusSummary(meetingId);
            if (liveStatus != null && !liveStatus.isBlank()) {
                out.put("liveStatusProbe", liveStatus);
            }
        }

        return out;
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

    private void ensureMeetingTranscriptionStarted(String callId, Meeting meeting, String source) {
        if (!transcriptionEnabled || !isAwsChimeAvailable()) {
            recordTranscriptionAttempt(
                    callId,
                    source,
                    "SKIPPED",
                    !transcriptionEnabled ? "transcription.disabled" : "aws.chime.unavailable"
            );
            return;
        }

        if (Boolean.TRUE.equals(transcriptionStarted.get(callId))) {
            recordTranscriptionAttempt(callId, source, "ALREADY_STARTED", null);
            logMeetingTranscriptionStatus(callId, meeting.meetingId(), source + ":already-started");
            return;
        }

        try {
            StartMeetingTranscriptionRequest request = StartMeetingTranscriptionRequest.builder()
                    .meetingId(meeting.meetingId())
                    .transcriptionConfiguration(
                            TranscriptionConfiguration.builder()
                                    .engineTranscribeSettings(
                                            EngineTranscribeSettings.builder()
                                                    .languageCode(transcriptionLanguageCode)
                                                    .region(transcriptionRegion)
                                                    .build())
                                    .build())
                    .build();

                            StartMeetingTranscriptionResponse response =
                                chimeSdkMeetingsClient.startMeetingTranscription(request);
                            String responseSummary = response == null ? "null" : response.toString();
                    transcriptionStarted.put(callId, true);
                                recordTranscriptionAttempt(callId, source, "STARTED", responseSummary);
                                        recordTranscriptionStartAttempt(callId, source, "STARTED", responseSummary);
            log.info(
                                "Started Chime transcription for callId={} meetingId={} language={} region={} source={} response={}",
                    callId,
                    meeting.meetingId(),
                    transcriptionLanguageCode,
                    transcriptionRegion,
                                source,
                                responseSummary);
                    logMeetingTranscriptionStatus(callId, meeting.meetingId(), source + ":post-start");
        } catch (Exception e) {
                                String detail = e.getClass().getSimpleName() + ": " + e.getMessage();
                                recordTranscriptionAttempt(callId, source, "START_FAILED", detail);
                recordTranscriptionStartAttempt(callId, source, "START_FAILED", detail);
            log.warn(
                    "Could not start Chime transcription for callId={} meetingId={} source={}: {}. " +
                    "Verify Chime StartMeetingTranscription permission and Transcribe service-linked role.",
                    callId,
                    meeting.meetingId(),
                    source,
                                detail);
        }
    }

    private void recordTranscriptionAttempt(String callId, String source, String status, String detail) {
        transcriptionLastSource.put(callId, source);
        transcriptionLastAttemptAtMs.put(callId, System.currentTimeMillis());
        transcriptionLastStatus.put(callId, status);
        if (detail == null || detail.isBlank()) {
            transcriptionLastDetail.remove(callId);
        } else {
            transcriptionLastDetail.put(callId, detail);
        }
    }

    private void recordTranscriptionStartAttempt(String callId, String source, String status, String detail) {
        transcriptionLastStartSource.put(callId, source);
        transcriptionLastStartAtMs.put(callId, System.currentTimeMillis());
        transcriptionLastStartStatus.put(callId, status);
        if (detail == null || detail.isBlank()) {
            transcriptionLastStartDetail.remove(callId);
        } else {
            transcriptionLastStartDetail.put(callId, detail);
        }
    }

    private void logMeetingTranscriptionStatus(String callId, String meetingId, String source) {
        String summary = queryMeetingTranscriptionStatusSummary(meetingId);
        if (summary == null) {
            return;
        }

        recordTranscriptionAttempt(callId, source, "STATUS_PROBE", summary);
        log.info(
                "Chime transcription status callId={} meetingId={} source={} response={}",
                callId,
                meetingId,
                source,
                summary);
    }

    private String queryMeetingTranscriptionStatusSummary(String meetingId) {
        try {
            // Some AWS SDK versions do not expose getMeetingTranscription APIs.
            // Use reflection so this code remains compatible across versions.
            Class<?> requestClass = Class.forName(
                    "software.amazon.awssdk.services.chimesdkmeetings.model.GetMeetingTranscriptionRequest");
            Object requestBuilder = requestClass.getMethod("builder").invoke(null);
            requestBuilder.getClass().getMethod("meetingId", String.class).invoke(requestBuilder, meetingId);
            Object request = requestBuilder.getClass().getMethod("build").invoke(requestBuilder);

            Object statusResponse = chimeSdkMeetingsClient
                    .getClass()
                    .getMethod("getMeetingTranscription", requestClass)
                    .invoke(chimeSdkMeetingsClient, request);

            return String.valueOf(statusResponse);
        } catch (ClassNotFoundException notSupportedBySdk) {
            return "STATUS_API_UNAVAILABLE_IN_SDK";
        } catch (Exception statusErr) {
            return "STATUS_QUERY_FAILED: " + statusErr.getMessage();
        }
    }

}
