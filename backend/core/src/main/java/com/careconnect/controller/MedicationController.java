package com.careconnect.controller;

import com.careconnect.security.Permission;
import com.careconnect.security.RequirePermission;

import com.careconnect.dto.MedicationDTO;
import com.careconnect.service.MedicationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.careconnect.model.User;
import com.careconnect.security.AuthorizationService;
import com.careconnect.security.UnauthorizedException;
import com.careconnect.util.SecurityUtil;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v3/api/patients")
@Tag(name = "Medication Management", description = "Endpoints for managing patient medications")
public class MedicationController {

    @Autowired
    private MedicationService medicationService;

    @Autowired
    private SecurityUtil securityUtil;

    @Autowired
    private AuthorizationService authorizationService;

    // ================================================================
    // 1. Fetch all medications for a patient
    // ================================================================
    @RequirePermission(Permission.VIEW_ASSIGNED_PATIENTS)

    @GetMapping("/{patientId}/medications")
    public ResponseEntity<List<MedicationDTO>> getAllMedications(@PathVariable Long patientId) throws UnauthorizedException {
        User currentUser = securityUtil.resolveCurrentUser();
        authorizationService.requirePatientAccess(currentUser, patientId);
        List<MedicationDTO> allMeds = medicationService.getAllMedicationsForPatient(patientId);
        return ResponseEntity.ok(allMeds);
    }

    // ================================================================
    // 1.1 Fetch only active medications
    // ================================================================
    @RequirePermission(Permission.VIEW_ASSIGNED_PATIENTS)

    @GetMapping("/{patientId}/medications/active")
    public ResponseEntity<List<MedicationDTO>> getActiveMedications(@PathVariable Long patientId) throws UnauthorizedException {
        User currentUser = securityUtil.resolveCurrentUser();
        authorizationService.requirePatientAccess(currentUser, patientId);
        List<MedicationDTO> activeMeds = medicationService.getActiveMedicationsForPatient(patientId);
        return ResponseEntity.ok(activeMeds);
    }

    // ================================================================
    // 1.2 Fetch pending medications (approval_status = 'PENDING')
    // ================================================================
    @RequirePermission(Permission.VIEW_ASSIGNED_PATIENTS)

    @GetMapping("/{patientId}/medications/pending")
    public ResponseEntity<List<MedicationDTO>> getPendingMedications(@PathVariable Long patientId) throws UnauthorizedException {
        User currentUser = securityUtil.resolveCurrentUser();
        authorizationService.requirePatientAccess(currentUser, patientId);
        List<MedicationDTO> pending = medicationService.getPendingMedications(patientId);
        return ResponseEntity.ok(pending);
    }

    // ================================================================
    // 2. Add a new medication (creates record as PENDING)
    // ================================================================
    @RequirePermission(Permission.CREATE_TASKS)

    @PostMapping("/{patientId}/medications")
    public ResponseEntity<MedicationDTO> addMedication(
            @PathVariable Long patientId,
            @RequestBody MedicationDTO newMedication) throws UnauthorizedException {

        User currentUser = securityUtil.resolveCurrentUser();
        authorizationService.requirePatientAccess(currentUser, patientId);
        MedicationDTO createdMedication = medicationService.addMedication(patientId, newMedication);
        return ResponseEntity.ok(createdMedication);
    }

    // ================================================================
    // 3. Approve a medication (sets isActive=true, approval_status='APPROVED')
    // ================================================================
    @RequirePermission(Permission.UPDATE_TASKS)

    @PutMapping("/{patientId}/medications/{medicationId}/approve")
    public ResponseEntity<?> approveMedication(
            @PathVariable Long patientId,
            @PathVariable Long medicationId) throws UnauthorizedException {

        User currentUser = securityUtil.resolveCurrentUser();
        authorizationService.requirePatientAccess(currentUser, patientId);
        MedicationDTO approvedMedication = medicationService.approveMedication(patientId, medicationId);
        return ResponseEntity.ok(Map.of(
                "message", "Medication approved successfully",
                "approvedMedication", approvedMedication
        ));
    }

    // ================================================================
    // 4. Remove (soft delete) medication and trigger notification (Patient-side)
    // ================================================================
    @RequirePermission(Permission.DELETE_PATIENTS)

    @DeleteMapping("/{patientId}/medications/{medicationId}")
    public ResponseEntity<?> deleteMedication(
            @PathVariable Long patientId,
            @PathVariable Long medicationId) throws UnauthorizedException {

        User currentUser = securityUtil.resolveCurrentUser();
        authorizationService.requirePatientAccess(currentUser, patientId);
        medicationService.deactivateMedication(patientId, medicationId);
        return ResponseEntity.ok(Map.of(
                "message", "Medication removed and notification sent"
        ));
    }

    // ================================================================
    // 5. Hard delete medication (Caregiver-side)
    // ================================================================
    @RequirePermission(Permission.DELETE_PATIENTS)

    @DeleteMapping("/{patientId}/medications/{medicationId}/caregiver/{caregiverId}")
    public ResponseEntity<?> deleteMedicationByCaregiver(
            @PathVariable Long patientId,
            @PathVariable Long medicationId,
            @PathVariable Long caregiverId) throws UnauthorizedException {

        User currentUser = securityUtil.resolveCurrentUser();
        authorizationService.requirePatientAccess(currentUser, patientId);
        medicationService.hardDeleteMedication(patientId, medicationId, caregiverId);
        return ResponseEntity.ok(Map.of(
                "message", "Medication deleted successfully"
        ));
    }
}
