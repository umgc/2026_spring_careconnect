package com.careconnect.controller;

import com.careconnect.security.Permission;
import com.careconnect.security.RequirePermission;

import com.careconnect.dto.QuestionDTO;
import com.careconnect.service.QuestionService;
import com.careconnect.util.SecurityUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(path = {"/api/checkins", "/v1/api/checkins"})
public class CheckInQuestionController {

  private final QuestionService questionService;

  @Autowired
    private SecurityUtil securityUtil;

  public CheckInQuestionController(QuestionService questionService) {
    this.questionService = questionService;
  }

  /**
     * GET /api/checkins/{checkInId}/questions
     * GET /v1/api/checkins/{checkInId}/questions
     */
  @RequirePermission(Permission.VIEW_ASSIGNED_PATIENTS)

  @GetMapping("/{checkInId}/questions")
    public ResponseEntity<List<QuestionDTO>> getQuestions(@PathVariable("checkInId") Long checkInId) {
    // RBAC: Defense-in-depth Ã¢â‚¬â€ verify caller is a real user in the database
    if (securityUtil != null) {
      securityUtil.resolveCurrentUser();
    }
    // Temporary: return active, ordered questions until per-check-in mapping is ready
    List<QuestionDTO> questions = questionService.findActiveOrdered();
    return ResponseEntity.ok(questions);
  }
}
