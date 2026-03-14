package com.careconnect.controller;

import com.careconnect.security.Permission;
import com.careconnect.security.RequirePermission;

import com.careconnect.model.*;
import com.careconnect.security.AuthorizationService;
import com.careconnect.security.UnauthorizedException;
import com.careconnect.service.GamificationService;
import com.careconnect.util.SecurityUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("v1/api/gamification")
public class GamificationController {

    private final GamificationService gamificationService;
    private final SecurityUtil securityUtil;
    private final AuthorizationService authorizationService;

    @Autowired
    public GamificationController(GamificationService gamificationService,
                                  SecurityUtil securityUtil,
                                  AuthorizationService authorizationService) {
        this.gamificationService = gamificationService;
        this.securityUtil = securityUtil;
        this.authorizationService = authorizationService;
    }

    // 1. Award XP to user
    @RequirePermission(Permission.CREATE_TASKS)

    @PostMapping("/award-xp")
    public ResponseEntity<?> awardXp(@RequestBody Map<String, Object> body) throws UnauthorizedException {
        User currentUser = securityUtil.resolveCurrentUser();
        authorizationService.requireAdmin(currentUser);

        Long userId = Long.valueOf(body.get("userId").toString());
        int amount = Integer.parseInt(body.get("amount").toString());

        XPProgress updatedProgress = gamificationService.awardXp(userId, amount);
        return ResponseEntity.ok(updatedProgress);
    }

    @RequirePermission(Permission.VIEW_ASSIGNED_PATIENTS)


    @GetMapping("/progress/{userId}")
    public ResponseEntity<?> getXpProgress(@PathVariable Long userId) throws UnauthorizedException {
        User currentUser = securityUtil.resolveCurrentUser();
        authorizationService.requireSelfOrAdmin(currentUser, userId);

        return gamificationService.getXpProgress(userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(404).body(null));
    }

    // 3. Get earned achievements for a user
    @RequirePermission(Permission.VIEW_ASSIGNED_PATIENTS)

    @GetMapping("/achievements/{userId}")
    public ResponseEntity<List<UserAchievement>> getUserAchievements(@PathVariable Long userId) throws UnauthorizedException {
        User currentUser = securityUtil.resolveCurrentUser();
        authorizationService.requireSelfOrAdmin(currentUser, userId);

        return ResponseEntity.ok(gamificationService.getUserAchievements(userId));
    }

    // 4. Get full list of all achievements (earned + unearned)
    @RequirePermission(Permission.VIEW_ASSIGNED_PATIENTS)

    @GetMapping("/all-achievements")
    public ResponseEntity<List<Achievement>> getAllAchievements() {
        return ResponseEntity.ok(gamificationService.getAllAchievements());
    }
}
