package com.careconnect.controller;

import com.careconnect.dto.QuestionDTO;
import com.careconnect.model.User;
import com.careconnect.security.Role;
import com.careconnect.service.QuestionService;
import com.careconnect.util.SecurityUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * RBAC tests for CheckInQuestionController.
 *
 * Tests that GET /api/checkins/{id}/questions and /v1/api/checkins/{id}/questions
 * enforce defense-in-depth via securityUtil.resolveCurrentUser().
 * All authenticated roles (ADMIN, CAREGIVER, PATIENT, FAMILY_MEMBER) should be
 * able to read check-in questions since patients need them during check-ins.
 * The resolveCurrentUser() call verifies the user exists in the database.
 *
 * Uses @WebMvcTest to match the existing RBAC test conventions in this project.
 */
@WebMvcTest(CheckInQuestionController.class)
@DisplayName("CheckInQuestionController RBAC Tests")
class CheckInQuestionControllerRbacTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private QuestionService questionService;

    @MockitoBean
    private SecurityUtil securityUtil;

    private User adminUser;
    private User caregiverUser;
    private User patientUser;
    private User familyMemberUser;

    @BeforeEach
    void setUp() {
        adminUser = makeUser(1L, Role.ADMIN);
        caregiverUser = makeUser(2L, Role.CAREGIVER);
        patientUser = makeUser(3L, Role.PATIENT);
        familyMemberUser = makeUser(4L, Role.FAMILY_MEMBER);
    }

    private User makeUser(Long id, Role role) {
        final User u = new User();
        u.setId(id);
        u.setEmail("user" + id + "@test.com");
        u.setRole(role);
        return u;
    }

    // ── GET /api/checkins/{id}/questions - resolveCurrentUser defense-in-depth ──

    @Test
    @WithMockUser(username = "admin@test.com")
    @DisplayName("ADMIN can read check-in questions")
    void admin_canReadQuestions() throws Exception {
        when(securityUtil.resolveCurrentUser()).thenReturn(adminUser);
        when(questionService.findActiveOrdered()).thenReturn(List.of(
                new QuestionDTO(1L, "How are you?", "TEXT", true, true, 1)));

        mockMvc.perform(get("/api/checkins/1/questions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].prompt").value("How are you?"));

        verify(securityUtil).resolveCurrentUser();
    }

    @Test
    @WithMockUser(username = "caregiver@test.com")
    @DisplayName("CAREGIVER can read check-in questions")
    void caregiver_canReadQuestions() throws Exception {
        when(securityUtil.resolveCurrentUser()).thenReturn(caregiverUser);
        when(questionService.findActiveOrdered()).thenReturn(List.of(
                new QuestionDTO(1L, "How are you?", "TEXT", true, true, 1)));

        mockMvc.perform(get("/api/checkins/1/questions"))
                .andExpect(status().isOk());

        verify(securityUtil).resolveCurrentUser();
    }

    @Test
    @WithMockUser(username = "patient@test.com")
    @DisplayName("PATIENT can read check-in questions (needed for check-in flow)")
    void patient_canReadQuestions() throws Exception {
        when(securityUtil.resolveCurrentUser()).thenReturn(patientUser);
        when(questionService.findActiveOrdered()).thenReturn(List.of(
                new QuestionDTO(1L, "How are you?", "TEXT", true, true, 1)));

        mockMvc.perform(get("/api/checkins/1/questions"))
                .andExpect(status().isOk());

        verify(securityUtil).resolveCurrentUser();
    }

    @Test
    @WithMockUser(username = "family@test.com")
    @DisplayName("FAMILY_MEMBER can read check-in questions")
    void familyMember_canReadQuestions() throws Exception {
        when(securityUtil.resolveCurrentUser()).thenReturn(familyMemberUser);
        when(questionService.findActiveOrdered()).thenReturn(List.of(
                new QuestionDTO(1L, "How are you?", "TEXT", true, true, 1)));

        mockMvc.perform(get("/api/checkins/1/questions"))
                .andExpect(status().isOk());

        verify(securityUtil).resolveCurrentUser();
    }

    // ── Versioned path /v1/api/checkins/{id}/questions ──────────────────────────

    @Test
    @WithMockUser(username = "admin@test.com")
    @DisplayName("ADMIN can read questions via versioned path")
    void admin_canReadQuestions_versionedPath() throws Exception {
        when(securityUtil.resolveCurrentUser()).thenReturn(adminUser);
        when(questionService.findActiveOrdered()).thenReturn(List.of(
                new QuestionDTO(1L, "How are you?", "TEXT", true, true, 1)));

        mockMvc.perform(get("/v1/api/checkins/99/questions"))
                .andExpect(status().isOk());

        verify(securityUtil).resolveCurrentUser();
    }

    @Test
    @WithMockUser(username = "patient@test.com")
    @DisplayName("PATIENT can read questions via versioned path")
    void patient_canReadQuestions_versionedPath() throws Exception {
        when(securityUtil.resolveCurrentUser()).thenReturn(patientUser);
        when(questionService.findActiveOrdered()).thenReturn(List.of());

        mockMvc.perform(get("/v1/api/checkins/5/questions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        verify(securityUtil).resolveCurrentUser();
    }

    // ── Defense-in-depth: resolveCurrentUser fails for unknown user ─────────────

    @Test
    @WithMockUser(username = "unknown@test.com")
    @DisplayName("Unknown user in DB triggers RuntimeException from resolveCurrentUser")
    void unknownUser_triggersRuntimeException() throws Exception {
        when(securityUtil.resolveCurrentUser())
                .thenThrow(new RuntimeException("User not found: unknown@test.com"));

        mockMvc.perform(get("/api/checkins/1/questions"))
                .andExpect(status().isInternalServerError());

        verify(securityUtil).resolveCurrentUser();
        verifyNoInteractions(questionService);
    }
}
