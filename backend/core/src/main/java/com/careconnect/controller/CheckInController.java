package com.careconnect.controller;

import com.careconnect.security.Permission;
import com.careconnect.security.RequirePermission;

import com.careconnect.model.CheckIn;
import com.careconnect.model.User;
import com.careconnect.security.AuthorizationService;
import com.careconnect.security.UnauthorizedException;
import com.careconnect.service.CheckInService;
import com.careconnect.util.SecurityUtil;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/checkins")
@Tag(name = "Check-In", description = "Endpoint for the virtual Check-In, including both patient submitting and caregiver checking")
public class CheckInController {

  @Autowired
    private SecurityUtil securityUtil;

  @Autowired
    private AuthorizationService authorizationService;

  @Autowired
    private CheckInService checkInService;

  @RequirePermission(Permission.CREATE_TASKS)


  @PostMapping()
    public ResponseEntity<CheckIn> patientCheckIn() throws UnauthorizedException {
    User currentUser = securityUtil.resolveCurrentUser();

    // TODO: Replace with actual patient check-in logic later
    return ResponseEntity.ok(new CheckIn());
  }

  @RequirePermission(Permission.VIEW_ASSIGNED_PATIENTS)


  @GetMapping()
    public ResponseEntity<List<CheckIn>> getCheckIns() throws UnauthorizedException {
    User currentUser = securityUtil.resolveCurrentUser();
    authorizationService.requireAdminOrCaregiver(currentUser);

    // Fetch all check-ins (placeholder)
    return ResponseEntity.ok(checkInService.getAllCheckIns());
  }

  @RequirePermission(Permission.VIEW_ASSIGNED_PATIENTS)


  @GetMapping("/{id}")
    public ResponseEntity<CheckIn> getCheckIn(@PathVariable Long id) throws UnauthorizedException {
    // RBAC: Only admins and caregivers can view individual check-ins
    User currentUser = securityUtil.resolveCurrentUser();
    authorizationService.requireAdminOrCaregiver(currentUser);

    // Retrieve a specific check-in by ID
    CheckIn target = checkInService.getCheckInByID(id);
    return ResponseEntity.ok(target);
  }

  @RequirePermission(Permission.UPDATE_TASKS)


  @PutMapping("/{id}")
    public ResponseEntity<CheckIn> updateCheckIn(@PathVariable Long id) throws UnauthorizedException {
    User currentUser = securityUtil.resolveCurrentUser();
    authorizationService.requireAdminOrCaregiver(currentUser);

    // TODO: Implement update logic later
    return ResponseEntity.ok(new CheckIn());
  }
}
