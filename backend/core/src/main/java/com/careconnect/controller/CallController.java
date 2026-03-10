package com.careconnect.controller;

import com.careconnect.exception.AppException;
import com.careconnect.model.User;
import com.careconnect.repository.UserRepository;
import com.careconnect.service.BedrockSentimentService;
import com.careconnect.service.BedrockSentimentService.SentimentResult;
import com.careconnect.service.ChimeService;
import com.careconnect.service.CallTelemetryService;
import com.careconnect.service.CallTranscriptService;
import com.careconnect.service.CallSummaryService;
import com.careconnect.service.CallRecordingService;
import com.careconnect.service.CaregiverPatientLinkService;
import com.careconnect.model.CallTelemetryEvent;

import com.careconnect.websocket.CallNotificationHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;

@RestController
@RequestMapping("/api/v3/calls")
@Tag(name = "Calls", description = "Video call and sentiment analysis endpoints")
@SecurityRequirement(name = "Bearer Authentication")
public class CallController {

    private static final Logger log = LoggerFactory.getLogger(CallController.class);
    private static final double SILENCE_SPEECH_RATIO_THRESHOLD = 0.04;
    private static final double SILENCE_MIC_LEVEL_THRESHOLD = 0.02;
    private static final double SILENCE_VARIABILITY_THRESHOLD = 0.12;

    @Autowired private ChimeService chimeService;
    @Autowired private BedrockSentimentService sentimentService;
    @Autowired private CallNotificationHandler callNotificationHandler;
    @Autowired private CallTelemetryService callTelemetryService;
    @Autowired private CallTranscriptService callTranscriptService;
    @Autowired private CallSummaryService callSummaryService;
    @Autowired private CallRecordingService callRecordingService;
    @Autowired private CaregiverPatientLinkService caregiverPatientLinkService;
    @Autowired private UserRepository userRepository;
    @Autowired private Environment environment;

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String userEmail = authentication.getName();
        return userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "User not authenticated"));
    }

    private void ensurePatientSource(User currentUser) {
        if (currentUser.getRole() != com.careconnect.security.Role.PATIENT) {
            throw new AppException(HttpStatus.FORBIDDEN,
                    "Only patient-origin audio/video/text can be analyzed for sentiment");
        }
    }

    private void ensureDevOrLocalMode() {
        String[] activeProfiles = environment.getActiveProfiles();
        if (activeProfiles == null || activeProfiles.length == 0) {
            activeProfiles = environment.getDefaultProfiles();
        }

        for (String profile : activeProfiles) {
            String normalized = profile == null ? "" : profile.trim().toLowerCase();
            if (normalized.equals("dev") ||
                    normalized.equals("local") ||
                    normalized.equals("default") ||
                    normalized.equals("test")) {
                return;
            }
        }

        throw new AppException(HttpStatus.FORBIDDEN,
                "Call telemetry deletion is only available in local/dev mode");
    }

    @PostMapping("/{callId}/join")
    @Operation(summary = "Join or create a Chime meeting for a call")
    public ResponseEntity<java.util.Map<String, Object>> joinCall(
            @PathVariable String callId,
            @RequestBody(required = false) Map<String, Object> body) {
        try {
            User currentUser = getCurrentUser();
            Map<String, Object> response = chimeService.joinMeeting(callId, currentUser.getId().toString());
            Map<String, Object> contextMetadata = extractCallContextMetadata(body);
            callTelemetryService.recordCallEvent(
                    callId,
                    "CALL_JOIN",
                    currentUser.getId(),
                    null,
                    "SUCCESS",
                    mergeMetadata(Map.of("meetingActive", chimeService.isMeetingActive(callId)), contextMetadata),
                    null
            );
            log.info("User {} joined call {}", currentUser.getId(), callId);
            return ResponseEntity.ok(response);
        } catch (AppException e) {
            Long actorId = null;
            try {
                actorId = getCurrentUser().getId();
            } catch (Exception ignored) {
            }
            callTelemetryService.recordCallEvent(
                    callId,
                    "CALL_JOIN",
                    actorId,
                    null,
                    "ERROR",
                    Map.of(),
                    e.getMessage()
            );
            throw e;
        } catch (Exception e) {
            Long actorId = null;
            try {
                actorId = getCurrentUser().getId();
            } catch (Exception ignored) {
            }
            callTelemetryService.recordCallEvent(
                    callId,
                    "CALL_JOIN",
                    actorId,
                    null,
                    "ERROR",
                    Map.of(),
                    e.getMessage()
            );
            log.error("Failed to join call {}: {}", callId, e.getMessage(), e);
            throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to join call: " + e.getMessage());
        }
    }

    @PostMapping("/{callId}/end")
    @Operation(summary = "End a Chime meeting and notify all participants")
    public ResponseEntity<Map<String, String>> endCall(
            @PathVariable String callId,
            @RequestParam(required = false) String otherPartyId,
            @RequestBody(required = false) Map<String, Object> body) {
        try {
            User currentUser = getCurrentUser();
            if ((otherPartyId == null || otherPartyId.isBlank()) && body != null) {
                Object otherPartyRaw = body.get("otherPartyId");
                otherPartyId = otherPartyRaw == null ? null : otherPartyRaw.toString();
            }
            Map<String, Object> contextMetadata = extractCallContextMetadata(body);
            maybeRecordFinalOverallSentiment(callId, currentUser.getId(), parseUserId(otherPartyId));
            maybeGenerateAndStoreCallSummary(callId, currentUser.getId());
            callRecordingService.stopRecording(callId);
            chimeService.endMeeting(callId);
            if (otherPartyId != null && !otherPartyId.isBlank()) {
                callNotificationHandler.sendNotificationToUser(otherPartyId, Map.of(
                        "type", "call-ended",
                        "callId", callId,
                        "endedBy", currentUser.getId().toString()
                ));
            }
            callTelemetryService.recordCallEvent(
                    callId,
                    "CALL_END",
                    currentUser.getId(),
                    parseUserId(otherPartyId),
                    "SUCCESS",
                    mergeMetadata(
                            Map.of("notifiedOtherParty", otherPartyId != null && !otherPartyId.isBlank()),
                            contextMetadata
                    ),
                    null
            );
            log.info("User {} ended call {}", currentUser.getId(), callId);
            return ResponseEntity.ok(Map.of("status", "ended", "callId", callId));
        } catch (AppException e) {
            Long actorId = null;
            try {
                actorId = getCurrentUser().getId();
            } catch (Exception ignored) {
            }
            callTelemetryService.recordCallEvent(
                    callId,
                    "CALL_END",
                    actorId,
                    parseUserId(otherPartyId),
                    "ERROR",
                    Map.of(),
                    e.getMessage()
            );
            throw e;
        } catch (Exception e) {
            Long actorId = null;
            try {
                actorId = getCurrentUser().getId();
            } catch (Exception ignored) {
            }
            callTelemetryService.recordCallEvent(
                    callId,
                    "CALL_END",
                    actorId,
                    parseUserId(otherPartyId),
                    "ERROR",
                    Map.of(),
                    e.getMessage()
            );
            log.error("Failed to end call {}: {}", callId, e.getMessage(), e);
            throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to end call: " + e.getMessage());
        }
    }

    @PostMapping("/{callId}/sentiment/text")
    @Operation(summary = "Analyze sentiment from a chat message")
    public ResponseEntity<SentimentResult> analyzeTextSentiment(
            @PathVariable String callId,
            @RequestBody Map<String, String> body) {
        final Map<String, Object> telemetryPayload = sanitizeTelemetryPayload(body);
        try {
            User currentUser = getCurrentUser();
            ensurePatientSource(currentUser);
            ensureSentimentEnabledForCall(callId);
            String text = body.get("text");
            if (text == null || text.isBlank())
                throw new AppException(HttpStatus.BAD_REQUEST, "text field is required");
            SentimentResult result = sentimentService.analyzeText(text, callId);
                callTelemetryService.recordSentimentEvent(
                    callId,
                    "SENTIMENT_TEXT",
                    "TEXT",
                    currentUser.getId(),
                    parseUserId(body.get("otherPartyId")),
                    body.get("captureMode"),
                    result,
                        Map.of(
                            "textLength", text.length(),
                            "captureMode", body.get("captureMode")
                        ),
                    "SUCCESS",
                    null
                );
            broadcastSentimentToCaregivers(
                    callId,
                    currentUser.getId().toString(),
                    body.get("otherPartyId"),
                    result,
                    body.get("captureMode")
            );
            return ResponseEntity.ok(result);
        } catch (AppException e) {
            Long actorId = null;
            try {
                actorId = getCurrentUser().getId();
            } catch (Exception ignored) {
            }
            callTelemetryService.recordSentimentEvent(
                    callId,
                    "SENTIMENT_TEXT",
                    "TEXT",
                    actorId,
                    parseUserId(body.get("otherPartyId")),
                    body.get("captureMode"),
                    null,
                        telemetryPayload,
                    "ERROR",
                    e.getMessage()
            );
            throw e;
        } catch (Exception e) {
            Long actorId = null;
            try {
                actorId = getCurrentUser().getId();
            } catch (Exception ignored) {
            }
            callTelemetryService.recordSentimentEvent(
                    callId,
                    "SENTIMENT_TEXT",
                    "TEXT",
                    actorId,
                    parseUserId(body.get("otherPartyId")),
                    body.get("captureMode"),
                    null,
                        telemetryPayload,
                    "ERROR",
                    e.getMessage()
            );
            log.error("Text sentiment failed for call {}: {}", callId, e.getMessage(), e);
            throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "Sentiment analysis failed: " + e.getMessage());
        }
    }

    @PostMapping("/{callId}/sentiment/voice")
    @Operation(summary = "Analyze sentiment from an audio clip (base64 encoded)")
    public ResponseEntity<SentimentResult> analyzeVoiceSentiment(
            @PathVariable String callId,
            @RequestBody Map<String, String> body) {
        final Map<String, Object> telemetryPayload = sanitizeTelemetryPayload(body);
        try {
            User currentUser = getCurrentUser();
            ensurePatientSource(currentUser);
            ensureSentimentEnabledForCall(callId);
            Long otherPartyUserId = parseUserId(body.get("otherPartyId"));
            Double averageLevel = parseDouble(body.get("averageLevel"));
            Double speechRatio = parseDouble(body.get("speechRatio"));
            Double variability = parseDouble(body.get("variability"));

            if (averageLevel == null || speechRatio == null || variability == null) {
                throw new AppException(
                        HttpStatus.BAD_REQUEST,
                        "Provide Chime metrics fields: averageLevel, speechRatio, variability"
                );
            }

                if (isSilenceWindow(averageLevel, speechRatio, variability)) {
                SentimentResult ignored = SentimentResult.neutral(
                    "VOICE",
                    callId,
                    "Silence window ignored"
                );
                log.debug(
                    "Ignoring silence voice metrics callId={} actorUserId={} avgLevel={} speechRatio={}",
                    callId,
                    currentUser.getId(),
                    averageLevel,
                    speechRatio
                );
                broadcastQuietVoiceStateToCaregivers(
                    callId,
                    currentUser.getId().toString(),
                    body.get("otherPartyId"),
                    body.get("captureMode")
                );
                // Do not record/broadcast scored sentiment for ignored silence windows.
                return ResponseEntity.status(HttpStatus.ACCEPTED).body(ignored);
                }

            SentimentResult result = sentimentService.analyzeVoiceFromChimeMetrics(
                    callId,
                    averageLevel,
                    speechRatio,
                    variability
            );
            log.info(
                    "Voice sentiment result callId={} actorUserId={} actorRole={} fallback={} label={} score={}",
                    callId,
                    currentUser.getId(),
                    currentUser.getRole(),
                    result.fallback(),
                    result.label(),
                    result.score()
            );
                callTelemetryService.recordSentimentEvent(
                    callId,
                    "SENTIMENT_VOICE",
                    "VOICE",
                    currentUser.getId(),
                    otherPartyUserId,
                    body.get("captureMode"),
                    result,
                    telemetryPayload,
                    "SUCCESS",
                    null
                );
            broadcastSentimentToCaregivers(
                    callId,
                    currentUser.getId().toString(),
                    body.get("otherPartyId"),
                    result,
                    body.get("captureMode")
            );

            return ResponseEntity.ok(result);
        } catch (AppException e) {
            Long actorId = null;
            try {
                actorId = getCurrentUser().getId();
            } catch (Exception ignored) {
            }
            callTelemetryService.recordSentimentEvent(
                    callId,
                    "SENTIMENT_VOICE",
                    "VOICE",
                    actorId,
                    parseUserId(body.get("otherPartyId")),
                    body.get("captureMode"),
                    null,
                        telemetryPayload,
                    "ERROR",
                    e.getMessage()
            );
            throw e;
        } catch (Exception e) {
            Long actorId = null;
            try {
                actorId = getCurrentUser().getId();
            } catch (Exception ignored) {
            }
            callTelemetryService.recordSentimentEvent(
                    callId,
                    "SENTIMENT_VOICE",
                    "VOICE",
                    actorId,
                    parseUserId(body.get("otherPartyId")),
                    body.get("captureMode"),
                    null,
                        telemetryPayload,
                    "ERROR",
                    e.getMessage()
            );
            log.error("Voice sentiment failed for call {}: {}", callId, e.getMessage(), e);
            throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "Voice sentiment analysis failed: " + e.getMessage());
        }
    }

    private boolean isSilenceWindow(Double averageLevel, Double speechRatio, Double variability) {
        if (averageLevel == null || speechRatio == null || variability == null) {
            return false;
        }
        return speechRatio <= SILENCE_SPEECH_RATIO_THRESHOLD
                && averageLevel <= SILENCE_MIC_LEVEL_THRESHOLD
                && variability <= SILENCE_VARIABILITY_THRESHOLD;
    }

    private void broadcastQuietVoiceStateToCaregivers(
            String callId,
            String userId,
            String otherPartyId,
            String captureMode) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "sentiment-channel-state");
        notification.put("callId", callId);
        notification.put("channel", "voice");
        notification.put("muted", false);
        notification.put("status", "QUIET");
        notification.put("notes", "No speech detected in this window.");
        notification.put("timestamp", System.currentTimeMillis());
        if (captureMode != null && !captureMode.isBlank()) {
            notification.put("captureMode", captureMode.trim().toUpperCase(Locale.ROOT));
        }

        sendSentimentToCaregiverIfEligible(userId, notification);
        sendSentimentToCaregiverIfEligible(otherPartyId, notification);
    }

    @PostMapping("/{callId}/sentiment/video")
    @Operation(summary = "Analyze sentiment from a video frame (base64 encoded image)")
    public ResponseEntity<SentimentResult> analyzeVideoSentiment(
            @PathVariable String callId,
            @RequestBody Map<String, String> body) {
        final Map<String, Object> telemetryPayload = sanitizeTelemetryPayload(body);
        try {
            User currentUser = getCurrentUser();
            ensurePatientSource(currentUser);
            ensureSentimentEnabledForCall(callId);
            String imageBase64 = body.get("imageBase64");
            if (imageBase64 == null || imageBase64.isBlank())
                throw new AppException(HttpStatus.BAD_REQUEST, "imageBase64 field is required");
            String imageFormat = body.getOrDefault("imageFormat", "jpeg");
            SentimentResult result = sentimentService.analyzeVideoFrame(imageBase64, imageFormat, callId);
            callTelemetryService.recordSentimentEvent(
                    callId,
                    "SENTIMENT_VIDEO",
                    "VIDEO",
                    currentUser.getId(),
                    parseUserId(body.get("otherPartyId")),
                    body.get("captureMode"),
                    result,
                        telemetryPayload,
                    "SUCCESS",
                    null
            );
            broadcastSentimentToCaregivers(
                    callId,
                    currentUser.getId().toString(),
                    body.get("otherPartyId"),
                    result,
                    body.get("captureMode")
            );
            return ResponseEntity.ok(result);
        } catch (AppException e) {
            Long actorId = null;
            try {
                actorId = getCurrentUser().getId();
            } catch (Exception ignored) {
            }
            callTelemetryService.recordSentimentEvent(
                    callId,
                    "SENTIMENT_VIDEO",
                    "VIDEO",
                    actorId,
                    parseUserId(body.get("otherPartyId")),
                    body.get("captureMode"),
                    null,
                        telemetryPayload,
                    "ERROR",
                    e.getMessage()
            );
            throw e;
        } catch (Exception e) {
            Long actorId = null;
            try {
                actorId = getCurrentUser().getId();
            } catch (Exception ignored) {
            }
            callTelemetryService.recordSentimentEvent(
                    callId,
                    "SENTIMENT_VIDEO",
                    "VIDEO",
                    actorId,
                    parseUserId(body.get("otherPartyId")),
                    body.get("captureMode"),
                    null,
                        telemetryPayload,
                    "ERROR",
                    e.getMessage()
            );
            log.error("Video sentiment failed for call {}: {}", callId, e.getMessage(), e);
            throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "Video sentiment analysis failed: " + e.getMessage());
        }
    }

    @PostMapping("/{callId}/sentiment/combined")
    @Operation(summary = "Get combined sentiment score across all channels")
    public ResponseEntity<java.util.Map<String, Object>> getCombinedSentiment(
            @PathVariable String callId,
            @RequestBody Map<String, String> body) {
        final Map<String, Object> telemetryPayload = sanitizeTelemetryPayload(body);
        try {
            User currentUser = getCurrentUser();
            ensurePatientSource(currentUser);
            ensureSentimentEnabledForCall(callId);
            String text = body.getOrDefault("text", "");
            String imageBase64 = body.getOrDefault("imageBase64", "");
            String imageFormat = body.getOrDefault("imageFormat", "jpeg");
                Double averageLevel = parseDouble(body.get("averageLevel"));
                Double speechRatio = parseDouble(body.get("speechRatio"));
                Double variability = parseDouble(body.get("variability"));

            SentimentResult textResult = text.isBlank() ? null : sentimentService.analyzeText(text, callId);
                SentimentResult voiceResult = (averageLevel == null || speechRatio == null || variability == null)
                    ? null
                    : sentimentService.analyzeVoiceFromChimeMetrics(callId, averageLevel, speechRatio, variability);
            SentimentResult videoResult = imageBase64.isBlank() ? null : sentimentService.analyzeVideoFrame(imageBase64, imageFormat, callId);
            Map<String, Object> combined = sentimentService.buildCombinedSentiment(textResult, voiceResult, videoResult, callId);
            callTelemetryService.recordSentimentEvent(
                    callId,
                    "SENTIMENT_COMBINED",
                    "COMBINED",
                    currentUser.getId(),
                    parseUserId(body.get("otherPartyId")),
                    body.get("captureMode"),
                    null,
                        sanitizeCombinedTelemetry(combined),
                    "SUCCESS",
                    null
            );
            return ResponseEntity.ok(combined);
        } catch (AppException e) {
            Long actorId = null;
            try {
                actorId = getCurrentUser().getId();
            } catch (Exception ignored) {
            }
            callTelemetryService.recordSentimentEvent(
                    callId,
                    "SENTIMENT_COMBINED",
                    "COMBINED",
                    actorId,
                    parseUserId(body.get("otherPartyId")),
                    body.get("captureMode"),
                    null,
                        telemetryPayload,
                    "ERROR",
                    e.getMessage()
            );
            throw e;
        } catch (Exception e) {
            Long actorId = null;
            try {
                actorId = getCurrentUser().getId();
            } catch (Exception ignored) {
            }
            callTelemetryService.recordSentimentEvent(
                    callId,
                    "SENTIMENT_COMBINED",
                    "COMBINED",
                    actorId,
                    parseUserId(body.get("otherPartyId")),
                    body.get("captureMode"),
                    null,
                        telemetryPayload,
                    "ERROR",
                    e.getMessage()
            );
            log.error("Combined sentiment failed for call {}: {}", callId, e.getMessage(), e);
            throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "Combined sentiment failed: " + e.getMessage());
        }
    }

    @GetMapping("/{callId}/telemetry")
    @Operation(summary = "Get stored call telemetry events")
    public ResponseEntity<List<com.careconnect.model.CallTelemetryEvent>> getCallTelemetry(@PathVariable String callId) {
        User currentUser = getCurrentUser();
        List<com.careconnect.model.CallTelemetryEvent> events = callTelemetryService.getTelemetryForCall(callId);
        boolean isAdmin = currentUser.getRole() == com.careconnect.security.Role.ADMIN;
        boolean isParticipant = events.stream().anyMatch(e ->
                currentUser.getId().equals(e.getActorUserId()) || currentUser.getId().equals(e.getTargetUserId())
        );
        if (!isAdmin && !events.isEmpty() && !isParticipant) {
            throw new AppException(HttpStatus.FORBIDDEN, "Access denied");
        }
        return ResponseEntity.ok(events);
    }

    @GetMapping("/{callId}/transcription/debug")
    @Operation(summary = "Get Chime transcription debug state for a call")
    public ResponseEntity<Map<String, Object>> getTranscriptionDebugStatus(@PathVariable String callId) {
        User currentUser = getCurrentUser();
        Map<String, Object> status = new HashMap<>(chimeService.getTranscriptionDebugStatus(callId));
        status.put("requestedByUserId", currentUser.getId());
        status.put("requestedByRole", currentUser.getRole().name());
        return ResponseEntity.ok(status);
    }

    @PostMapping("/{callId}/transcript/segments")
    @Operation(summary = "Persist transcript segments for a call")
    public ResponseEntity<Map<String, Object>> saveTranscriptSegments(
            @PathVariable String callId,
            @RequestBody Map<String, Object> body
    ) {
        if (callId == null || callId.trim().isEmpty() || callId.length() > 120) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Invalid callId");
        }
        User currentUser = getCurrentUser();
        boolean isAdmin = currentUser.getRole() == com.careconnect.security.Role.ADMIN;
        if (!isAdmin && !isCallParticipant(callId, currentUser.getId())) {
            throw new AppException(HttpStatus.FORBIDDEN, "Only call participants can persist transcript segments");
        }
        List<CallTranscriptService.TranscriptSegmentInput> segments = extractTranscriptSegments(body);
        if (segments.size() > 200) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Too many transcript segments in one request");
        }
        int saved = callTranscriptService.recordSegments(callId, currentUser.getId(), segments);
        log.info("Saved {} transcript segments for callId={} by userId={}", saved, callId, currentUser.getId());
        return ResponseEntity.ok(Map.of(
                "callId", callId,
                "savedSegments", saved,
                "status", "saved"
        ));
    }

    @GetMapping("/{callId}/summary")
    @Operation(summary = "Get latest stored call summary")
    public ResponseEntity<Map<String, Object>> getCallSummary(@PathVariable String callId) {
        User currentUser = getCurrentUser();
        boolean isAdmin = currentUser.getRole() == com.careconnect.security.Role.ADMIN;
        boolean inTelemetry = callTelemetryService.getTelemetryForCall(callId).stream().anyMatch(e ->
                currentUser.getId().equals(e.getActorUserId()) || currentUser.getId().equals(e.getTargetUserId())
        );
        boolean inTranscript = callTranscriptService.hasTranscriptAccess(callId, currentUser.getId());

        Optional<com.careconnect.model.CallSummary> latestEntity = callSummaryService.getLatestSummaryEntity(callId);
        boolean isSummaryOwner = latestEntity
                .map(s -> currentUser.getId().equals(s.getGeneratedByUserId()))
                .orElse(false);

        if (!isAdmin && !inTelemetry && !inTranscript && !isSummaryOwner) {
            throw new AppException(HttpStatus.FORBIDDEN, "Access denied");
        }

        // If end-call summary ran before transcript retries landed, regenerate on read.
        if (latestEntity.isPresent()
                && "NO_TRANSCRIPT".equalsIgnoreCase(latestEntity.get().getStatus())
                && callTranscriptService.countSegments(callId) > 0) {
            Map<String, CallTelemetryEvent> latestByChannel = callTelemetryService.getLatestSentimentByChannel(callId);
            callSummaryService.generateAndStoreSummary(callId, currentUser.getId(), latestByChannel);
        }

        return callSummaryService.getLatestSummary(callId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                        "callId", callId,
                        "status", "NOT_FOUND",
                        "message", "No stored summary found for this call"
                )));
    }

    @GetMapping("/{callId}/transcript/segments")
    @Operation(summary = "Get stored transcript segments for a call")
    public ResponseEntity<List<com.careconnect.model.CallTranscriptSegment>> getTranscriptSegments(
            @PathVariable String callId
    ) {
        User currentUser = getCurrentUser();
        boolean isAdmin = currentUser.getRole() == com.careconnect.security.Role.ADMIN;
        boolean inTelemetry = callTelemetryService.getTelemetryForCall(callId).stream().anyMatch(e ->
                currentUser.getId().equals(e.getActorUserId()) || currentUser.getId().equals(e.getTargetUserId())
        );
        boolean inTranscript = callTranscriptService.hasTranscriptAccess(callId, currentUser.getId());
        if (!isAdmin && !inTelemetry && !inTranscript) {
            throw new AppException(HttpStatus.FORBIDDEN, "Access denied");
        }
        return ResponseEntity.ok(callTranscriptService.getSegmentsForCall(callId));
    }

    @DeleteMapping("/{callId}/telemetry")
    @Operation(summary = "Delete the full stored call footprint for a call (dev/local only)")
    public ResponseEntity<Map<String, Object>> deleteCallTelemetry(@PathVariable String callId) {
        ensureDevOrLocalMode();

        User currentUser = getCurrentUser();
        long deletedEvents = callTelemetryService.deleteTelemetryForCall(callId);
        long deletedSummaries = callSummaryService.deleteSummariesForCall(callId);
        Map<String, Long> transcriptPurge = callTranscriptService.purgeForCall(callId);
        Map<String, Object> recordingPurge = callRecordingService.purgeRecordingsForCall(callId);

        long deletedTranscriptSegments =
                (transcriptPurge.get("deletedTranscriptSegments") == null)
                        ? 0L
                        : transcriptPurge.get("deletedTranscriptSegments");
        long deletedTranscriptArchives =
                (transcriptPurge.get("deletedTranscriptArchives") == null)
                        ? 0L
                        : transcriptPurge.get("deletedTranscriptArchives");
        long deletedRecordingRows =
                (recordingPurge.get("deletedDbRows") instanceof Number n)
                        ? n.longValue()
                        : 0L;
        long deletedRecordingObjects =
                (recordingPurge.get("deletedS3Objects") instanceof Number n)
                        ? n.longValue()
                        : 0L;

        log.warn("Deleted call footprint for call {} by user {} (dev/local mode): telemetry={}, summaries={}, transcriptSegments={}, transcriptArchives={}, recordingRows={}, recordingObjects={}",
                callId, currentUser.getId(), deletedEvents, deletedSummaries, deletedTranscriptSegments,
                deletedTranscriptArchives, deletedRecordingRows, deletedRecordingObjects);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("callId", callId);
        response.put("deletedEvents", deletedEvents);
        response.put("deletedSummaries", deletedSummaries);
        response.put("deletedTranscriptSegments", deletedTranscriptSegments);
        response.put("deletedTranscriptArchives", deletedTranscriptArchives);
        response.put("deletedRecordingRows", deletedRecordingRows);
        response.put("deletedRecordingS3Objects", deletedRecordingObjects);
        response.put("status", "deleted");
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/patients/{patientUserId}/telemetry")
    @Operation(summary = "Delete the full stored call footprint for a patient call history tile (dev/local only)")
    public ResponseEntity<Map<String, Object>> deletePatientCallHistory(@PathVariable Long patientUserId) {
        ensureDevOrLocalMode();

        User currentUser = getCurrentUser();
        CallTelemetryService.PatientCallHistoryMatch match = callTelemetryService.findCallHistoryForPatient(patientUserId);

        long deletedSummaries = 0L;
        long deletedTranscriptSegments = 0L;
        long deletedTranscriptArchives = 0L;
        long deletedRecordingRows = 0L;
        long deletedRecordingObjects = 0L;

        for (String callId : match.callIds()) {
            deletedSummaries += callSummaryService.deleteSummariesForCall(callId);

            Map<String, Long> transcriptPurge = callTranscriptService.purgeForCall(callId);
            deletedTranscriptSegments += transcriptPurge.getOrDefault("deletedTranscriptSegments", 0L);
            deletedTranscriptArchives += transcriptPurge.getOrDefault("deletedTranscriptArchives", 0L);

            Map<String, Object> recordingPurge = callRecordingService.purgeRecordingsForCall(callId);
            if (recordingPurge.get("deletedDbRows") instanceof Number deletedDbRows) {
                deletedRecordingRows += deletedDbRows.longValue();
            }
            if (recordingPurge.get("deletedS3Objects") instanceof Number deletedS3Objects) {
                deletedRecordingObjects += deletedS3Objects.longValue();
            }
        }

        long deletedEvents = callTelemetryService.deleteTelemetryEvents(match.events());

        log.warn("Deleted patient call history for patientUserId {} by user {} (dev/local mode): telemetry={}, calls={}, summaries={}, transcriptSegments={}, transcriptArchives={}, recordingRows={}, recordingObjects={}",
                patientUserId, currentUser.getId(), deletedEvents, match.callIds().size(), deletedSummaries,
                deletedTranscriptSegments, deletedTranscriptArchives, deletedRecordingRows, deletedRecordingObjects);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("patientUserId", patientUserId);
        response.put("deletedEvents", deletedEvents);
        response.put("deletedCalls", match.callIds().size());
        response.put("deletedSummaries", deletedSummaries);
        response.put("deletedTranscriptSegments", deletedTranscriptSegments);
        response.put("deletedTranscriptArchives", deletedTranscriptArchives);
        response.put("deletedRecordingRows", deletedRecordingRows);
        response.put("deletedRecordingS3Objects", deletedRecordingObjects);
        response.put("status", "deleted");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/telemetry/my")
    @Operation(summary = "Get telemetry for current user participation")
    public ResponseEntity<List<com.careconnect.model.CallTelemetryEvent>> getMyTelemetry() {
        User currentUser = getCurrentUser();
        return ResponseEntity.ok(callTelemetryService.getTelemetryForUser(currentUser.getId()));
    }

    @GetMapping("/sentiment-history")
    @Operation(summary = "Get longitudinal per-call sentiment summaries for a user")
    public ResponseEntity<List<Map<String, Object>>> getSentimentHistory(
            @RequestParam Long userId
    ) {
        User currentUser = getCurrentUser();
        if (!canAccessSentimentHistory(currentUser, userId)) {
            throw new AppException(HttpStatus.FORBIDDEN, "Access denied");
        }
        return ResponseEntity.ok(callTelemetryService.getSentimentHistoryForUser(userId));
    }

    private void broadcastSentimentToCaregivers(
            String callId,
            String userId,
            String otherPartyId,
            SentimentResult result,
            String captureMode) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "sentiment-update");
        notification.put("callId", callId);
        notification.put("sentiment", result);
        if (captureMode != null && !captureMode.isBlank()) {
            notification.put("captureMode", captureMode.trim().toUpperCase());
        }

        sendSentimentToCaregiverIfEligible(userId, notification);
        sendSentimentToCaregiverIfEligible(otherPartyId, notification);
    }

    private void sendSentimentToCaregiverIfEligible(String userId, Map<String, Object> notification) {
        if (userId == null || userId.isBlank()) {
            return;
        }

        try {
            Long parsedUserId = Long.parseLong(userId);
            userRepository.findById(parsedUserId).ifPresent(targetUser -> {
                if (targetUser.getRole() == com.careconnect.security.Role.CAREGIVER) {
                    callNotificationHandler.sendNotificationToUser(targetUser.getId().toString(), notification);
                }
            });
        } catch (NumberFormatException ex) {
            log.warn("Skipping sentiment recipient with invalid userId: {}", userId);
        }
    }

    private void ensureSentimentEnabledForCall(String callId) {
        if (isCareTeamCall(callId)) {
            throw new AppException(
                    HttpStatus.FORBIDDEN,
                    "Sentiment analysis is disabled for care-team calls"
            );
        }
    }

    private boolean isCareTeamCall(String callId) {
        if (callId == null || callId.isBlank()) {
            return false;
        }

        return callTelemetryService.getTelemetryForCall(callId).stream().anyMatch(event -> {
            String metadataJson = event.getMetadataJson();
            if (metadataJson == null || metadataJson.isBlank()) {
                return false;
            }
            String normalized = metadataJson.toUpperCase(Locale.ROOT);
            return normalized.contains("\"CALLKIND\":\"CARE_TEAM\"")
                    || normalized.contains("\"CALLKIND\": \"CARE_TEAM\"");
        });
    }

    private Long parseUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(userId.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Double parseDouble(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Map<String, Object> sanitizeTelemetryPayload(Map<String, String> body) {
        Map<String, Object> sanitized = new HashMap<>();
        if (body == null || body.isEmpty()) {
            return sanitized;
        }

        if (body.containsKey("captureMode")) {
            sanitized.put("captureMode", body.get("captureMode"));
        }
        if (body.containsKey("audioFormat")) {
            sanitized.put("audioFormat", body.get("audioFormat"));
        }
        if (body.containsKey("imageFormat")) {
            sanitized.put("imageFormat", body.get("imageFormat"));
        }
        if (body.containsKey("otherPartyId")) {
            sanitized.put("status", "TARGET_PRESENT");
        }
        if (body.containsKey("averageLevel")) {
            sanitized.put("averageLevel", body.get("averageLevel"));
        }
        if (body.containsKey("speechRatio")) {
            sanitized.put("speechRatio", body.get("speechRatio"));
        }
        if (body.containsKey("variability")) {
            sanitized.put("variability", body.get("variability"));
        }

        String text = body.get("text");
        if (text != null) {
            sanitized.put("textLength", text.length());
        }

        return sanitized;
    }

    private Map<String, Object> sanitizeCombinedTelemetry(Map<String, Object> combined) {
        if (combined == null || combined.isEmpty()) {
            return Map.of();
        }

        Object overallRaw = combined.get("overall");
        if (!(overallRaw instanceof Map<?, ?> overallMap)) {
            return Map.of();
        }

        Map<String, Object> safe = new HashMap<>();
        Object score = overallMap.get("score");
        Object label = overallMap.get("label");
        if (score != null) {
            safe.put("overallScore", score);
        }
        if (label != null) {
            safe.put("overallLabel", label.toString());
        }
        Object timestamp = combined.get("timestamp");
        if (timestamp != null) {
            safe.put("timestamp", timestamp);
        }

        for (String debugKey : List.of(
                "dbgTs", "dbgVs", "dbgIs",
                "dbgTw", "dbgVw", "dbgIw",
                "dbgTc", "dbgVc", "dbgIc", "dbgCf")) {
            Object debugValue = combined.get(debugKey);
            if (debugValue != null) {
                safe.put(debugKey, debugValue);
            }
        }

        return safe;
    }

    private void maybeRecordFinalOverallSentiment(String callId, Long actorUserId, Long targetUserId) {
        try {
            Map<String, CallTelemetryEvent> latestByChannel = callTelemetryService.getLatestSentimentByChannel(callId);
            if (latestByChannel.isEmpty()) {
                return;
            }

            Map<String, SentimentResult> channelResults = new LinkedHashMap<>();
            for (Map.Entry<String, CallTelemetryEvent> entry : latestByChannel.entrySet()) {
                CallTelemetryEvent event = entry.getValue();
                if (event == null || event.getSentimentScore() == null) {
                    continue;
                }
                String channel = entry.getKey().trim().toUpperCase(Locale.ROOT);
                channelResults.put(channel, new SentimentResult(
                        event.getSentimentScore(),
                    event.getSentimentLabel() == null ? "ANXIOUS" : event.getSentimentLabel(),
                        event.getSentimentNotes() == null ? "" : event.getSentimentNotes(),
                        channel,
                        callId,
                        event.getAnalysisTimestamp() == null ? System.currentTimeMillis() : event.getAnalysisTimestamp(),
                        false
                ));
            }

            if (channelResults.isEmpty()) {
                return;
            }

            SentimentResult finalResult = sentimentService.analyzeFinalOverallSentiment(callId, channelResults);
            callTelemetryService.recordSentimentEvent(
                    callId,
                    "SENTIMENT_FINAL",
                    "COMBINED",
                    actorUserId,
                    targetUserId,
                    "END_CALL",
                    finalResult,
                    Map.of(
                            "overallScore", finalResult.score(),
                            "overallLabel", finalResult.label(),
                            "status", "FINAL_END_CALL"
                    ),
                    "SUCCESS",
                    null
            );
        } catch (Exception ex) {
            log.warn("Final end-call sentiment analysis skipped for callId {}: {}", callId, ex.getMessage());
        }
    }

    private Map<String, Object> extractCallContextMetadata(Map<String, Object> body) {
        if (body == null || body.isEmpty()) {
            return Map.of();
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        String callKind = asString(body.get("callKind"));
        if (callKind != null) {
            metadata.put("callKind", callKind.toUpperCase(Locale.ROOT));
        }

        Object rawContextIds = body.get("contextPatientUserIds");
        List<Long> contextPatientUserIds = new ArrayList<>();
        if (rawContextIds instanceof List<?> list) {
            for (Object item : list) {
                Long parsed = asLong(item);
                if (parsed != null && parsed > 0L && !contextPatientUserIds.contains(parsed)) {
                    contextPatientUserIds.add(parsed);
                }
            }
        }

        if (contextPatientUserIds.isEmpty()) {
            Long singleContext = asLong(body.get("contextPatientUserId"));
            if (singleContext != null && singleContext > 0L) {
                contextPatientUserIds.add(singleContext);
            }
        }

        if (!contextPatientUserIds.isEmpty()) {
            metadata.put("contextPatientUserIds", contextPatientUserIds);
            metadata.put("contextPatientUserId", contextPatientUserIds.get(0));
        }

        return metadata;
    }

    private Map<String, Object> mergeMetadata(Map<String, Object> base, Map<String, Object> extras) {
        if ((base == null || base.isEmpty()) && (extras == null || extras.isEmpty())) {
            return Map.of();
        }
        if (extras == null || extras.isEmpty()) {
            return base == null ? Map.of() : base;
        }

        Map<String, Object> merged = new LinkedHashMap<>();
        if (base != null && !base.isEmpty()) {
            merged.putAll(base);
        }
        merged.putAll(extras);
        return merged;
    }

    private void maybeGenerateAndStoreCallSummary(String callId, Long actorUserId) {
        try {
            Map<String, CallTelemetryEvent> latestByChannel = callTelemetryService.getLatestSentimentByChannel(callId);
            callSummaryService.generateAndStoreSummary(callId, actorUserId, latestByChannel);
        } catch (Exception ex) {
            log.warn("Call summary generation skipped for callId {}: {}", callId, ex.getMessage());
        }
    }

    private List<CallTranscriptService.TranscriptSegmentInput> extractTranscriptSegments(Map<String, Object> body) {
        if (body == null || body.isEmpty()) {
            return List.of();
        }

        List<CallTranscriptService.TranscriptSegmentInput> segments = new ArrayList<>();
        Object rawSegments = body.get("segments");
        if (rawSegments instanceof List<?> segmentList) {
            for (Object rawSegment : segmentList) {
                if (!(rawSegment instanceof Map<?, ?> map)) {
                    continue;
                }
                segments.add(toTranscriptInput(map));
            }
            return segments;
        }

        segments.add(new CallTranscriptService.TranscriptSegmentInput(
                asString(body.get("speakerLabel")),
                asString(body.get("text")),
                asLong(body.get("startMs")),
                asLong(body.get("endMs")),
                asString(body.get("source"))
        ));
        return segments;
    }

    private CallTranscriptService.TranscriptSegmentInput toTranscriptInput(Map<?, ?> rawSegment) {
        return new CallTranscriptService.TranscriptSegmentInput(
                asString(rawSegment.get("speakerLabel")),
                asString(rawSegment.get("text")),
                asLong(rawSegment.get("startMs")),
                asLong(rawSegment.get("endMs")),
                asString(rawSegment.get("source"))
        );
    }

    private String asString(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(value.toString().trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private boolean isCallParticipant(String callId, Long userId) {
        if (callId == null || callId.isBlank() || userId == null) {
            return false;
        }
        return callTelemetryService.getTelemetryForCall(callId).stream().anyMatch(e ->
                userId.equals(e.getActorUserId()) || userId.equals(e.getTargetUserId())
        );
    }

    private boolean canAccessSentimentHistory(User currentUser, Long requestedUserId) {
        if (currentUser == null || requestedUserId == null) {
            return false;
        }
        if (currentUser.getRole() == com.careconnect.security.Role.ADMIN) {
            return true;
        }
        if (currentUser.getId().equals(requestedUserId)) {
            return true;
        }
        return currentUser.getRole() == com.careconnect.security.Role.CAREGIVER
                && caregiverPatientLinkService.hasAccessToPatient(currentUser.getId(), requestedUserId);
    }

    // ================================================================
    // RECORDING ENDPOINTS
    // ================================================================

    @PostMapping("/{callId}/recording/start")
    @Operation(summary = "Start recording a call via AWS Chime Media Capture Pipeline")
    public ResponseEntity<Map<String, Object>> startRecording(@PathVariable String callId) {
        User currentUser = getCurrentUser();
        Map<String, Object> result = callRecordingService.startRecording(callId, currentUser.getId());
        callTelemetryService.recordCallEvent(
                callId,
                "RECORDING_START",
                currentUser.getId(),
                null,
                result.getOrDefault("status", "UNKNOWN").toString(),
                Map.of("recordingEnabled", !result.containsKey("message") || !"DISABLED".equals(result.get("status"))),
                result.containsKey("message") ? result.get("message").toString() : null
        );
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{callId}/recording/stop")
    @Operation(summary = "Stop the active recording pipeline for a call")
    public ResponseEntity<Map<String, Object>> stopRecording(@PathVariable String callId) {
        User currentUser = getCurrentUser();
        Map<String, Object> result = callRecordingService.stopRecording(callId);
        callTelemetryService.recordCallEvent(
                callId,
                "RECORDING_STOP",
                currentUser.getId(),
                null,
                result.getOrDefault("status", "UNKNOWN").toString(),
                Map.of(),
                null
        );
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{callId}/recording")
    @Operation(summary = "Get recording status and metadata for a call")
    public ResponseEntity<Map<String, Object>> getRecordingStatus(@PathVariable String callId) {
        User currentUser = getCurrentUser();
        boolean isAdmin = currentUser.getRole() == com.careconnect.security.Role.ADMIN;
        boolean isParticipant = isCallParticipant(callId, currentUser.getId());
        boolean isCaregiver = currentUser.getRole() == com.careconnect.security.Role.CAREGIVER;
        if (!isAdmin && !isParticipant && !isCaregiver) {
            throw new AppException(HttpStatus.FORBIDDEN, "Access denied");
        }
        return ResponseEntity.ok(callRecordingService.getRecordingStatus(callId));
    }

    @GetMapping("/{callId}/recording/playback-url")
    @Operation(summary = "Get a presigned S3 URL for recording playback (expires in 15 minutes)")
    public ResponseEntity<Map<String, Object>> getRecordingPlaybackUrl(@PathVariable String callId) {
        User currentUser = getCurrentUser();
        boolean isAdmin = currentUser.getRole() == com.careconnect.security.Role.ADMIN;
        boolean isCaregiver = currentUser.getRole() == com.careconnect.security.Role.CAREGIVER;
        boolean isParticipant = isCallParticipant(callId, currentUser.getId());
        if (!isAdmin && !isCaregiver && !isParticipant) {
            throw new AppException(HttpStatus.FORBIDDEN, "Access denied");
        }
        callTelemetryService.recordCallEvent(
                callId,
                "RECORDING_PLAYBACK_URL_GENERATED",
                currentUser.getId(),
                null,
                "SUCCESS",
                Map.of("requestedByRole", currentUser.getRole().name()),
                null
        );
        return ResponseEntity.ok(callRecordingService.generatePlaybackUrl(callId));
    }

    @GetMapping("/recordings")
    @Operation(summary = "List all call recordings (admin and caregiver only)")
    public ResponseEntity<List<Map<String, Object>>> listRecordings(
            @RequestParam(required = false) Long userId) {
        User currentUser = getCurrentUser();
        boolean isAdmin = currentUser.getRole() == com.careconnect.security.Role.ADMIN;
        boolean isCaregiver = currentUser.getRole() == com.careconnect.security.Role.CAREGIVER;
        if (!isAdmin && !isCaregiver) {
            throw new AppException(HttpStatus.FORBIDDEN, "Only admins and caregivers can list recordings");
        }
        List<Map<String, Object>> recordings;
        if (userId != null) {
            recordings = callRecordingService.getRecordingsByUser(userId);
        } else if (isAdmin) {
            recordings = callRecordingService.getAllRecordings();
        } else {
            // Caregiver sees only recordings they initiated
            recordings = callRecordingService.getRecordingsByUser(currentUser.getId());
        }
        return ResponseEntity.ok(recordings);
    }

    @PostMapping("/{callId}/recording/cleanup-raw")
    @Operation(summary = "Delete raw recording artifacts after the stitched video is available (dev/local only)")
    public ResponseEntity<Map<String, Object>> cleanupRawRecordingArtifacts(@PathVariable String callId) {
        ensureDevOrLocalMode();
        getCurrentUser();
        return ResponseEntity.ok(callRecordingService.cleanupRawArtifactsForCall(callId));
    }

    @DeleteMapping("/recordings")
    @Operation(summary = "Purge ALL recordings from S3 and DB (dev/local only — for test cleanup)")
    public ResponseEntity<Map<String, Object>> purgeAllRecordings() {
        ensureDevOrLocalMode();
        User currentUser = getCurrentUser();
        log.warn("Recording purge requested by user {} (dev/local mode)", currentUser.getId());
        Map<String, Object> result = callRecordingService.purgeAllRecordings();
        return ResponseEntity.ok(result);
    }

}


