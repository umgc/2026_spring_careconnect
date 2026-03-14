package com.careconnect.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.careconnect.dto.v2.TaskDtoV2;
import com.careconnect.model.Patient;
import com.careconnect.model.User;
import com.careconnect.repository.PatientRepository;
import com.careconnect.repository.UserRepository;
import com.careconnect.security.AuthorizationService;
import com.careconnect.security.JwtTokenProvider;
import com.careconnect.security.Role;
import com.careconnect.util.SecurityUtil;
import com.careconnect.service.v2.TaskServiceV2;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Unit tests for {@link AlexaController}, covering the HTTP layer of the
 * Alexa-facing calendar task endpoints.
 *
 * <p><b>Why @WebMvcTest + MockMvc?</b><br>
 * {@code @WebMvcTest} spins up only the Spring MVC slice without a real
 * database or a full application context, keeping the tests fast.  Each test
 * verifies that the controller correctly handles token validation, patient
 * resolution, task delegation, and error mapping — without exercising the
 * underlying service implementations.
 *
 * <p>All service and repository collaborators are replaced with Mockito mocks
 * via {@code @MockBean}.  Security filters are disabled with
 * {@code @AutoConfigureMockMvc(addFilters = false)} so that the token
 * validation logic inside the controller itself (reading the
 * {@code Authorization} header and calling {@link JwtTokenProvider}) can be
 * tested in isolation, without interference from Spring Security's filter chain.
 */
@WebMvcTest(AlexaController.class)
@AutoConfigureMockMvc(addFilters = false)
class AlexaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // --- Mocked collaborators ---
    // Each bean below is replaced with a Mockito stub so the controller can be
    // instantiated without real JWT infrastructure, a database, or task storage.

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private PatientRepository patientRepository;

    @MockitoBean
    private TaskServiceV2 taskService;

    @MockitoBean
    private SecurityUtil securityUtil;

    @MockitoBean
    private AuthorizationService authorizationService;

    // --- Shared test constants ---

    private static final String VALID_TOKEN = "valid.jwt.token";
    private static final String BEARER_TOKEN = "Bearer " + VALID_TOKEN;
    private static final Long PATIENT_ID = 42L;

    // --- Test fixtures ---

    private User patientUser;
    private Patient patient;
    private TaskDtoV2 sampleTask;

    @BeforeEach
    void setup() throws Exception {
        patientUser = new User();
        patientUser.setId(10L);
        patientUser.setEmail("patient@test.com");
        patientUser.setRole(Role.PATIENT);

        patient = new Patient();
        patient.setId(PATIENT_ID);
        patient.setUser(patientUser);

        sampleTask = TaskDtoV2.builder()
                .id(1L)
                .name("Take Medication")
                .description("Take blood pressure pill")
                .date(LocalDate.now() + "T00:00:00")
                .isCompleted(false)
                .taskType("Medication")
                .build();

        // Stub securityUtil.resolveCurrentUser() so that controller-level
        // role checks (e.g. isFamilyMember()) do not NPE.
        Mockito.when(securityUtil.resolveCurrentUser()).thenReturn(patientUser);

        // Default: token is valid
        Mockito.when(jwtTokenProvider.validateToken(VALID_TOKEN)).thenReturn(true);
        Mockito.when(jwtTokenProvider.getEmailFromToken(VALID_TOKEN)).thenReturn("patient@test.com");
        Mockito.when(userRepository.findByEmail("patient@test.com")).thenReturn(Optional.of(patientUser));
        Mockito.when(patientRepository.findByUser(patientUser)).thenReturn(Optional.of(patient));
    }

    // =========================================================================
    // GET /v1/api/alexa/calendarTasks/get
    // =========================================================================

    /**
     * Verifies that GET /v1/api/alexa/calendarTasks/get returns HTTP 200 and
     * all tasks when the {@code filter} parameter is {@code "all"}.
     *
     * <p>The "all" filter signals that no date-based narrowing should be
     * applied.  {@link TaskServiceV2#getTasksByPatient} is stubbed to return a
     * list containing {@code sampleTask}.  The test confirms that the controller
     * forwards the full list without filtering.
     */
    @Test
    @DisplayName("GET /calendarTasks/get returns all tasks when filter=all")
    void testGetCalendarTasks_allFilter() throws Exception {
        Mockito.when(taskService.getTasksByPatient(PATIENT_ID)).thenReturn(List.of(sampleTask));

        mockMvc.perform(get("/v1/api/alexa/calendarTasks/get")
                        .header("Authorization", BEARER_TOKEN)
                        .param("filter", "all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name", is("Take Medication")))
                .andExpect(jsonPath("$[0].id", is(1)));

        Mockito.verify(taskService).getTasksByPatient(PATIENT_ID);
    }

    /**
     * Verifies that GET /v1/api/alexa/calendarTasks/get returns only tasks
     * whose date falls within the current week when {@code filter=week}.
     *
     * <p>Two tasks are stubbed: one dated today (within the current week) and
     * one dated 10 days ago (outside the window).  The test confirms that the
     * controller's week filter includes only the today task, verifying the
     * date-range logic applied to the service's response.
     */
    @Test
    @DisplayName("GET /calendarTasks/get returns only this-week tasks when filter=week")
    void testGetCalendarTasks_weekFilter_includesTaskForToday() throws Exception {
        final TaskDtoV2 todayTask = TaskDtoV2.builder()
                .id(2L)
                .name("Today Task")
                .date(LocalDate.now() + "T00:00:00")
                .isCompleted(false)
                .build();

        final TaskDtoV2 oldTask = TaskDtoV2.builder()
                .id(3L)
                .name("Old Task")
                .date(LocalDate.now().minusDays(10) + "T00:00:00")
                .isCompleted(false)
                .build();

        Mockito.when(taskService.getTasksByPatient(PATIENT_ID)).thenReturn(List.of(todayTask, oldTask));

        mockMvc.perform(get("/v1/api/alexa/calendarTasks/get")
                        .header("Authorization", BEARER_TOKEN)
                        .param("filter", "week"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(1)))
                .andExpect(jsonPath("$[0].name", is("Today Task")));
    }

    /**
     * Verifies that GET /v1/api/alexa/calendarTasks/get returns HTTP 401 when
     * the {@code Authorization} header is absent.
     *
     * <p>The Alexa calendar endpoint requires a valid JWT in the
     * {@code Authorization} header.  This test confirms that the controller
     * rejects requests with no token and returns a JSON body with an
     * {@code error} field describing the failure.
     */
    @Test
    @DisplayName("GET /calendarTasks/get returns 401 when no Authorization header")
    void testGetCalendarTasks_missingToken() throws Exception {
        mockMvc.perform(get("/v1/api/alexa/calendarTasks/get"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error", is("Missing or invalid access token")));
    }

    /**
     * Verifies that GET /v1/api/alexa/calendarTasks/get returns HTTP 401 when
     * the token in the {@code Authorization} header fails JWT validation.
     *
     * <p>{@link JwtTokenProvider#validateToken} is stubbed to return
     * {@code false} for {@code "bad.token"}.  The test confirms that the
     * controller correctly identifies an invalid token and responds with 401
     * rather than proceeding with the request.
     */
    @Test
    @DisplayName("GET /calendarTasks/get returns 401 when token is invalid")
    void testGetCalendarTasks_invalidToken() throws Exception {
        Mockito.when(jwtTokenProvider.validateToken("bad.token")).thenReturn(false);

        mockMvc.perform(get("/v1/api/alexa/calendarTasks/get")
                        .header("Authorization", "Bearer bad.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error", is("Missing or invalid access token")));
    }

    /**
     * Verifies that GET /v1/api/alexa/calendarTasks/get returns HTTP 400 when
     * a valid token is supplied but the associated user cannot be resolved to
     * a patient record.
     *
     * <p>{@link PatientRepository#findByUser} is stubbed to return
     * {@link Optional#empty()}, simulating a user whose patient profile does
     * not exist or cannot be determined.  The test confirms that the controller
     * returns a descriptive 400 error rather than proceeding with a null
     * patient ID.
     */
    @Test
    @DisplayName("GET /calendarTasks/get returns 400 when patient cannot be resolved")
    void testGetCalendarTasks_patientNotFound() throws Exception {
        Mockito.when(patientRepository.findByUser(patientUser)).thenReturn(Optional.empty());

        mockMvc.perform(get("/v1/api/alexa/calendarTasks/get")
                        .header("Authorization", BEARER_TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Unable to resolve patient ID")));
    }

    /**
     * Verifies that GET /v1/api/alexa/calendarTasks/get correctly resolves the
     * patient ID when the authenticated user has the {@code CAREGIVER} role.
     *
     * <p>For caregiver users the controller must look up the patient differently
     * (via {@link PatientRepository#findAll} and access checking) rather than
     * using {@code findByUser}.  This test stubs the full caregiver resolution
     * path and confirms that tasks are returned for the correct patient.
     */
    @Test
    @DisplayName("GET /calendarTasks/get resolves patient via caregiver role")
    void testGetCalendarTasks_caregiverRole() throws Exception {
        final User caregiverUser = new User();
        caregiverUser.setId(20L);
        caregiverUser.setEmail("caregiver@test.com");
        caregiverUser.setRole(Role.CAREGIVER);

        Mockito.when(jwtTokenProvider.getEmailFromToken(VALID_TOKEN)).thenReturn("caregiver@test.com");
        Mockito.when(userRepository.findByEmail("caregiver@test.com")).thenReturn(Optional.of(caregiverUser));
        Mockito.when(patientRepository.findAll()).thenReturn(List.of(patient));
        Mockito.when(patientRepository.hasAccessByCaregiverId(PATIENT_ID, 20L)).thenReturn(true);
        Mockito.when(taskService.getTasksByPatient(PATIENT_ID)).thenReturn(List.of(sampleTask));

        mockMvc.perform(get("/v1/api/alexa/calendarTasks/get")
                        .header("Authorization", BEARER_TOKEN)
                        .param("filter", "all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name", is("Take Medication")));

        Mockito.verify(taskService).getTasksByPatient(PATIENT_ID);
    }

    /**
     * Verifies that GET /v1/api/alexa/calendarTasks/get applies the week filter
     * by default when the {@code filter} query parameter is not provided.
     *
     * <p>Alexa requests may omit the filter parameter; the controller should
     * default to the {@code "week"} view rather than returning all historical
     * tasks.  This test stubs a task dated today (within the current week) and
     * confirms it is included in the response without an explicit filter param.
     */
    @Test
    @DisplayName("GET /calendarTasks/get uses default week filter when no filter param provided")
    void testGetCalendarTasks_defaultFilterIsWeek() throws Exception {
        final TaskDtoV2 todayTask = TaskDtoV2.builder()
                .id(4L)
                .name("Now Task")
                .date(LocalDate.now() + "T00:00:00")
                .isCompleted(false)
                .build();

        Mockito.when(taskService.getTasksByPatient(PATIENT_ID)).thenReturn(List.of(todayTask));

        mockMvc.perform(get("/v1/api/alexa/calendarTasks/get")
                        .header("Authorization", BEARER_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name", is("Now Task")));
    }

    // =========================================================================
    // POST /v1/api/alexa/calendarTasks/add
    // =========================================================================

    /**
     * Verifies that POST /v1/api/alexa/calendarTasks/add returns HTTP 201
     * Created and the new task DTO when a valid {@code Authorization} header
     * and request body are provided.
     *
     * <p>{@link TaskServiceV2#createTask} is stubbed to return
     * {@code sampleTask} for the resolved patient ID.  The test confirms that
     * the controller extracts the token from the header, resolves the patient,
     * delegates to the service, and wraps the result in a 201 response.
     */
    @Test
    @DisplayName("POST /calendarTasks/add creates a task with Authorization header")
    void testAddCalendarTask_success() throws Exception {
        Mockito.when(taskService.createTask(eq(PATIENT_ID), any(TaskDtoV2.class))).thenReturn(sampleTask);

        final Map<String, Object> body = Map.of(
                "name", "Take Medication",
                "date", LocalDate.now().toString(),
                "taskType", "Medication"
        );

        mockMvc.perform(post("/v1/api/alexa/calendarTasks/add")
                        .header("Authorization", BEARER_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("Take Medication")));

        Mockito.verify(taskService).createTask(eq(PATIENT_ID), any(TaskDtoV2.class));
    }

    /**
     * Verifies that POST /v1/api/alexa/calendarTasks/add falls back to reading
     * the token from the JSON request body ({@code accessToken} field) when no
     * {@code Authorization} header is present.
     *
     * <p>Alexa skill requests may include the access token in the body rather
     * than the header.  This test confirms that the controller supports this
     * fallback mechanism, enabling Alexa integrations that cannot set HTTP
     * headers directly.
     */
    @Test
    @DisplayName("POST /calendarTasks/add creates a task using accessToken in body as fallback")
    void testAddCalendarTask_tokenFromBody() throws Exception {
        Mockito.when(taskService.createTask(eq(PATIENT_ID), any(TaskDtoV2.class))).thenReturn(sampleTask);

        final Map<String, Object> body = Map.of(
                "name", "Walk Outside",
                "date", LocalDate.now().toString(),
                "accessToken", VALID_TOKEN
        );

        mockMvc.perform(post("/v1/api/alexa/calendarTasks/add")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("Take Medication")));
    }

    /**
     * Verifies that POST /v1/api/alexa/calendarTasks/add returns HTTP 401 when
     * neither the {@code Authorization} header nor the {@code accessToken} body
     * field contains a valid token.
     *
     * <p>The request is sent with no token information at all.  The test
     * confirms that the controller rejects the unauthenticated request with
     * a 401 and a descriptive error message.
     */
    @Test
    @DisplayName("POST /calendarTasks/add returns 401 when token is missing or invalid")
    void testAddCalendarTask_invalidToken() throws Exception {
        final Map<String, Object> body = Map.of("name", "Walk", "date", LocalDate.now().toString());

        mockMvc.perform(post("/v1/api/alexa/calendarTasks/add")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error", is("Missing or invalid access token")));
    }

    /**
     * Verifies that POST /v1/api/alexa/calendarTasks/add returns HTTP 400 when
     * a valid token is supplied but the associated user cannot be resolved to
     * a patient record.
     *
     * <p>{@link PatientRepository#findByUser} is stubbed to return
     * {@link Optional#empty()}, simulating a missing patient profile.  The test
     * confirms that the controller returns 400 with a descriptive error rather
     * than proceeding to create a task with an unresolved patient ID.
     */
    @Test
    @DisplayName("POST /calendarTasks/add returns 400 when patient cannot be resolved")
    void testAddCalendarTask_patientNotFound() throws Exception {
        Mockito.when(patientRepository.findByUser(patientUser)).thenReturn(Optional.empty());

        final Map<String, Object> body = Map.of("name", "Walk", "date", LocalDate.now().toString());

        mockMvc.perform(post("/v1/api/alexa/calendarTasks/add")
                        .header("Authorization", BEARER_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Unable to resolve patient ID")));
    }

    /**
     * Verifies that POST /v1/api/alexa/calendarTasks/add returns HTTP 400 when
     * the {@code name} field is absent from the request body.
     *
     * <p>A task name is mandatory.  This test confirms that the controller
     * validates the presence of the name before delegating to the service,
     * returning a descriptive 400 error rather than creating a nameless task.
     */
    @Test
    @DisplayName("POST /calendarTasks/add returns 400 when task name is missing")
    void testAddCalendarTask_missingName() throws Exception {
        final Map<String, Object> body = Map.of("date", LocalDate.now().toString());

        mockMvc.perform(post("/v1/api/alexa/calendarTasks/add")
                        .header("Authorization", BEARER_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Task name is required")));
    }

    /**
     * Verifies that POST /v1/api/alexa/calendarTasks/add returns HTTP 403 when
     * the service throws a {@link RuntimeException} whose message contains
     * "Unauthorized".
     *
     * <p>An "Unauthorized" exception from the service indicates that the
     * authenticated user does not have permission to create tasks for the
     * resolved patient.  The test confirms that the controller maps this
     * specific failure to a 403 Forbidden response to clearly distinguish
     * authorisation failures from general errors.
     */
    @Test
    @DisplayName("POST /calendarTasks/add returns 403 when service rejects with Unauthorized")
    void testAddCalendarTask_serviceThrowsUnauthorized() throws Exception {
        Mockito.when(taskService.createTask(eq(PATIENT_ID), any(TaskDtoV2.class)))
                .thenThrow(new RuntimeException("Unauthorized access to patient data"));

        final Map<String, Object> body = Map.of(
                "name", "Take Medication",
                "date", LocalDate.now().toString()
        );

        mockMvc.perform(post("/v1/api/alexa/calendarTasks/add")
                        .header("Authorization", BEARER_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error", is("Access denied to patient data")));
    }

    /**
     * Verifies that POST /v1/api/alexa/calendarTasks/add returns HTTP 500 when
     * the service throws an unexpected {@link RuntimeException} that does not
     * indicate an authorisation failure.
     *
     * <p>The service is stubbed to throw a generic infrastructure error (e.g.,
     * a database connection loss).  The test confirms that the controller
     * catches the exception and returns a 500 Internal Server Error with a
     * user-friendly error message.
     */
    @Test
    @DisplayName("POST /calendarTasks/add returns 500 when service throws unexpected error")
    void testAddCalendarTask_serviceThrowsGenericError() throws Exception {
        Mockito.when(taskService.createTask(eq(PATIENT_ID), any(TaskDtoV2.class)))
                .thenThrow(new RuntimeException("Database connection lost"));

        final Map<String, Object> body = Map.of(
                "name", "Take Medication",
                "date", LocalDate.now().toString()
        );

        mockMvc.perform(post("/v1/api/alexa/calendarTasks/add")
                        .header("Authorization", BEARER_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error", is("Error adding task")));
    }

    /**
     * Verifies that POST /v1/api/alexa/calendarTasks/add normalises an
     * unparseable date string by defaulting it to today's date, then proceeds
     * to create the task successfully.
     *
     * <p>Alexa may send dates in an unexpected format or with a user speech
     * error.  The controller should gracefully recover by substituting today's
     * date rather than rejecting the request.  The test confirms a 201
     * response and the expected task name in the body.
     */
    @Test
    @DisplayName("POST /calendarTasks/add normalizes an invalid date to today")
    void testAddCalendarTask_invalidDateDefaultsToToday() throws Exception {
        final TaskDtoV2 created = TaskDtoV2.builder()
                .id(5L)
                .name("Walk")
                .date(LocalDate.now() + "T00:00:00")
                .isCompleted(false)
                .build();

        Mockito.when(taskService.createTask(eq(PATIENT_ID), any(TaskDtoV2.class))).thenReturn(created);

        final Map<String, Object> body = Map.of(
                "name", "Walk",
                "date", "not-a-date"
        );

        mockMvc.perform(post("/v1/api/alexa/calendarTasks/add")
                        .header("Authorization", BEARER_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("Walk")));
    }
}
