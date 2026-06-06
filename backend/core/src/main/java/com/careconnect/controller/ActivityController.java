package com.careconnect.controller;

import com.careconnect.exception.AppException;
import com.careconnect.model.User;
import com.careconnect.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Master list of activities (ADL/IADL). GET /v1/api/activities?category=ADL|IADL.
 */
@RestController
@RequestMapping("/v1/api/activities")
public class ActivityController {

  @Autowired
    private UserRepository userRepository;

  private User getCurrentUser() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    return userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "User not authenticated"));
  }

  /**
     * Get all activities, optionally filtered by category (ADL or IADL).
     * Returns id, name, category, defaultIconUrl.
     */
  @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getActivities(
            @RequestParam(required = false) String category) {
    getCurrentUser();
    // TODO: load from activity table when it exists
    return ResponseEntity.ok(Collections.emptyList());
  }
}
