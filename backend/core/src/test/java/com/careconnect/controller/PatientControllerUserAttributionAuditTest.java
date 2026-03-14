package com.careconnect.controller;

import com.careconnect.model.Patient;
import com.careconnect.model.PatientRisk;
import com.careconnect.model.RiskType;
import com.careconnect.model.User;
import com.careconnect.repository.UserRepository;
import com.careconnect.security.Role;
import com.careconnect.service.CaregiverPatientLinkService;
import com.careconnect.service.PatientRiskService;
import com.careconnect.service.PatientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that flaggedBy (user attribution for patient_risks) is set from the
 * authenticated user session and never from the request body.
 */
@WebMvcTest(PatientController.class)
@DisplayName("PatientController user attribution audit (patient_risks)")
class PatientControllerUserAttributionAuditTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PatientService patientService;
    @MockitoBean
    private PatientRiskService patientRiskService;
    @MockitoBean
    private UserRepository userRepository;
    @MockitoBean
    private CaregiverPatientLinkService caregiverPatientLinkService;
    @MockitoBean
    private com.careconnect.service.FamilyMemberService familyMemberService;
    @MockitoBean
    private com.careconnect.service.MoodPainLogService moodPainLogService;
    @MockitoBean
    private com.careconnect.service.MedicationService medicationService;
    private User caregiverUser;
    private Patient patient;

    @BeforeEach
    void setUp() {
        caregiverUser = new User();
        caregiverUser.setId(100L);
        caregiverUser.setEmail("caregiver@test.com");
        caregiverUser.setRole(Role.CAREGIVER);
        patient = new Patient();
        patient.setId(10L);
        User patientUser = new User();
        patientUser.setId(1L);
        patient.setUser(patientUser);
    }

    @Test
    @WithMockUser(username = "caregiver@test.com")
    @DisplayName("flagPatientRisk: flaggedBy is taken from session, not from request body")
    void flaggedByFromSession() throws Exception {
        when(userRepository.findByEmail("caregiver@test.com")).thenReturn(Optional.of(caregiverUser));
        when(patientService.getPatientById(10L)).thenReturn(patient);
        when(caregiverPatientLinkService.hasAccessToPatient(100L, 1L)).thenReturn(true);

        RiskType riskType = new RiskType();
        riskType.setId(1L);
        riskType.setName("Fall with Injury");
        PatientRisk risk = new PatientRisk();
        risk.setId(1L);
        risk.setRiskType(riskType);
        risk.setFlaggedBy(caregiverUser);
        risk.setFlaggedAt(java.time.Instant.now());
        when(patientRiskService.flagRisk(eq(10L), eq(1L), eq(100L)))
                .thenReturn(risk);

        // Body only has riskTypeId (no flaggedBy field in API)
        String body = "{\"riskTypeId\": 1}";

        mockMvc.perform(post("/v1/api/patients/10/risks")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        // Verify service was called with current user id (100L) as third argument (flaggedBy)
        verify(patientRiskService).flagRisk(eq(10L), eq(1L), eq(100L));
    }
}
