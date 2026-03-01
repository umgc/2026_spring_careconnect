package com.careconnect.controller;

import com.careconnect.exception.AppException;
import com.careconnect.model.User;
import com.careconnect.repository.UserRepository;
import com.careconnect.service.BedrockSentimentService;
import com.careconnect.service.BedrockSentimentService.SentimentResult;
import com.careconnect.service.ChimeService;
import com.careconnect.service.CallTelemetryService;

import com.careconnect.websocket.CallNotificationHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v3/calls")
@Tag(name = "Calls", description = "Video call and sentiment analysis endpoints")
@SecurityRequirement(name = "Bearer Authentication")
public class CallController {

    private static final Logger log = LoggerFactory.getLogger(CallController.class);

    @Autowired private ChimeService chimeService;
    @Autowired private BedrockSentimentService sentimentService;
    @Autowired private CallNotificationHandler callNotificationHandler;
    @Autowired private CallTelemetryService callTelemetryService;
    @Autowired private UserRepository userRepository;

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

    @PostMapping("/{callId}/join")
    @Operation(summary = "Join or create a Chime meeting for a call")
    public ResponseEntity<java.util.Map<String, Object>> joinCall(@PathVariable String callId) {
        try {
            User currentUser = getCurrentUser();
            Map<String, Object> response = chimeService.joinMeeting(callId, currentUser.getId().toString());
            callTelemetryService.recordCallEvent(
                    callId,
                    "CALL_JOIN",
                    currentUser.getId(),
                    null,
                    "SUCCESS",
                    Map.of("meetingActive", chimeService.isMeetingActive(callId)),
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
            @RequestBody(required = false) Map<String, String> body) {
        try {
            User currentUser = getCurrentUser();
            chimeService.endMeeting(callId);
            if ((otherPartyId == null || otherPartyId.isBlank()) && body != null) {
                otherPartyId = body.get("otherPartyId");
            }
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
                    Map.of("notifiedOtherParty", otherPartyId != null && !otherPartyId.isBlank()),
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
        final Map<String, Object> telemetryPayload = new HashMap<>(body);
        try {
            User currentUser = getCurrentUser();
            ensurePatientSource(currentUser);
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
                    Map.of("text", text),
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
        final Map<String, Object> telemetryPayload = new HashMap<>(body);
        try {
            User currentUser = getCurrentUser();
            ensurePatientSource(currentUser);
            String audioBase64 = body.get("audioBase64");
            String audioFormat = body.getOrDefault("audioFormat", "wav");
            if (audioBase64 == null || audioBase64.isBlank())
                throw new AppException(HttpStatus.BAD_REQUEST, "audioBase64 field is required");
            SentimentResult result = sentimentService.analyzeVoice(audioBase64, callId, audioFormat);
                callTelemetryService.recordSentimentEvent(
                    callId,
                    "SENTIMENT_VOICE",
                    "VOICE",
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

    @PostMapping("/{callId}/sentiment/video")
    @Operation(summary = "Analyze sentiment from a video frame (base64 encoded image)")
    public ResponseEntity<SentimentResult> analyzeVideoSentiment(
            @PathVariable String callId,
            @RequestBody Map<String, String> body) {
        final Map<String, Object> telemetryPayload = new HashMap<>(body);
        try {
            User currentUser = getCurrentUser();
            ensurePatientSource(currentUser);
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
        final Map<String, Object> telemetryPayload = new HashMap<>(body);
        try {
            User currentUser = getCurrentUser();
            ensurePatientSource(currentUser);
            String text = body.getOrDefault("text", "");
            String audioBase64 = body.getOrDefault("audioBase64", "");
            String audioFormat = body.getOrDefault("audioFormat", "wav");
            String imageBase64 = body.getOrDefault("imageBase64", "");
            String imageFormat = body.getOrDefault("imageFormat", "jpeg");
            SentimentResult textResult = text.isBlank() ? null : sentimentService.analyzeText(text, callId);
            SentimentResult voiceResult = audioBase64.isBlank() ? null : sentimentService.analyzeVoice(audioBase64, callId, audioFormat);
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
                    combined,
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

    @GetMapping("/telemetry/my")
    @Operation(summary = "Get telemetry for current user participation")
    public ResponseEntity<List<com.careconnect.model.CallTelemetryEvent>> getMyTelemetry() {
        User currentUser = getCurrentUser();
        return ResponseEntity.ok(callTelemetryService.getTelemetryForUser(currentUser.getId()));
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
}


