package com.careconnect.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.chimesdkmeetings.ChimeSdkMeetingsClient;
import software.amazon.awssdk.services.chimesdkmeetings.model.*;

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

    @Autowired
    public ChimeService(
            @Autowired(required = false) ChimeSdkMeetingsClient chimeSdkMeetingsClient,
            @Value("${careconnect.aws.enabled:true}") boolean awsEnabled) {
        this.chimeSdkMeetingsClient = chimeSdkMeetingsClient;
        this.awsEnabled = awsEnabled;
    }

    // In-memory store of active meetings: callId -> MeetingResponse
    // On ECS single-instance this is sufficient — no distributed cache needed
    private final Map<String, Meeting> activeMeetings = new ConcurrentHashMap<>();

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
}
