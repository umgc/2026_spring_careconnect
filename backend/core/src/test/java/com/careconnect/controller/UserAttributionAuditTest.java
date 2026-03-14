package com.careconnect.controller;

import com.careconnect.dto.ActivityLogDtos;
import com.careconnect.model.ActivityLog;
import com.careconnect.model.Patient;
import com.careconnect.model.User;
import com.careconnect.repository.ActivityLogRepository;
import com.careconnect.security.Role;
import com.careconnect.service.CaregiverPatientLinkService;
import com.careconnect.service.FamilyMemberService;
import com.careconnect.service.PatientService;
import com.careconnect.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that createdBy (and equivalent audit fields) are always set from the authenticated
 * user session and never from the HTTP request body.
 */
@WebMvcTest(ActivityLogController.class)
@DisplayName("User attribution audit – createdBy from session only")
class UserAttributionAuditTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ActivityLogRepository activityLogRepository;
    @MockitoBean
    private PatientService patientService;
    @MockitoBean
    private UserRepository userRepository;
    @MockitoBean
    private CaregiverPatientLinkService caregiverPatientLinkService;
    @MockitoBean
    private FamilyMemberService familyMemberService;

    private ObjectMapper objectMapper;
    private User caregiverUser;
    private Patient patient;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        caregiverUser = new User();
        caregiverUser.setId(42L);
        caregiverUser.setEmail("caregiver@test.com");
        caregiverUser.setRole(Role.CAREGIVER);
        patient = new Patient();
        patient.setId(10L);
        User patientUser = new User();
        patientUser.setId(1L);
        patient.setUser(patientUser);
    }

    @Nested
    @DisplayName("POST /v1/api/activity-logs")
    class ActivityLogPost {

        @Test
        @WithMockUser(username = "caregiver@test.com")
        @DisplayName("caregiverUserId is set from session, not from request body")
        void createdByFromSession_notFromBody() throws Exception {
            when(userRepository.findByEmail("caregiver@test.com")).thenReturn(Optional.of(caregiverUser));
            when(patientService.getPatientById(10L)).thenReturn(patient);
            when(caregiverPatientLinkService.hasAccessToPatient(42L, 1L)).thenReturn(true);

            // Body attempts to set caregiverId (and if we had createdBy in DTO, it would be ignored)
            ActivityLogDtos.CreateActivityLogRequest body = new ActivityLogDtos.CreateActivityLogRequest();
            body.setClientId(10L);
            body.setActivityId(1L);
            body.setCompetencyScore(3);
            body.setCaregiverId(999L); // attempt to spoof – must be ignored

            when(activityLogRepository.save(any(ActivityLog.class))).thenAnswer(inv -> inv.getArgument(0));

            mockMvc.perform(post("/v1/api/activity-logs")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isCreated());

            var captor = org.mockito.ArgumentCaptor.forClass(ActivityLog.class);
            verify(activityLogRepository).save(captor.capture());
            ActivityLog saved = captor.getValue();
            assertThat(saved.getCaregiverUserId()).isEqualTo(42L);
            assertThat(saved.getCaregiverUserId()).isNotEqualTo(999L);
        }
    }
}
