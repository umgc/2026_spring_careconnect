package com.careconnect.controller;

import com.careconnect.dto.MedicationDTO;
import com.careconnect.exception.AppException;
import com.careconnect.service.FeatureTelemetryService;
import com.careconnect.service.MedicationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/api/dev")
@Tag(name = "Medication Management", description = "Endpoints for managing patient medications")
public class MedicationController {

    @Autowired
    private MedicationService medicationService;

    @Autowired
    private FeatureTelemetryService telemetry;

    // ================================================================
    // 1. Fetch all medications for a patient
    // ================================================================
    @GetMapping("/patients/{patientId}/medications")
    public ResponseEntity<List<MedicationDTO>> getAllMedications(@PathVariable Long patientId) {
        long start = System.nanoTime();
        try {
            List<MedicationDTO> allMeds = medicationService.getAllMedicationsForPatient(patientId);
            emit("feature.medications.view_all_success", "getAllMedications", "success",
                    elapsedMs(start), null);
            return ResponseEntity.ok(allMeds);
        } catch (Exception ex) {
            emit("feature.medications.view_all_fail", "getAllMedications", "fail",
                    elapsedMs(start), categorize(ex));
            throw ex;
        }
    }

    // ================================================================
    // 1.1 Fetch only active medications
    // ================================================================
    @GetMapping("/patients/{patientId}/medications/active")
    public ResponseEntity<List<MedicationDTO>> getActiveMedications(@PathVariable Long patientId) {
        long start = System.nanoTime();
        try {
            List<MedicationDTO> activeMeds = medicationService.getActiveMedicationsForPatient(patientId);
            emit("feature.medications.view_active_success", "getActiveMedications", "success",
                    elapsedMs(start), null);
            return ResponseEntity.ok(activeMeds);
        } catch (Exception ex) {
            emit("feature.medications.view_active_fail", "getActiveMedications", "fail",
                    elapsedMs(start), categorize(ex));
            throw ex;
        }
    }

    // ================================================================
    // 1.2 Fetch pending medications (approval_status = 'PENDING')
    // ================================================================
    @GetMapping("/patients/{patientId}/medications/pending")
    public ResponseEntity<List<MedicationDTO>> getPendingMedications(@PathVariable Long patientId) {
        long start = System.nanoTime();
        try {
            List<MedicationDTO> pending = medicationService.getPendingMedications(patientId);
            emit("feature.medications.view_pending_success", "getPendingMedications", "success",
                    elapsedMs(start), null);
            return ResponseEntity.ok(pending);
        } catch (Exception ex) {
            emit("feature.medications.view_pending_fail", "getPendingMedications", "fail",
                    elapsedMs(start), categorize(ex));
            throw ex;
        }
    }

    // ================================================================
    // 2. Add a new medication (creates record as PENDING)
    // ================================================================
    @PostMapping("/patients/{patientId}/medications")
    public ResponseEntity<MedicationDTO> addMedication(
            @PathVariable Long patientId,
            @RequestBody MedicationDTO newMedication) {

        long start = System.nanoTime();
        try {
            MedicationDTO createdMedication = medicationService.addMedication(patientId, newMedication);
            emit("feature.medications.add_success", "addMedication", "success",
                    elapsedMs(start), null);
            return ResponseEntity.ok(createdMedication);
        } catch (Exception ex) {
            emit("feature.medications.add_fail", "addMedication", "fail",
                    elapsedMs(start), categorize(ex));
            throw ex;
        }
    }

    // ================================================================
    // 3. Approve a medication (sets isActive=true, approval_status='APPROVED')
    // ================================================================
    @PutMapping("/patients/{patientId}/medications/{medicationId}/approve")
    public ResponseEntity<?> approveMedication(
            @PathVariable Long patientId,
            @PathVariable Long medicationId) {

        long start = System.nanoTime();
        try {
            MedicationDTO approvedMedication = medicationService.approveMedication(patientId, medicationId);
            emit("feature.medications.approve_success", "approveMedication", "success",
                    elapsedMs(start), null);

            return ResponseEntity.ok(Map.of(
                    "message", "Medication approved successfully",
                    "approvedMedication", approvedMedication
            ));
        } catch (Exception ex) {
            emit("feature.medications.approve_fail", "approveMedication", "fail",
                    elapsedMs(start), categorize(ex));
            throw ex;
        }
    }

    // ================================================================
    // 4. Remove (soft delete) medication and trigger notification (Patient-side)
    // ================================================================
    @DeleteMapping("/patients/{patientId}/medications/{medicationId}")
    public ResponseEntity<?> deleteMedication(
            @PathVariable Long patientId,
            @PathVariable Long medicationId) {

        long start = System.nanoTime();
        try {
            medicationService.deactivateMedication(patientId, medicationId);
            emit("feature.medications.delete_success", "deleteMedication", "success",
                    elapsedMs(start), null);

            return ResponseEntity.ok(Map.of(
                    "message", "Medication removed and notification sent"
            ));
        } catch (Exception ex) {
            emit("feature.medications.delete_fail", "deleteMedication", "fail",
                    elapsedMs(start), categorize(ex));
            throw ex;
        }
    }

    // ================================================================
    // 5. Hard delete medication (Caregiver-side)
    // ================================================================
    @DeleteMapping("/patients/{patientId}/medications/{medicationId}/caregiver/{caregiverId}")
    public ResponseEntity<?> deleteMedicationByCaregiver(
            @PathVariable Long patientId,
            @PathVariable Long medicationId,
            @PathVariable Long caregiverId) {

        long start = System.nanoTime();
        try {
            medicationService.hardDeleteMedication(patientId, medicationId, caregiverId);
            emit("feature.medications.delete_caregiver_success", "deleteMedicationByCaregiver", "success",
                    elapsedMs(start), null);

            return ResponseEntity.ok(Map.of(
                    "message", "Medication deleted successfully"
            ));
        } catch (Exception ex) {
            emit("feature.medications.delete_caregiver_fail", "deleteMedicationByCaregiver", "fail",
                    elapsedMs(start), categorize(ex));
            throw ex;
        }
    }

    // ------------------------
    // Anonymous analytics helpers (BNS7)
    // ------------------------

    private void emit(String eventName, String action, String result, long durationMs, String errorCategory) {
        Map<String, Object> details = new HashMap<>();
        details.put("feature", "medications");
        details.put("action", action);
        details.put("result", result);
        details.put("durationMs", durationMs);
        details.put("routeKey", "MedicationController." + action);
        if (errorCategory != null) {
            details.put("errorCategory", errorCategory);
        }

        Map<String, Object> deviceInfo = Map.of("uiSurface", "api");

        telemetry.recordAnonymous(
                eventName,
                details,
                deviceInfo,
                null,
                null
        );
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private static String categorizeStatus(int status) {
        if (status == 401 || status == 403) return "auth";
        if (status >= 400 && status < 500) return "client";
        return "server";
    }

    private static String categorize(Exception ex) {
        if (ex instanceof org.springframework.web.server.ResponseStatusException rse) {
            return categorizeStatus(rse.getStatusCode().value());
        }

        if (ex instanceof AppException ae) {
            return categorizeStatus(ae.getStatus().value());
        }

        org.springframework.web.bind.annotation.ResponseStatus rs =
                org.springframework.core.annotation.AnnotationUtils.findAnnotation(
                        ex.getClass(),
                        org.springframework.web.bind.annotation.ResponseStatus.class
                );

        if (rs != null) {
            return categorizeStatus(rs.code().value());
        }

        if (ex instanceof org.springframework.web.bind.MethodArgumentNotValidException) return "validation";
        if (ex instanceof org.springframework.http.converter.HttpMessageNotReadableException) return "client";
        if (ex instanceof org.springframework.web.bind.MissingServletRequestParameterException) return "client";

        String n = ex.getClass().getSimpleName().toLowerCase();
        if (n.contains("access") || n.contains("auth")) return "auth";
        if (n.contains("valid") || n.contains("constraint") || n.contains("illegalargument")) return "validation";
        if (n.contains("timeout")) return "timeout";
        if (n.contains("notfound") || n.contains("not_found")) return "client";
        return "server";
    }
}