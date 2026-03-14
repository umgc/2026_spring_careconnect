package com.careconnect.controller;

import com.careconnect.exception.AppException;
import com.careconnect.model.Mood;
import com.careconnect.model.User;
import com.careconnect.repository.UserRepository;
import com.careconnect.service.CaregiverPatientLinkService;
import com.careconnect.service.FamilyMemberService;
import com.careconnect.service.MoodService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import com.careconnect.model.User;
import com.careconnect.security.AuthorizationService;
import com.careconnect.security.UnauthorizedException;
import com.careconnect.util.SecurityUtil;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/v1/api/patient", "/api/patient"})
public class MoodController {

    @Autowired
    private MoodService moodService;

    @Autowired
    private SecurityUtil securityUtil;

    @Autowired
    private AuthorizationService authorizationService;

    @PostMapping("/{userId}/mood")
    public ResponseEntity<Mood> saveMood(
            @PathVariable Long userId,
            @RequestBody Map<String, Object> payload) throws UnauthorizedException {

        User currentUser = securityUtil.resolveCurrentUser();
        authorizationService.requireSelfOrAdmin(currentUser, userId);
        int score = (int) payload.get("score");
        String label = (String) payload.get("label");

        Mood savedMood = moodService.saveMood(userId, score, label);
        return ResponseEntity.ok(savedMood);
    }

    @GetMapping("/caregiver/{caregiverId}/moods")
    public ResponseEntity<Map<String, Object>> getCaregiverMoodSummaries(@PathVariable Long caregiverId) throws UnauthorizedException {
        User currentUser = securityUtil.resolveCurrentUser();
        authorizationService.requireSelfOrAdmin(currentUser, caregiverId);
        Map<String, Object> data = new HashMap<>();

        List<Long> patientIds = List.of(1L, 2L, 3L);
        List<Map<String, Object>> summaries = new ArrayList<>();

        for (Long patientId : patientIds) {
            List<Mood> moods = moodService.getMoods(patientId);
            if (!moods.isEmpty()) {
                Mood latest = moods.get(0);
                Map<String, Object> summary = new HashMap<>();
                summary.put("patientId", patientId);
                summary.put("score", latest.getScore());
                summary.put("label", latest.getLabel());
                summary.put("createdAt", latest.getCreatedAt());
                summaries.add(summary);
            }
        }

        data.put("caregiverId", caregiverId);
        data.put("summaries", summaries);
        return ResponseEntity.ok(data);
    }

    @GetMapping("/{userId}/mood")
    public ResponseEntity<List<Mood>> getMoods(@PathVariable Long userId) throws UnauthorizedException {
        User currentUser = securityUtil.resolveCurrentUser();
        authorizationService.requireSelfOrAdmin(currentUser, userId);
        List<Mood> moods = moodService.getMoods(userId);
        return ResponseEntity.ok(moods);
    }
}
