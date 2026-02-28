package com.careconnect.controller;

import com.careconnect.exception.AppException;
import com.careconnect.model.User;
import com.careconnect.repository.UserRepository;
import com.careconnect.service.BedrockSentimentService;
import com.careconnect.service.BedrockSentimentService.SentimentResult;
import com.careconnect.service.ChimeService;

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
    @Autowired private UserRepository userRepository;

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String userEmail = authentication.getName();
        return userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "User not authenticated"));
    }

    @PostMapping("/{callId}/join")
    @Operation(summary = "Join or create a Chime meeting for a call")
    public ResponseEntity<java.util.Map<String, Object>> joinCall(@PathVariable String callId) {
        try {
            User currentUser = getCurrentUser();
            Map<String, Object> response = chimeService.joinMeeting(callId, currentUser.getId().toString());
            log.info("User {} joined call {}", currentUser.getId(), callId);
            return ResponseEntity.ok(response);
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
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
            log.info("User {} ended call {}", currentUser.getId(), callId);
            return ResponseEntity.ok(Map.of("status", "ended", "callId", callId));
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to end call {}: {}", callId, e.getMessage(), e);
            throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to end call: " + e.getMessage());
        }
    }

    @PostMapping("/{callId}/sentiment/text")
    @Operation(summary = "Analyze sentiment from a chat message")
    public ResponseEntity<SentimentResult> analyzeTextSentiment(
            @PathVariable String callId,
            @RequestBody Map<String, String> body) {
        try {
            User currentUser = getCurrentUser();
            String text = body.get("text");
            if (text == null || text.isBlank())
                throw new AppException(HttpStatus.BAD_REQUEST, "text field is required");
            SentimentResult result = sentimentService.analyzeText(text, callId);
            broadcastSentiment(callId, currentUser.getId().toString(), body.get("otherPartyId"), result);
            return ResponseEntity.ok(result);
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("Text sentiment failed for call {}: {}", callId, e.getMessage(), e);
            throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "Sentiment analysis failed: " + e.getMessage());
        }
    }

    @PostMapping("/{callId}/sentiment/voice")
    @Operation(summary = "Analyze sentiment from an audio clip (base64 encoded)")
    public ResponseEntity<SentimentResult> analyzeVoiceSentiment(
            @PathVariable String callId,
            @RequestBody Map<String, String> body) {
        try {
            User currentUser = getCurrentUser();
            String audioBase64 = body.get("audioBase64");
            if (audioBase64 == null || audioBase64.isBlank())
                throw new AppException(HttpStatus.BAD_REQUEST, "audioBase64 field is required");
            SentimentResult result = sentimentService.analyzeVoice(audioBase64, callId);
            broadcastSentiment(callId, currentUser.getId().toString(), body.get("otherPartyId"), result);
            return ResponseEntity.ok(result);
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("Voice sentiment failed for call {}: {}", callId, e.getMessage(), e);
            throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "Voice sentiment analysis failed: " + e.getMessage());
        }
    }

    @PostMapping("/{callId}/sentiment/video")
    @Operation(summary = "Analyze sentiment from a video frame (base64 encoded image)")
    public ResponseEntity<SentimentResult> analyzeVideoSentiment(
            @PathVariable String callId,
            @RequestBody Map<String, String> body) {
        try {
            User currentUser = getCurrentUser();
            String imageBase64 = body.get("imageBase64");
            if (imageBase64 == null || imageBase64.isBlank())
                throw new AppException(HttpStatus.BAD_REQUEST, "imageBase64 field is required");
            String imageFormat = body.getOrDefault("imageFormat", "jpeg");
            SentimentResult result = sentimentService.analyzeVideoFrame(imageBase64, imageFormat, callId);
            broadcastSentiment(callId, currentUser.getId().toString(), body.get("otherPartyId"), result);
            return ResponseEntity.ok(result);
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("Video sentiment failed for call {}: {}", callId, e.getMessage(), e);
            throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "Video sentiment analysis failed: " + e.getMessage());
        }
    }

    @PostMapping("/{callId}/sentiment/combined")
    @Operation(summary = "Get combined sentiment score across all channels")
    public ResponseEntity<java.util.Map<String, Object>> getCombinedSentiment(
            @PathVariable String callId,
            @RequestBody Map<String, String> body) {
        try {
            String text = body.getOrDefault("text", "");
            String audioBase64 = body.getOrDefault("audioBase64", "");
            String imageBase64 = body.getOrDefault("imageBase64", "");
            String imageFormat = body.getOrDefault("imageFormat", "jpeg");
            SentimentResult textResult = text.isBlank() ? null : sentimentService.analyzeText(text, callId);
            SentimentResult voiceResult = audioBase64.isBlank() ? null : sentimentService.analyzeVoice(audioBase64, callId);
            SentimentResult videoResult = imageBase64.isBlank() ? null : sentimentService.analyzeVideoFrame(imageBase64, imageFormat, callId);
            return ResponseEntity.ok(sentimentService.buildCombinedSentiment(textResult, voiceResult, videoResult, callId));
        } catch (Exception e) {
            log.error("Combined sentiment failed for call {}: {}", callId, e.getMessage(), e);
            throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "Combined sentiment failed: " + e.getMessage());
        }
    }

    private void broadcastSentiment(String callId, String userId, String otherPartyId, SentimentResult result) {
        Map<String, Object> notification = Map.of("type", "sentiment-update", "callId", callId, "sentiment", result);
        callNotificationHandler.sendNotificationToUser(userId, notification);
        if (otherPartyId != null && !otherPartyId.isBlank()) {
            callNotificationHandler.sendNotificationToUser(otherPartyId, notification);
        }
    }
}


