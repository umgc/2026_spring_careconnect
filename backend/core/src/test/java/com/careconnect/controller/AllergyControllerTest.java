package com.careconnect.controller;

import com.careconnect.dto.AllergyDTO;
import com.careconnect.model.Allergy.AllergyType;
import com.careconnect.model.Allergy.AllergySeverity;
import com.careconnect.model.Patient;
import com.careconnect.model.User;
import com.careconnect.repository.PatientRepository;
import com.careconnect.repository.UserRepository;
import com.careconnect.security.AuthorizationService;
import com.careconnect.security.Role;
import com.careconnect.service.AllergyService;
import com.careconnect.service.CaregiverService;
import com.careconnect.util.SecurityUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for {@link AllergyController}, covering the HTTP layer of all
 * allergy-management endpoints exposed under {@code /v1/api/allergies}.
 *
 * <p><b>Why @WebMvcTest + MockMvc?</b><br>
 * {@code @WebMvcTest} spins up only the Spring MVC slice (controllers, filters,
 * argument resolvers) without loading a full application context or a real
 * database.  This makes the tests fast and focused: they verify that the
 * controller routes requests correctly, enforces access-control rules, applies
 * the right HTTP status codes, and serialises/deserialises JSON properly —
 * without caring about the actual business logic inside the services.
 *
 * <p>All service and repository collaborators are replaced with Mockito mocks
 * via {@code @MockBean}.  Security filters are disabled with
 * {@code @AutoConfigureMockMvc(addFilters = false)} so that the
 * {@link SecurityContextHolder} can be configured directly per test, allowing
 * precise control over which user identity is active without running the full
 * Spring Security filter chain.
 */
@WebMvcTest(AllergyController.class)
@AutoConfigureMockMvc(addFilters = false)
class AllergyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // --- Mocked collaborators ---
    // Each bean below is replaced with a Mockito stub so the controller can be
    // instantiated without real allergy storage, user lookup, or caregiver logic.

    @MockitoBean
    private AllergyService allergyService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private PatientRepository patientRepository;

    @MockitoBean
    private CaregiverService caregiverService;

    @MockitoBean
    private SecurityUtil securityUtil;

    @MockitoBean
    private AuthorizationService authorizationService;

    @Autowired
    private ObjectMapper objectMapper;

    // --- Test fixtures ---
    // Pre-built objects reused across tests to avoid repetitive construction.

    private AllergyDTO sampleAllergy;
    private User adminUser;
    private User patientUser;
    private Patient patient;

    @BeforeEach
    void setup() throws Exception {
        sampleAllergy = AllergyDTO.builder()
                .id(1L)
                .patientId(10L)
                .allergen("Penicillin")
                .allergyType(AllergyType.MEDICATION)
                .severity(AllergySeverity.SEVERE)
                .reaction("Anaphylaxis")
                .notes("Avoid all penicillin-based antibiotics")
                .diagnosedDate("2023-01-15")
                .isActive(true)
                .build();

        adminUser = new User();
        adminUser.setId(1L);
        adminUser.setEmail("admin@test.com");
        adminUser.setRole(Role.ADMIN);

        patientUser = new User();
        patientUser.setId(10L);
        patientUser.setEmail("patient@test.com");
        patientUser.setRole(Role.PATIENT);

        patient = new Patient();
        patient.setId(10L);
        patient.setUser(patientUser);
    }

    @AfterEach
    void tearDown() throws Exception {
        SecurityContextHolder.clearContext();
    }

    // -----------------------------------------------------------------------
    // Security context helpers
    // -----------------------------------------------------------------------

    /**
     * Configures the {@link SecurityContextHolder} so that the current
     * principal is the admin user.  An admin always passes the
     * {@code hasAccessToPatient()} check inside the controller.
     */
    private void mockAdminSecurityContext() throws Exception {
        mockSecurityContext("admin@test.com", adminUser);
        when(patientRepository.findById(10L)).thenReturn(Optional.of(patient));
    }

    /**
     * Configures the {@link SecurityContextHolder} so that the current
     * principal is the patient user whose ID matches the patient's linked
     * user — self-access is allowed.
     */
    private void mockPatientSelfAccessContext() throws Exception {
        mockSecurityContext("patient@test.com", patientUser);
        when(patientRepository.findById(10L)).thenReturn(Optional.of(patient));
    }

    /**
     * Configures the {@link SecurityContextHolder} so that the current
     * principal is a different patient user whose ID does NOT match the
     * patient — the controller should deny access with a 403.
     */
    private void mockForbiddenSecurityContext() throws Exception {
        final User other = new User();
        other.setId(99L);
        other.setEmail("other@test.com");
        other.setRole(Role.PATIENT);

        mockSecurityContext("other@test.com", other);
        when(patientRepository.findById(10L)).thenReturn(Optional.of(patient));
    }

    private void mockSecurityContext(String email, User user) {
        final Authentication auth = Mockito.mock(Authentication.class);
        when(auth.getName()).thenReturn(email);
        final SecurityContext secCtx = Mockito.mock(SecurityContext.class);
        when(secCtx.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(secCtx);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
    }

    // -----------------------------------------------------------------------
    // POST /v1/api/allergies
    // -----------------------------------------------------------------------

    /**
     * Verifies that POST /v1/api/allergies returns HTTP 201 Created and the
     * new allergy's details when an admin user submits a valid allergy DTO.
     *
     * <p>{@link AllergyService#createAllergy} is stubbed to return
     * {@code sampleAllergy}.  The test asserts the success message and spot-
     * checks {@code allergen} and {@code severity} in the nested {@code data}
     * object to confirm correct serialisation.
     */
    @Test
    @DisplayName("POST /v1/api/allergies - admin creates allergy, returns 201")
    void createAllergy_success() throws Exception {
        mockAdminSecurityContext();
        when(allergyService.createAllergy(any(AllergyDTO.class))).thenReturn(sampleAllergy);

        mockMvc.perform(post("/v1/api/allergies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleAllergy)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message", is("Allergy created successfully")))
                .andExpect(jsonPath("$.data.allergen", is("Penicillin")))
                .andExpect(jsonPath("$.data.severity", is("SEVERE")));

        Mockito.verify(allergyService).createAllergy(any(AllergyDTO.class));
    }

    /**
     * Verifies that POST /v1/api/allergies returns HTTP 201 Created when a
     * patient creates an allergy for their own record (self-access).
     *
     * <p>A patient whose user ID matches the patient entity's linked user
     * should be allowed to manage their own allergy data.  The test confirms
     * that the controller's access check passes for this case.
     */
    @Test
    @DisplayName("POST /v1/api/allergies - patient creates own allergy, returns 201")
    void createAllergy_patientSelfAccess_success() throws Exception {
        mockPatientSelfAccessContext();
        when(allergyService.createAllergy(any(AllergyDTO.class))).thenReturn(sampleAllergy);

        mockMvc.perform(post("/v1/api/allergies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleAllergy)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message", is("Allergy created successfully")));
    }

    /**
     * Verifies that POST /v1/api/allergies returns HTTP 403 Forbidden when a
     * patient attempts to create an allergy for a different patient's record.
     *
     * <p>The security context is set to a patient whose ID does not match the
     * target patient.  The test confirms that the controller's access-control
     * check rejects the request before delegating to the service.
     */
    @Test
    @DisplayName("POST /v1/api/allergies - unauthorized patient returns 403")
    void createAllergy_forbidden() throws Exception {
        mockForbiddenSecurityContext();

        mockMvc.perform(post("/v1/api/allergies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleAllergy)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error", is("Not authorized to manage allergies for this patient")));
    }

    /**
     * Verifies that POST /v1/api/allergies returns HTTP 400 Bad Request when
     * the service rejects the allergy due to a duplicate active entry.
     *
     * <p>The service is stubbed to throw an {@link IllegalArgumentException}
     * indicating that an active allergy for the same allergen already exists.
     * The test confirms that the controller translates this exception to a 400
     * response with an error body rather than a raw 500.
     */
    @Test
    @DisplayName("POST /v1/api/allergies - duplicate allergy returns 400")
    void createAllergy_duplicateAllergy_badRequest() throws Exception {
        mockAdminSecurityContext();
        when(allergyService.createAllergy(any(AllergyDTO.class)))
                .thenThrow(new IllegalArgumentException(
                        "Active allergy for 'Penicillin' already exists for this patient"));

        mockMvc.perform(post("/v1/api/allergies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleAllergy)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    /**
     * Verifies that POST /v1/api/allergies returns HTTP 500 Internal Server
     * Error when the service throws an unexpected {@link RuntimeException}.
     *
     * <p>The service is stubbed to throw a generic runtime exception simulating
     * an infrastructure failure.  The test confirms that the controller maps
     * this to a 500 with a user-friendly error message.
     */
    @Test
    @DisplayName("POST /v1/api/allergies - unexpected exception returns 500")
    void createAllergy_unexpectedError_returns500() throws Exception {
        mockAdminSecurityContext();
        when(allergyService.createAllergy(any(AllergyDTO.class)))
                .thenThrow(new RuntimeException("Database connection failed"));

        mockMvc.perform(post("/v1/api/allergies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleAllergy)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error", is("Failed to create allergy")));
    }

    // -----------------------------------------------------------------------
    // PUT /v1/api/allergies/{id}
    // -----------------------------------------------------------------------

    /**
     * Verifies that PUT /v1/api/allergies/{id} returns HTTP 200 and the
     * updated allergy DTO when an admin submits valid changes.
     *
     * <p>{@link AllergyService#getAllergy} is stubbed to confirm the allergy
     * exists, and {@link AllergyService#updateAllergy} is stubbed to return an
     * updated DTO with {@code severity=MODERATE}.  The test asserts the
     * response message and the changed severity field.
     */
    @Test
    @DisplayName("PUT /v1/api/allergies/{id} - updates allergy, returns 200")
    void updateAllergy_success() throws Exception {
        mockAdminSecurityContext();

        final AllergyDTO updated = AllergyDTO.builder()
                .id(1L)
                .patientId(10L)
                .allergen("Penicillin")
                .severity(AllergySeverity.MODERATE)
                .isActive(true)
                .build();

        when(allergyService.getAllergy(1L)).thenReturn(Optional.of(sampleAllergy));
        when(allergyService.updateAllergy(eq(1L), any(AllergyDTO.class))).thenReturn(updated);

        mockMvc.perform(put("/v1/api/allergies/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updated)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("Allergy updated successfully")))
                .andExpect(jsonPath("$.data.allergen", is("Penicillin")))
                .andExpect(jsonPath("$.data.severity", is("MODERATE")));

        Mockito.verify(allergyService).updateAllergy(eq(1L), any(AllergyDTO.class));
    }

    /**
     * Verifies that PUT /v1/api/allergies/{id} returns HTTP 404 Not Found when
     * the specified allergy ID does not exist.
     *
     * <p>{@link AllergyService#getAllergy} is stubbed to return
     * {@link Optional#empty()} for ID {@code 99L}.  The test confirms that the
     * controller detects the missing resource and returns a 404 with an
     * {@code error} field rather than proceeding with the update.
     */
    @Test
    @DisplayName("PUT /v1/api/allergies/{id} - allergy not found returns 404")
    void updateAllergy_notFound() throws Exception {
        mockAdminSecurityContext();
        when(allergyService.getAllergy(99L)).thenReturn(Optional.empty());

        mockMvc.perform(put("/v1/api/allergies/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleAllergy)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", is("Allergy not found")));
    }

    /**
     * Verifies that PUT /v1/api/allergies/{id} returns HTTP 403 Forbidden when
     * the current user does not have access to the target patient's records.
     *
     * <p>The security context is configured for a user who is not associated
     * with patient ID 10.  The test confirms that the access-control check
     * prevents the update from reaching the service.
     */
    @Test
    @DisplayName("PUT /v1/api/allergies/{id} - unauthorized user returns 403")
    void updateAllergy_forbidden() throws Exception {
        mockForbiddenSecurityContext();
        when(allergyService.getAllergy(1L)).thenReturn(Optional.of(sampleAllergy));

        mockMvc.perform(put("/v1/api/allergies/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleAllergy)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error", is("Not authorized to manage allergies for this patient")));
    }

    /**
     * Verifies that PUT /v1/api/allergies/{id} returns HTTP 400 Bad Request
     * when the service throws an {@link IllegalArgumentException} due to
     * invalid allergy data.
     *
     * <p>The service is stubbed to throw after the allergy existence check
     * passes.  The test confirms that business-rule violations during update
     * are surfaced as 400 errors.
     */
    @Test
    @DisplayName("PUT /v1/api/allergies/{id} - invalid data returns 400")
    void updateAllergy_badRequest() throws Exception {
        mockAdminSecurityContext();
        when(allergyService.getAllergy(1L)).thenReturn(Optional.of(sampleAllergy));
        when(allergyService.updateAllergy(eq(1L), any(AllergyDTO.class)))
                .thenThrow(new IllegalArgumentException("Invalid allergy data"));

        mockMvc.perform(put("/v1/api/allergies/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleAllergy)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    /**
     * Verifies that PUT /v1/api/allergies/{id} returns HTTP 500 when the
     * service throws an unexpected {@link RuntimeException}.
     *
     * <p>The service is stubbed to throw a generic runtime exception after the
     * existence check.  The test confirms that infrastructure failures are
     * mapped to a 500 with a user-friendly error message.
     */
    @Test
    @DisplayName("PUT /v1/api/allergies/{id} - unexpected exception returns 500")
    void updateAllergy_unexpectedError_returns500() throws Exception {
        mockAdminSecurityContext();
        when(allergyService.getAllergy(1L)).thenReturn(Optional.of(sampleAllergy));
        when(allergyService.updateAllergy(eq(1L), any(AllergyDTO.class)))
                .thenThrow(new RuntimeException("DB error"));

        mockMvc.perform(put("/v1/api/allergies/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleAllergy)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error", is("Failed to update allergy")));
    }

    // -----------------------------------------------------------------------
    // GET /v1/api/allergies/patient/{patientId}
    // -----------------------------------------------------------------------

    /**
     * Verifies that GET /v1/api/allergies/patient/{patientId} returns HTTP 200
     * and all allergies for the patient when the caller is authorised.
     *
     * <p>{@link AllergyService#getAllergiesForPatient} is stubbed to return a
     * list containing {@code sampleAllergy}.  The test spot-checks
     * {@code allergen} and {@code isActive} inside the nested {@code data}
     * array to confirm correct serialisation.
     */
    @Test
    @DisplayName("GET /v1/api/allergies/patient/{patientId} - returns all allergies with 200")
    void getAllergiesForPatient_success() throws Exception {
        mockAdminSecurityContext();
        when(allergyService.getAllergiesForPatient(10L)).thenReturn(List.of(sampleAllergy));

        mockMvc.perform(get("/v1/api/allergies/patient/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("Allergies retrieved successfully")))
                .andExpect(jsonPath("$.data[0].allergen", is("Penicillin")))
                .andExpect(jsonPath("$.data[0].isActive", is(true)));

        Mockito.verify(allergyService).getAllergiesForPatient(10L);
    }

    /**
     * Verifies that GET /v1/api/allergies/patient/{patientId} returns HTTP 200
     * with an empty {@code data} array when the patient has no recorded
     * allergies.
     *
     * <p>An empty result is a valid, non-error state.  The test confirms that
     * the controller returns a properly structured response rather than a 404
     * or an error when no allergies are found.
     */
    @Test
    @DisplayName("GET /v1/api/allergies/patient/{patientId} - returns empty list when none exist")
    void getAllergiesForPatient_emptyList() throws Exception {
        mockAdminSecurityContext();
        when(allergyService.getAllergiesForPatient(10L)).thenReturn(List.of());

        mockMvc.perform(get("/v1/api/allergies/patient/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    /**
     * Verifies that GET /v1/api/allergies/patient/{patientId} returns HTTP 403
     * Forbidden when the current user is not authorised to view the patient's
     * allergies.
     *
     * <p>The security context is set to a different patient user.  The test
     * confirms that the controller's access check prevents the service call
     * and returns an appropriate error message.
     */
    @Test
    @DisplayName("GET /v1/api/allergies/patient/{patientId} - unauthorized user returns 403")
    void getAllergiesForPatient_forbidden() throws Exception {
        mockForbiddenSecurityContext();

        mockMvc.perform(get("/v1/api/allergies/patient/10"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error", is("Not authorized to view allergies for this patient")));
    }

    /**
     * Verifies that GET /v1/api/allergies/patient/{patientId} returns HTTP 500
     * when the service throws an unexpected {@link RuntimeException}.
     *
     * <p>The service is stubbed to throw, simulating a database failure.  The
     * test confirms that the controller maps the exception to a 500 with a
     * user-friendly error message.
     */
    @Test
    @DisplayName("GET /v1/api/allergies/patient/{patientId} - unexpected exception returns 500")
    void getAllergiesForPatient_unexpectedError_returns500() throws Exception {
        mockAdminSecurityContext();
        when(allergyService.getAllergiesForPatient(10L))
                .thenThrow(new RuntimeException("DB error"));

        mockMvc.perform(get("/v1/api/allergies/patient/10"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error", is("Failed to retrieve allergies")));
    }

    // -----------------------------------------------------------------------
    // GET /v1/api/allergies/patient/{patientId}/active
    // -----------------------------------------------------------------------

    /**
     * Verifies that GET /v1/api/allergies/patient/{patientId}/active returns
     * HTTP 200 and only the active allergies for the patient.
     *
     * <p>{@link AllergyService#getActiveAllergiesForPatient} is stubbed to
     * return a list containing {@code sampleAllergy} (which is active).  The
     * test confirms the response message and the {@code allergen} field.
     */
    @Test
    @DisplayName("GET /v1/api/allergies/patient/{patientId}/active - returns active allergies with 200")
    void getActiveAllergiesForPatient_success() throws Exception {
        mockAdminSecurityContext();
        when(allergyService.getActiveAllergiesForPatient(10L)).thenReturn(List.of(sampleAllergy));

        mockMvc.perform(get("/v1/api/allergies/patient/10/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("Active allergies retrieved successfully")))
                .andExpect(jsonPath("$.data[0].allergen", is("Penicillin")));

        Mockito.verify(allergyService).getActiveAllergiesForPatient(10L);
    }

    /**
     * Verifies that GET /v1/api/allergies/patient/{patientId}/active returns
     * HTTP 200 when a patient accesses their own active allergies (self-access).
     *
     * <p>A patient should always be able to view their own active allergy list.
     * The test confirms that the controller's access check passes for self-access
     * and that the response includes the correct success message.
     */
    @Test
    @DisplayName("GET /v1/api/allergies/patient/{patientId}/active - patient self-access succeeds")
    void getActiveAllergiesForPatient_patientSelfAccess() throws Exception {
        mockPatientSelfAccessContext();
        when(allergyService.getActiveAllergiesForPatient(10L)).thenReturn(List.of(sampleAllergy));

        mockMvc.perform(get("/v1/api/allergies/patient/10/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("Active allergies retrieved successfully")));
    }

    /**
     * Verifies that GET /v1/api/allergies/patient/{patientId}/active returns
     * HTTP 403 Forbidden when the current user is not authorised to view the
     * patient's active allergies.
     *
     * <p>The security context is set to an unrelated patient user.  The test
     * confirms that the access check prevents the service call.
     */
    @Test
    @DisplayName("GET /v1/api/allergies/patient/{patientId}/active - unauthorized user returns 403")
    void getActiveAllergiesForPatient_forbidden() throws Exception {
        mockForbiddenSecurityContext();

        mockMvc.perform(get("/v1/api/allergies/patient/10/active"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error", is("Not authorized to view allergies for this patient")));
    }

    /**
     * Verifies that GET /v1/api/allergies/patient/{patientId}/active returns
     * HTTP 500 when the service throws an unexpected {@link RuntimeException}.
     *
     * <p>The service is stubbed to throw, simulating a database failure.  The
     * test confirms that the controller maps the exception to a 500 with a
     * descriptive error message.
     */
    @Test
    @DisplayName("GET /v1/api/allergies/patient/{patientId}/active - unexpected exception returns 500")
    void getActiveAllergiesForPatient_unexpectedError_returns500() throws Exception {
        mockAdminSecurityContext();
        when(allergyService.getActiveAllergiesForPatient(10L))
                .thenThrow(new RuntimeException("DB error"));

        mockMvc.perform(get("/v1/api/allergies/patient/10/active"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error", is("Failed to retrieve active allergies")));
    }

    // -----------------------------------------------------------------------
    // PATCH /v1/api/allergies/{id}/deactivate
    // -----------------------------------------------------------------------

    /**
     * Verifies that PATCH /v1/api/allergies/{id}/deactivate returns HTTP 200
     * and a success message when an admin deactivates an existing allergy.
     *
     * <p>The allergy's existence is confirmed via {@link AllergyService#getAllergy},
     * and {@link AllergyService#deactivateAllergy} is stubbed as a no-op.
     * The test asserts the success message and confirms the service was called.
     */
    @Test
    @DisplayName("PATCH /v1/api/allergies/{id}/deactivate - deactivates allergy, returns 200")
    void deactivateAllergy_success() throws Exception {
        mockAdminSecurityContext();
        when(allergyService.getAllergy(1L)).thenReturn(Optional.of(sampleAllergy));
        Mockito.doNothing().when(allergyService).deactivateAllergy(1L);

        mockMvc.perform(patch("/v1/api/allergies/1/deactivate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("Allergy deactivated successfully")));

        Mockito.verify(allergyService).deactivateAllergy(1L);
    }

    /**
     * Verifies that PATCH /v1/api/allergies/{id}/deactivate returns HTTP 404
     * Not Found when the specified allergy ID does not exist.
     *
     * <p>{@link AllergyService#getAllergy} is stubbed to return
     * {@link Optional#empty()} for ID {@code 99L}.  The test confirms that the
     * controller detects the missing resource and returns 404 before attempting
     * to deactivate it.
     */
    @Test
    @DisplayName("PATCH /v1/api/allergies/{id}/deactivate - allergy not found returns 404")
    void deactivateAllergy_notFound() throws Exception {
        mockAdminSecurityContext();
        when(allergyService.getAllergy(99L)).thenReturn(Optional.empty());

        mockMvc.perform(patch("/v1/api/allergies/99/deactivate"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", is("Allergy not found")));
    }

    /**
     * Verifies that PATCH /v1/api/allergies/{id}/deactivate returns HTTP 403
     * Forbidden when the current user does not have access to the patient's
     * allergy records.
     *
     * <p>The security context is set to an unrelated patient user.  The test
     * confirms that the access-control check prevents deactivation.
     */
    @Test
    @DisplayName("PATCH /v1/api/allergies/{id}/deactivate - unauthorized user returns 403")
    void deactivateAllergy_forbidden() throws Exception {
        mockForbiddenSecurityContext();
        when(allergyService.getAllergy(1L)).thenReturn(Optional.of(sampleAllergy));

        mockMvc.perform(patch("/v1/api/allergies/1/deactivate"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error", is("Not authorized to manage allergies for this patient")));
    }

    /**
     * Verifies that PATCH /v1/api/allergies/{id}/deactivate returns HTTP 400
     * when the service throws an {@link IllegalArgumentException} (e.g., the
     * allergy was not found during deactivation despite passing the guard).
     *
     * <p>The service is stubbed to throw after the existence check.  The test
     * confirms that business-rule violations are surfaced as 400 errors.
     */
    @Test
    @DisplayName("PATCH /v1/api/allergies/{id}/deactivate - service throws IllegalArgumentException returns 400")
    void deactivateAllergy_badRequest() throws Exception {
        mockAdminSecurityContext();
        when(allergyService.getAllergy(1L)).thenReturn(Optional.of(sampleAllergy));
        Mockito.doThrow(new IllegalArgumentException("Allergy not found with id: 1"))
                .when(allergyService).deactivateAllergy(1L);

        mockMvc.perform(patch("/v1/api/allergies/1/deactivate"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    /**
     * Verifies that PATCH /v1/api/allergies/{id}/deactivate returns HTTP 500
     * when the service throws an unexpected {@link RuntimeException}.
     *
     * <p>The service is stubbed to throw a generic runtime exception.  The
     * test confirms that infrastructure failures are mapped to a 500 with a
     * user-friendly error message.
     */
    @Test
    @DisplayName("PATCH /v1/api/allergies/{id}/deactivate - unexpected exception returns 500")
    void deactivateAllergy_unexpectedError_returns500() throws Exception {
        mockAdminSecurityContext();
        when(allergyService.getAllergy(1L)).thenReturn(Optional.of(sampleAllergy));
        Mockito.doThrow(new RuntimeException("DB error"))
                .when(allergyService).deactivateAllergy(1L);

        mockMvc.perform(patch("/v1/api/allergies/1/deactivate"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error", is("Failed to deactivate allergy")));
    }

    // -----------------------------------------------------------------------
    // DELETE /v1/api/allergies/{id}
    // -----------------------------------------------------------------------

    /**
     * Verifies that DELETE /v1/api/allergies/{id} returns HTTP 200 and a
     * success message when an admin permanently deletes an existing allergy.
     *
     * <p>The allergy's existence is confirmed via {@link AllergyService#getAllergy},
     * and {@link AllergyService#deleteAllergy} is stubbed as a no-op.  The
     * test asserts the success message and confirms the service was called.
     */
    @Test
    @DisplayName("DELETE /v1/api/allergies/{id} - deletes allergy, returns 200")
    void deleteAllergy_success() throws Exception {
        mockAdminSecurityContext();
        when(allergyService.getAllergy(1L)).thenReturn(Optional.of(sampleAllergy));
        Mockito.doNothing().when(allergyService).deleteAllergy(1L);

        mockMvc.perform(delete("/v1/api/allergies/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("Allergy deleted successfully")));

        Mockito.verify(allergyService).deleteAllergy(1L);
    }

    /**
     * Verifies that DELETE /v1/api/allergies/{id} returns HTTP 404 Not Found
     * when the specified allergy ID does not exist.
     *
     * <p>{@link AllergyService#getAllergy} is stubbed to return
     * {@link Optional#empty()} for ID {@code 99L}.  The test confirms that
     * the controller returns 404 before attempting the deletion.
     */
    @Test
    @DisplayName("DELETE /v1/api/allergies/{id} - allergy not found returns 404")
    void deleteAllergy_notFound() throws Exception {
        mockAdminSecurityContext();
        when(allergyService.getAllergy(99L)).thenReturn(Optional.empty());

        mockMvc.perform(delete("/v1/api/allergies/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", is("Allergy not found")));
    }

    /**
     * Verifies that DELETE /v1/api/allergies/{id} returns HTTP 403 Forbidden
     * when the current user does not have access to the patient's allergy
     * records.
     *
     * <p>The security context is set to an unrelated patient user.  The test
     * confirms that the access-control check prevents deletion.
     */
    @Test
    @DisplayName("DELETE /v1/api/allergies/{id} - unauthorized user returns 403")
    void deleteAllergy_forbidden() throws Exception {
        mockForbiddenSecurityContext();
        when(allergyService.getAllergy(1L)).thenReturn(Optional.of(sampleAllergy));

        mockMvc.perform(delete("/v1/api/allergies/1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error", is("Not authorized to manage allergies for this patient")));
    }

    /**
     * Verifies that DELETE /v1/api/allergies/{id} returns HTTP 400 when the
     * service throws an {@link IllegalArgumentException} during deletion.
     *
     * <p>The service is stubbed to throw after the existence check passes.
     * The test confirms that business-rule violations during deletion are
     * surfaced as 400 errors.
     */
    @Test
    @DisplayName("DELETE /v1/api/allergies/{id} - service throws IllegalArgumentException returns 400")
    void deleteAllergy_badRequest() throws Exception {
        mockAdminSecurityContext();
        when(allergyService.getAllergy(1L)).thenReturn(Optional.of(sampleAllergy));
        Mockito.doThrow(new IllegalArgumentException("Allergy not found with id: 1"))
                .when(allergyService).deleteAllergy(1L);

        mockMvc.perform(delete("/v1/api/allergies/1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    /**
     * Verifies that DELETE /v1/api/allergies/{id} returns HTTP 500 when the
     * service throws an unexpected {@link RuntimeException}.
     *
     * <p>The service is stubbed to throw a generic runtime exception.  The
     * test confirms that infrastructure failures are mapped to a 500 with a
     * user-friendly error message.
     */
    @Test
    @DisplayName("DELETE /v1/api/allergies/{id} - unexpected exception returns 500")
    void deleteAllergy_unexpectedError_returns500() throws Exception {
        mockAdminSecurityContext();
        when(allergyService.getAllergy(1L)).thenReturn(Optional.of(sampleAllergy));
        Mockito.doThrow(new RuntimeException("DB error"))
                .when(allergyService).deleteAllergy(1L);

        mockMvc.perform(delete("/v1/api/allergies/1"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error", is("Failed to delete allergy")));
    }
}
