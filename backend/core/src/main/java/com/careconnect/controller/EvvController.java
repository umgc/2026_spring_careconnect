package com.careconnect.controller;

import com.careconnect.security.Permission;
import com.careconnect.security.RequirePermission;

import com.careconnect.dto.evv.*;
import com.careconnect.model.User;
import com.careconnect.model.evv.EvvRecord;
import com.careconnect.model.evv.EvvCorrection;
import com.careconnect.model.evv.EvvOfflineQueue;
import com.careconnect.security.AuthorizationService;
import com.careconnect.security.UnauthorizedException;
import com.careconnect.service.evv.EvvService;
import com.careconnect.service.evv.EvvSubmissionService;
import com.careconnect.service.evv.EvvOfflineSyncService;
import com.careconnect.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController @RequestMapping("/v1/api/evv") @RequiredArgsConstructor
public class EvvController {
    private final EvvService evvService;
    private final EvvSubmissionService submitter;
    private final EvvOfflineSyncService offlineSyncService;
    private final SecurityUtil securityUtil;
    private final AuthorizationService authorizationService;

    private static final Long DEFAULT_USER_ID = 1L;

    @RequirePermission(Permission.CREATE_TASKS)


    @PostMapping("/records")
    public ResponseEntity<EvvRecord> create(@RequestBody EvvRecordRequestDto req) throws UnauthorizedException {
        User currentUser = securityUtil.resolveCurrentUser();
        authorizationService.requireAdminOrCaregiver(currentUser);
        return ResponseEntity.ok(evvService.createRecord(req, DEFAULT_USER_ID));
    }

    @RequirePermission(Permission.CREATE_TASKS)


    @PostMapping("/records/{id}/review")
    public ResponseEntity<EvvRecord> review(@PathVariable Long id, @RequestBody EvvReviewRequest action) throws UnauthorizedException {
        User currentUser = securityUtil.resolveCurrentUser();
        authorizationService.requireAdminOrCaregiver(currentUser);
        var rec = evvService.review(id, action.isApprove(), DEFAULT_USER_ID, action.getComment());
        if (action.isApprove()) submitter.queueForSubmission(rec, DEFAULT_USER_ID);
        return ResponseEntity.ok(rec);
    }

    @RequirePermission(Permission.CREATE_TASKS)


    @PostMapping("/records/offline")
    public ResponseEntity<EvvRecord> createOfflineRecord(@RequestBody EvvRecordRequestDto req,
                                                         @RequestHeader("X-Device-ID") String deviceId) throws UnauthorizedException {
        User currentUser = securityUtil.resolveCurrentUser();
        authorizationService.requireAdminOrCaregiver(currentUser);
        return ResponseEntity.ok(evvService.createOfflineRecord(req, DEFAULT_USER_ID, deviceId));
    }

    @RequirePermission(Permission.CREATE_TASKS)


    @PostMapping("/records/correct")
    public ResponseEntity<EvvRecord> correctRecord(@RequestBody EvvCorrectionRequestDto req) throws UnauthorizedException {
        User currentUser = securityUtil.resolveCurrentUser();
        authorizationService.requireAdminOrCaregiver(currentUser);
        return ResponseEntity.ok(evvService.correctRecord(req, DEFAULT_USER_ID));
    }

    @RequirePermission(Permission.CREATE_TASKS)


    @PostMapping("/records/eor-approve")
    public ResponseEntity<EvvRecord> approveEor(@RequestBody EorApprovalRequestDto req) throws UnauthorizedException {
        User currentUser = securityUtil.resolveCurrentUser();
        authorizationService.requireAdminOrCaregiver(currentUser);
        return ResponseEntity.ok(evvService.approveEor(req, DEFAULT_USER_ID));
    }

    @RequirePermission(Permission.VIEW_ASSIGNED_PATIENTS)


    @GetMapping("/records/search")
    public ResponseEntity<Page<EvvRecord>> searchRecords(EvvSearchRequestDto searchRequest) throws UnauthorizedException {
        User currentUser = securityUtil.resolveCurrentUser();
        authorizationService.requireAdminOrCaregiver(currentUser);
        return ResponseEntity.ok(evvService.searchRecords(searchRequest));
    }

    @RequirePermission(Permission.VIEW_ASSIGNED_PATIENTS)


    @GetMapping("/records/pending-eor-approvals")
    public ResponseEntity<List<EvvRecord>> getPendingEorApprovals() throws UnauthorizedException {
        User currentUser = securityUtil.resolveCurrentUser();
        authorizationService.requireAdminOrCaregiver(currentUser);
        return ResponseEntity.ok(evvService.getPendingEorApprovals());
    }

    @RequirePermission(Permission.VIEW_ASSIGNED_PATIENTS)


    @GetMapping("/corrections/pending")
    public ResponseEntity<List<EvvCorrection>> getPendingCorrections() throws UnauthorizedException {
        User currentUser = securityUtil.resolveCurrentUser();
        authorizationService.requireAdminOrCaregiver(currentUser);
        return ResponseEntity.ok(evvService.getPendingCorrections());
    }

    @RequirePermission(Permission.CREATE_TASKS)


    @PostMapping("/corrections/{id}/approve")
    public ResponseEntity<EvvCorrection> approveCorrection(@PathVariable Long id,
                                                           @RequestParam(required = false) String comment) throws UnauthorizedException {
        User currentUser = securityUtil.resolveCurrentUser();
        authorizationService.requireAdminOrCaregiver(currentUser);
        return ResponseEntity.ok(evvService.approveCorrection(id, DEFAULT_USER_ID, comment));
    }

    @RequirePermission(Permission.VIEW_ASSIGNED_PATIENTS)


    @GetMapping("/offline/queue")
    public ResponseEntity<List<EvvOfflineQueue>> getOfflineQueue() throws UnauthorizedException {
        User currentUser = securityUtil.resolveCurrentUser();
        authorizationService.requireAdminOrCaregiver(currentUser);
        return ResponseEntity.ok(evvService.getOfflineQueue(DEFAULT_USER_ID));
    }

    @RequirePermission(Permission.CREATE_TASKS)


    @PostMapping("/offline/sync")
    public ResponseEntity<String> syncOfflineData() throws UnauthorizedException {
        User currentUser = securityUtil.resolveCurrentUser();
        authorizationService.requireAdminOrCaregiver(currentUser);
        offlineSyncService.syncCaregiverOfflineData(DEFAULT_USER_ID);
        return ResponseEntity.ok("Offline data sync initiated");
    }

    @RequirePermission(Permission.VIEW_ASSIGNED_PATIENTS)


    @GetMapping("/offline/status")
    public ResponseEntity<List<EvvOfflineQueue>> getOfflineStatus() throws UnauthorizedException {
        User currentUser = securityUtil.resolveCurrentUser();
        authorizationService.requireAdminOrCaregiver(currentUser);
        return ResponseEntity.ok(offlineSyncService.getOfflineQueueStatus(DEFAULT_USER_ID));
    }
}