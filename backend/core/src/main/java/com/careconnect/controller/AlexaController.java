package com.careconnect.controller;

import com.careconnect.security.Permission;
import com.careconnect.security.RequirePermission;

import com.careconnect.security.AuthorizationService;
import com.careconnect.security.JwtTokenProvider;
import com.careconnect.security.UnauthorizedException;
import com.careconnect.model.Patient;
import com.careconnect.model.User;
import com.careconnect.util.SecurityUtil;
import com.careconnect.repository.UserRepository;
import com.careconnect.repository.PatientRepository;
import com.careconnect.service.v2.TaskServiceV2;
import com.careconnect.dto.v2.TaskDtoV2;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/v1/api/alexa")
public class AlexaController {

  @Autowired
    private SecurityUtil securityUtil;

  @Autowired
    private AuthorizationService authorizationService;

  @Autowired
    private JwtTokenProvider jwtTokenProvider;

  @Autowired
    private UserRepository userRepository;

  @Autowired
    private PatientRepository patientRepository;

  // ÃƒÆ’Ã‚Â°Ãƒâ€¦Ã‚Â¸ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¢ Inject TaskServiceV2 instead of using RestTemplate
  @Autowired
    private TaskServiceV2 taskService;

  private final ObjectMapper mapper = new ObjectMapper();

  // ==============================================================
  // ÃƒÆ’Ã‚Â°Ãƒâ€¦Ã‚Â¸ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒâ€šÃ‚Â§ Helper: Unlink Alexa account for a patient
  // ==============================================================
  private void unlinkAlexaAccount(Long patientId, String reason) {
    try {
      Optional<Patient> patientOpt = patientRepository.findById(patientId);
      if (patientOpt.isPresent()) {
        Patient patient = patientOpt.get();
        patient.setAlexaLinked(false);
        patientRepository.save(patient);
        System.out.println("ÃƒÆ’Ã‚Â°Ãƒâ€¦Ã‚Â¸ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â€šÂ¬Ã…â€œ Unlinked Alexa for patient ID " + patientId + ". Reason: " + reason);
      }
    } catch (Exception e) {
      System.err.println("ÃƒÆ’Ã‚Â¢Ãƒâ€¦Ã‚Â¡Ãƒâ€šÃ‚Â ÃƒÆ’Ã‚Â¯Ãƒâ€šÃ‚Â¸Ãƒâ€šÃ‚Â Failed to unlink Alexa account: " + e.getMessage());
    }
  }

  // ==============================================================
  // ÃƒÆ’Ã‚Â°Ãƒâ€¦Ã‚Â¸ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒâ€šÃ‚Â§ Helper: Extract token from Alexa request payload
  // ==============================================================
  private String extractAlexaToken(String requestBody) {
    try {
      JsonNode root = mapper.readTree(requestBody);

      // Custom skill
      if (root.has("session") && root.path("session").path("user").has("accessToken")) {
        return root.path("session").path("user").path("accessToken").asText();
      }

      // Smart Home
      if (root.has("directive")) {
        return root.path("directive").path("endpoint")
                        .path("scope").path("token").asText(null);
      }

      return null;
    } catch (Exception e) {
      System.err.println("ÃƒÆ’Ã‚Â¢Ãƒâ€¦Ã‚Â¡Ãƒâ€šÃ‚Â ÃƒÆ’Ã‚Â¯Ãƒâ€šÃ‚Â¸Ãƒâ€šÃ‚Â Failed to extract Alexa token: " + e.getMessage());
      return null;
    }
  }

  // ==============================================================
  // ÃƒÆ’Ã‚Â°Ãƒâ€¦Ã‚Â¸Ãƒâ€šÃ‚Â§Ãƒâ€šÃ‚Â  Helper: Resolve patient ID from JWT ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â ÃƒÂ¢Ã¢â€šÂ¬Ã¢â€žÂ¢ DB lookup
  // ==============================================================
  private Long resolvePatientIdFromToken(String token) {
    try {
      String email = jwtTokenProvider.getEmailFromToken(token);
      if (email == null) {
        System.err.println("ÃƒÆ’Ã‚Â¢Ãƒâ€¦Ã‚Â¡Ãƒâ€šÃ‚Â ÃƒÆ’Ã‚Â¯Ãƒâ€šÃ‚Â¸Ãƒâ€šÃ‚Â Token missing email claim");
        return null;
      }

      Optional<User> userOpt = userRepository.findByEmail(email);
      if (userOpt.isEmpty()) {
        System.err.println("ÃƒÆ’Ã‚Â¢Ãƒâ€¦Ã‚Â¡Ãƒâ€šÃ‚Â ÃƒÆ’Ã‚Â¯Ãƒâ€šÃ‚Â¸Ãƒâ€šÃ‚Â No user found for email: " + email);
        return null;
      }

      User user = userOpt.get();
      System.out.println(
                    "ÃƒÆ’Ã‚Â°Ãƒâ€¦Ã‚Â¸ÃƒÂ¢Ã¢â€šÂ¬Ã‹Å“Ãƒâ€šÃ‚Â¤ Found user: " + user.getEmail() + " (id=" + user.getId() + ", role=" + user.getRole() + ")");

      // ÃƒÆ’Ã‚Â°Ãƒâ€¦Ã‚Â¸Ãƒâ€šÃ‚Â§Ãƒâ€šÃ‚Â© CASE 1 ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â Direct Patient Login
      if (user.getRole() == com.careconnect.security.Role.PATIENT) {
        Optional<Patient> patientOpt = patientRepository.findByUser(user);
        if (patientOpt.isPresent()) {
          Long patientId = patientOpt.get().getId();
          System.out.println("ÃƒÆ’Ã‚Â°Ãƒâ€¦Ã‚Â¸Ãƒâ€šÃ‚Â©Ãƒâ€šÃ‚Âº Found patient ID " + patientId + " linked to user " + user.getEmail());
          return patientId;
        } else {
          System.err.println("ÃƒÆ’Ã‚Â¢Ãƒâ€¦Ã‚Â¡Ãƒâ€šÃ‚Â ÃƒÆ’Ã‚Â¯Ãƒâ€šÃ‚Â¸Ãƒâ€šÃ‚Â No Patient record linked to this user (role=PATIENT).");
          return null;
        }
      }

      // ÃƒÆ’Ã‚Â°Ãƒâ€¦Ã‚Â¸Ãƒâ€šÃ‚Â§Ãƒâ€šÃ‚Â© CASE 2 ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â Caregiver Login (linked via CaregiverPatientLink)
      else if (user.getRole() == com.careconnect.security.Role.CAREGIVER) {
        System.out.println("ÃƒÆ’Ã‚Â°Ãƒâ€¦Ã‚Â¸Ãƒâ€šÃ‚Â§ÃƒÂ¢Ã¢â€šÂ¬Ã‹Å“ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚ÂÃƒÆ’Ã‚Â¢Ãƒâ€¦Ã‚Â¡ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¢ÃƒÆ’Ã‚Â¯Ãƒâ€šÃ‚Â¸Ãƒâ€šÃ‚Â User is a caregiver ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â attempting to find linked patient(s)");

        List<Patient> allPatients = patientRepository.findAll();
        for (Patient p : allPatients) {
          boolean hasAccess = patientRepository.hasAccessByCaregiverId(p.getId(), user.getId());
          if (hasAccess) {
            System.out.println("ÃƒÆ’Ã‚Â¢Ãƒâ€¦Ã¢â‚¬Å“ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¦ Caregiver " + user.getEmail() + " has access to patient ID " + p.getId());
            return p.getId();
          }
        }

        System.err.println("ÃƒÆ’Ã‚Â¢Ãƒâ€¦Ã‚Â¡Ãƒâ€šÃ‚Â ÃƒÆ’Ã‚Â¯Ãƒâ€šÃ‚Â¸Ãƒâ€šÃ‚Â Caregiver " + user.getEmail() + " has no active linked patients.");
        return null;
      }

      // ÃƒÆ’Ã‚Â°Ãƒâ€¦Ã‚Â¸Ãƒâ€šÃ‚Â§Ãƒâ€šÃ‚Â© CASE 3 ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â Unsupported Role
      else {
        System.err.println("ÃƒÆ’Ã‚Â¢Ãƒâ€¦Ã‚Â¡Ãƒâ€šÃ‚Â ÃƒÆ’Ã‚Â¯Ãƒâ€šÃ‚Â¸Ãƒâ€šÃ‚Â Unsupported role type: " + user.getRole());
        return null;
      }

    } catch (Exception e) {
      System.err.println("ÃƒÆ’Ã‚Â°Ãƒâ€¦Ã‚Â¸ÃƒÂ¢Ã¢â€šÂ¬Ã¢â€žÂ¢Ãƒâ€šÃ‚Â¥ Failed to resolve patient ID: " + e.getMessage());
      e.printStackTrace();
      return null;
    }
  }

  // ==============================================================
  // ÃƒÆ’Ã‚Â°Ãƒâ€¦Ã‚Â¸ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¢ Helper: Parse and normalize task data from Alexa
  // ==============================================================
  private TaskDtoV2 normalizeAlexaTaskData(Map<String, Object> alexaBody, Long patientId) {
    TaskDtoV2 taskDto = new TaskDtoV2();

    // Core fields
    taskDto.setCompleted(false);

    // Task name (title)
    if (alexaBody.containsKey("name")) {
      taskDto.setName((String) alexaBody.get("name"));
    } else if (alexaBody.containsKey("title")) {
      taskDto.setName((String) alexaBody.get("title"));
    }

    // Task description
    if (alexaBody.containsKey("description")) {
      taskDto.setDescription((String) alexaBody.get("description"));
    }

    // Date
    if (alexaBody.containsKey("date")) {
      String dateStr = (String) alexaBody.get("date");
      try {
        // Validate ISO date format (YYYY-MM-DD)
        LocalDate.parse(dateStr);
        taskDto.setDate(dateStr + "T00:00:00");
      } catch (DateTimeParseException ex) {
        System.err.println("ÃƒÆ’Ã‚Â¢Ãƒâ€¦Ã‚Â¡Ãƒâ€šÃ‚Â ÃƒÆ’Ã‚Â¯Ãƒâ€šÃ‚Â¸Ãƒâ€šÃ‚Â Invalid date format from Alexa: " + dateStr);
        taskDto.setDate(LocalDate.now().toString() + "T00:00:00");
      }
    } else {
      taskDto.setDate(LocalDate.now().toString() + "T00:00:00");
    }

    // Time of day
    if (alexaBody.containsKey("timeOfDay")) {
      String timeStr = (String) alexaBody.get("timeOfDay");
      try {
        LocalTime.parse(timeStr);
        taskDto.setTimeOfDay(timeStr);
      } catch (DateTimeParseException ex) {
        System.err.println("ÃƒÆ’Ã‚Â¢Ãƒâ€¦Ã‚Â¡Ãƒâ€šÃ‚Â ÃƒÆ’Ã‚Â¯Ãƒâ€šÃ‚Â¸Ãƒâ€šÃ‚Â Invalid time format from Alexa: " + timeStr);
      }
    }

    // Task type
    if (alexaBody.containsKey("taskType")) {
      taskDto.setTaskType((String) alexaBody.get("taskType"));
    }

    // Frequency (recurrence)
    if (alexaBody.containsKey("frequency")) {
      taskDto.setFrequency((String) alexaBody.get("frequency"));
    }

    // Interval
    if (alexaBody.containsKey("interval")) {
      Object intervalObj = alexaBody.get("interval");
      if (intervalObj instanceof Integer) {
        taskDto.setInterval((Integer) intervalObj);
      } else if (intervalObj instanceof String) {
        try {
          taskDto.setInterval(Integer.parseInt((String) intervalObj));
        } catch (NumberFormatException ex) {
          taskDto.setInterval(1);
        }
      }
    }

    // Count (number of occurrences)
    if (alexaBody.containsKey("count")) {
      Object countObj = alexaBody.get("count");
      if (countObj instanceof Integer) {
        taskDto.setCount((Integer) countObj);
      } else if (countObj instanceof String) {
        try {
          taskDto.setCount(Integer.parseInt((String) countObj));
        } catch (NumberFormatException ex) {
          taskDto.setCount(1);
        }
      }
    }

    // Days of week
    if (alexaBody.containsKey("daysOfWeek")) {
      Object daysObj = alexaBody.get("daysOfWeek");
      if (daysObj instanceof List) {
        @SuppressWarnings("unchecked")
                List<?> daysList = (List<?>) daysObj;
        List<Boolean> booleanDays = daysList.stream()
                        .map(day -> {
                          if (day instanceof Boolean) {
                            return (Boolean) day;
                          } else if (day instanceof String) {
                            return Boolean.parseBoolean((String) day);
                          } else if (day instanceof Integer) {
                            return ((Integer) day) != 0;
                          }
                          return false;
                        })
                        .collect(Collectors.toList());
        taskDto.setDaysOfWeek(booleanDays);
      }
    }

    return taskDto;
  }

  // ==============================================================
  // 1ÃƒÆ’Ã‚Â¯Ãƒâ€šÃ‚Â¸Ãƒâ€šÃ‚ÂÃƒÆ’Ã‚Â¢Ãƒâ€ Ã¢â‚¬â„¢Ãƒâ€šÃ‚Â£ Get Tasks (authenticated) - NOW USES TASKSERVICEV2
  // ==============================================================
  @RequirePermission(Permission.VIEW_ASSIGNED_PATIENTS)

  @GetMapping("/calendarTasks/get")
    public ResponseEntity<?> getCalendarTasks(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(value = "filter", defaultValue = "week") String filter) throws UnauthorizedException {
    User currentUser = securityUtil.resolveCurrentUser();
    if (currentUser.isFamilyMember()) {
      throw new UnauthorizedException("Family members cannot access Alexa features");
    }
    Long patientId = null;
    try {
      System.out.println("ÃƒÆ’Ã‚Â°Ãƒâ€¦Ã‚Â¸ÃƒÂ¢Ã¢â€šÂ¬Ã…â€œÃƒâ€šÃ‚Â¥ Received Alexa task retrieval request");

      String token = null;
      if (authHeader != null && authHeader.startsWith("Bearer ")) {
        token = authHeader.substring(7);
      }

      // ÃƒÆ’Ã‚Â°Ãƒâ€¦Ã‚Â¸Ãƒâ€¦Ã‚Â¡Ãƒâ€šÃ‚Â¨ UNLINK CASE 1: Invalid or expired token
      if (token == null || !jwtTokenProvider.validateToken(token)) {
        System.err.println("ÃƒÆ’Ã‚Â°Ãƒâ€¦Ã‚Â¸ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â€šÂ¬Ã…â€œ Token validation failed for task retrieval");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Missing or invalid access token"));
      }

      patientId = resolvePatientIdFromToken(token);

      // ÃƒÆ’Ã‚Â°Ãƒâ€¦Ã‚Â¸Ãƒâ€¦Ã‚Â¡Ãƒâ€šÃ‚Â¨ UNLINK CASE 2: Unable to resolve patient
      if (patientId == null) {
        System.err.println("ÃƒÆ’Ã‚Â°Ãƒâ€¦Ã‚Â¸ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â€šÂ¬Ã…â€œ Unable to resolve patient ID for task retrieval");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Unable to resolve patient ID"));
      }

      // ÃƒÆ’Ã‚Â°Ãƒâ€¦Ã‚Â¸ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¢ Use TaskServiceV2 to get tasks instead of RestTemplate
      List<TaskDtoV2> allTasks = taskService.getTasksByPatient(patientId);
      System.out.println("ÃƒÆ’Ã‚Â°Ãƒâ€¦Ã‚Â¸ÃƒÂ¢Ã¢â€šÂ¬Ã…â€œÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¹ Retrieved " + allTasks.size() + " tasks for patient " + patientId);

      // Filter by week if requested
      if ("week".equalsIgnoreCase(filter)) {
        System.out.println("ÃƒÆ’Ã‚Â°Ãƒâ€¦Ã‚Â¸ÃƒÂ¢Ã¢â€šÂ¬Ã…â€œÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¦ Filtering tasks by week...");

        java.time.LocalDate today = java.time.LocalDate.now();

        // Rolling 7-day window starting today
        java.time.LocalDate startOfWeek = today;
        java.time.LocalDate endOfWeek = today.plusDays(6);

        System.out.println("ÃƒÆ’Ã‚Â°Ãƒâ€¦Ã‚Â¸ÃƒÂ¢Ã¢â€šÂ¬Ã…â€œÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¦ Week range: " + startOfWeek + " to " + endOfWeek);

        // Filter tasks within this week
        List<TaskDtoV2> weekTasks = allTasks.stream()
                        .filter(task -> {
                          try {
                            String dateStr = task.getDate();
                            if (dateStr != null) {
                              java.time.LocalDate taskDate = java.time.LocalDate.parse(
                                            dateStr.substring(0, 10));
                              boolean inWeek = !taskDate.isBefore(startOfWeek) && !taskDate.isAfter(endOfWeek);
                              return inWeek;
                            }
                            return false;
                          } catch (Exception e) {
                            System.err.println("ÃƒÆ’Ã‚Â¢Ãƒâ€¦Ã‚Â¡Ãƒâ€šÃ‚Â ÃƒÆ’Ã‚Â¯Ãƒâ€šÃ‚Â¸Ãƒâ€šÃ‚Â Error parsing task date: " + e.getMessage());
                            return false;
                          }
                        })
                        .collect(Collectors.toList());

        System.out.println("ÃƒÆ’Ã‚Â°Ãƒâ€¦Ã‚Â¸ÃƒÂ¢Ã¢â€šÂ¬Ã…â€œÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¹ Filtered to " + weekTasks.size() + " tasks for this week (out of "
                        + allTasks.size() + " total)");

        return ResponseEntity.ok(weekTasks);
      }

      return ResponseEntity.ok(allTasks);

    } catch (Exception e) {
      System.err.println("ÃƒÆ’Ã‚Â¢Ãƒâ€¦Ã‚Â¡Ãƒâ€šÃ‚Â ÃƒÆ’Ã‚Â¯Ãƒâ€šÃ‚Â¸Ãƒâ€šÃ‚Â Error retrieving tasks: " + e.getMessage());
      e.printStackTrace();
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error fetching tasks", "details", e.getMessage()));
    }
  }

  // ==============================================================
  // 2ÃƒÆ’Ã‚Â¯Ãƒâ€šÃ‚Â¸Ãƒâ€šÃ‚ÂÃƒÆ’Ã‚Â¢Ãƒâ€ Ã¢â‚¬â„¢Ãƒâ€šÃ‚Â£ Add New CareConnect Task (authenticated) - NOW USES TASKSERVICEV2
  // ==============================================================
  @RequirePermission(Permission.CREATE_TASKS)

  @PostMapping("/calendarTasks/add")
    public ResponseEntity<?> addCalendarTask(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, Object> alexaBody) throws UnauthorizedException {
    User currentUser = securityUtil.resolveCurrentUser();
    if (currentUser.isFamilyMember()) {
      throw new UnauthorizedException("Family members cannot access Alexa features");
    }
    Long patientId = null;
    try {
      System.out.println("ÃƒÆ’Ã‚Â°Ãƒâ€¦Ã‚Â¸ÃƒÂ¢Ã¢â€šÂ¬Ã…â€œÃƒâ€šÃ‚Â¥ Received Alexa task creation request: " + alexaBody);

      String token = null;

      // 1ÃƒÆ’Ã‚Â¯Ãƒâ€šÃ‚Â¸Ãƒâ€šÃ‚ÂÃƒÆ’Ã‚Â¢Ãƒâ€ Ã¢â‚¬â„¢Ãƒâ€šÃ‚Â£ Prefer Authorization header
      if (authHeader != null && authHeader.startsWith("Bearer ")) {
        token = authHeader.substring(7);
      }
      // 2ÃƒÆ’Ã‚Â¯Ãƒâ€šÃ‚Â¸Ãƒâ€šÃ‚ÂÃƒÆ’Ã‚Â¢Ãƒâ€ Ã¢â‚¬â„¢Ãƒâ€šÃ‚Â£ Fallback to JSON field if present
      else if (alexaBody.containsKey("accessToken")) {
        token = (String) alexaBody.get("accessToken");
      }

      // ÃƒÆ’Ã‚Â°Ãƒâ€¦Ã‚Â¸Ãƒâ€¦Ã‚Â¡Ãƒâ€šÃ‚Â¨ UNLINK CASE 1: Invalid or expired token
      if (token == null || !jwtTokenProvider.validateToken(token)) {
        System.err.println("ÃƒÆ’Ã‚Â°Ãƒâ€¦Ã‚Â¸ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â€šÂ¬Ã…â€œ Token validation failed for task creation");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Missing or invalid access token"));
      }

      patientId = resolvePatientIdFromToken(token);

      // ÃƒÆ’Ã‚Â°Ãƒâ€¦Ã‚Â¸Ãƒâ€¦Ã‚Â¡Ãƒâ€šÃ‚Â¨ UNLINK CASE 2: Unable to resolve patient
      if (patientId == null) {
        System.err.println("ÃƒÆ’Ã‚Â°Ãƒâ€¦Ã‚Â¸ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â€šÂ¬Ã…â€œ Unable to resolve patient ID for task creation");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Unable to resolve patient ID"));
      }

      // ÃƒÆ’Ã‚Â°Ãƒâ€¦Ã‚Â¸ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¢ Convert Alexa data to TaskDtoV2
      TaskDtoV2 taskDto = normalizeAlexaTaskData(alexaBody, patientId);

      // Validate required fields
      if (taskDto.getName() == null || taskDto.getName().isBlank()) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Task name is required"));
      }

      if (taskDto.getDate() == null || taskDto.getDate().isBlank()) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Task date is required"));
      }

      System.out.println("ÃƒÆ’Ã‚Â°Ãƒâ€¦Ã‚Â¸ÃƒÂ¢Ã¢â€šÂ¬Ã…â€œÃƒâ€šÃ‚Â¤ Creating task via TaskServiceV2: " + taskDto);

      // ÃƒÆ’Ã‚Â°Ãƒâ€¦Ã‚Â¸ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¢ Use TaskServiceV2 to create task instead of RestTemplate
      try {
        TaskDtoV2 createdTask = taskService.createTask(patientId, taskDto);
        System.out.println("ÃƒÆ’Ã‚Â¢Ãƒâ€¦Ã¢â‚¬Å“ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¦ Task created successfully: " + createdTask);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdTask);
      } catch (Exception e) {
        System.err.println("ÃƒÆ’Ã‚Â¢Ãƒâ€šÃ‚ÂÃƒâ€¦Ã¢â‚¬â„¢ Error creating task via service: " + e.getMessage());

        // ÃƒÆ’Ã‚Â°Ãƒâ€¦Ã‚Â¸Ãƒâ€¦Ã‚Â¡Ãƒâ€šÃ‚Â¨ UNLINK CASE 3: Check if it's an authorization issue
        if (e.getMessage() != null &&
                        (e.getMessage().contains("Unauthorized") ||
                                e.getMessage().contains("Forbidden"))) {
          unlinkAlexaAccount(patientId, "Backend rejected task creation: " + e.getMessage());
          return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(Map.of("error", "Access denied to patient data"));
        }

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", "Error adding task", "details", e.getMessage()));
      }

    } catch (Exception e) {
      System.err.println("ÃƒÆ’Ã‚Â¢Ãƒâ€šÃ‚ÂÃƒâ€¦Ã¢â‚¬â„¢ Exception during task creation: " + e.getMessage());
      e.printStackTrace();
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error adding task", "details", e.getMessage()));
    }
  }
}
