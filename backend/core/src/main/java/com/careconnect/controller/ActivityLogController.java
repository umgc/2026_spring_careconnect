package com.careconnect.controller;

import com.careconnect.dto.ActivityLogDtos;
import com.careconnect.exception.AppException;
import com.careconnect.model.ActivityLog;
import com.careconnect.model.Patient;
import com.careconnect.model.User;
import com.careconnect.repository.ActivityLogRepository;
import com.careconnect.repository.UserRepository;
import com.careconnect.service.CaregiverPatientLinkService;
import com.careconnect.service.FamilyMemberService;
import com.careconnect.service.PatientService;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/v1/api/activity-logs")
public class ActivityLogController {

    private final PatientService patientService;
    private final UserRepository userRepository;
    private final CaregiverPatientLinkService caregiverPatientLinkService;
    private final FamilyMemberService familyMemberService;
    private final ActivityLogRepository activityLogRepository;

    public ActivityLogController(
            PatientService patientService,
            UserRepository userRepository,
            CaregiverPatientLinkService caregiverPatientLinkService,
            FamilyMemberService familyMemberService,
            ActivityLogRepository activityLogRepository
    ) {
        this.patientService = patientService;
        this.userRepository = userRepository;
        this.caregiverPatientLinkService = caregiverPatientLinkService;
        this.familyMemberService = familyMemberService;
        this.activityLogRepository = activityLogRepository;
    }

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "User not authenticated"));
    }

    private void validateAccessToPatient(Long patientId, User currentUser) {
        Patient patient = patientService.getPatientById(patientId);
        Long patientUserId = patient.getUser().getId();
        switch (currentUser.getRole()) {
            case PATIENT:
                if (!currentUser.getId().equals(patientUserId)) {
                    throw new AppException(HttpStatus.FORBIDDEN, "Access denied");
                }
                break;
            case CAREGIVER:
                if (!caregiverPatientLinkService.hasAccessToPatient(currentUser.getId(), patientUserId)) {
                    throw new AppException(HttpStatus.FORBIDDEN, "Access denied");
                }
                break;
            case FAMILY_MEMBER:
                if (!familyMemberService.hasAccessToPatient(currentUser.getId(), patientUserId)) {
                    throw new AppException(HttpStatus.FORBIDDEN, "Access denied");
                }
                break;
            case ADMIN:
                break;
            default:
                throw new AppException(HttpStatus.FORBIDDEN, "Access denied");
        }
    }

    @PostMapping
    public ResponseEntity<?> createActivityLog(@RequestBody ActivityLogDtos.CreateActivityLogRequest req) {
        User currentUser = getCurrentUser();

        if (req.getClientId() == null || req.getActivityId() == null || req.getCompetencyScore() == null) {
            throw new AppException(HttpStatus.BAD_REQUEST, "clientId, activityId, and competencyScore are required");
        }
        if (req.getCompetencyScore() < 1 || req.getCompetencyScore() > 10) {
            throw new AppException(HttpStatus.BAD_REQUEST, "competencyScore out of range");
        }
        if (req.getSatisfactionRating() != null
                && (req.getSatisfactionRating() < 1 || req.getSatisfactionRating() > 3)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "satisfactionRating out of range");
        }

        validateAccessToPatient(req.getClientId(), currentUser);

        String activityName = req.getActivityName() != null ? req.getActivityName().trim() : null;
        if (activityName != null && activityName.isEmpty()) activityName = null;

        ActivityLog saved = activityLogRepository.save(ActivityLog.builder()
                .clientId(req.getClientId())
                .activityId(req.getActivityId())
                .activityName(activityName)
                .caregiverUserId(currentUser.getId()) // from session, not request
                .competencyScore(req.getCompetencyScore())
                .satisfactionRating(req.getSatisfactionRating())
                .notes(req.getNotes())
                .build());

        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping
    public ResponseEntity<List<ActivityLogDtos.ActivityLogResponse>> getActivityLogs(
            @RequestParam Long clientId,
            @RequestParam(required = false, defaultValue = "100") int limit
    ) {
        User currentUser = getCurrentUser();
        validateAccessToPatient(clientId, currentUser);

        int safeLimit = Math.min(Math.max(limit, 1), 500);
        List<ActivityLog> logs = activityLogRepository.findByClientIdOrderByCreatedAtDesc(
                clientId, PageRequest.of(0, safeLimit));

        List<ActivityLogDtos.ActivityLogResponse> body = logs.stream()
                .map(log -> new ActivityLogDtos.ActivityLogResponse(
                        log.getId(),
                        log.getClientId(),
                        log.getActivityId(),
                        log.getActivityName(),
                        log.getCompetencyScore(),
                        log.getSatisfactionRating(),
                        log.getNotes(),
                        log.getCreatedAt()
                ))
                .collect(Collectors.toList());
        return ResponseEntity.ok(body);
    }
}

