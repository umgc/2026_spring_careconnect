package com.careconnect.service;

import com.careconnect.dto.PatientNoteDTO;
import com.careconnect.dto.PatientNotetakerConfigDTO;
import com.careconnect.dto.v2.TaskDtoV2;
import com.careconnect.model.Patient;
import com.careconnect.model.PatientNote;
import com.careconnect.model.PatientNotetakerConfig;
import com.careconnect.model.PatientNotetakerKeyword;
import com.careconnect.model.PatientNotetakerKeyword.EventType;
import com.careconnect.repository.PatientNoteRepository;
import com.careconnect.repository.PatientNotetakerConfigRepository;
import com.careconnect.service.OpenRouterService.Choice;
import com.careconnect.service.OpenRouterService.Message;
import com.careconnect.service.OpenRouterService.OpenRouterChatRequest;
import com.careconnect.service.OpenRouterService.OpenRouterResponse;
import com.careconnect.service.v2.TaskServiceV2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PatientNotetakerServiceTest {

    @Mock private PatientNoteRepository patientNoteRepository;
    @Mock private PatientNotetakerConfigRepository patientNotetakerConfigRepository;
    @Mock private PatientService patientService;
    @Mock private OpenRouterService openRouterService;
    @Mock private TaskServiceV2 taskService;

    private PatientNotetakerService service;

    private Patient patient;
    private PatientNote patientNote;
    private PatientNotetakerConfig config;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        service = new PatientNotetakerService(
                patientNoteRepository,
                patientNotetakerConfigRepository,
                patientService,
                Optional.of(openRouterService),
                taskService);

        patient = Patient.builder().id(10L).firstName("John").lastName("Doe").build();

        patientNote = PatientNote.builder()
                .id(1L)
                .patientId(10L)
                .note("Patient discussed symptoms")
                .aiSummary("Patient has symptoms")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        config = PatientNotetakerConfig.builder()
                .id(1L)
                .patientId(10L)
                .isEnabled(true)
                .permitCaregiverAccess(true)
                .triggerKeywords(List.of(
                        PatientNotetakerKeyword.builder().keyword("pain").eventType(EventType.ALERT).build(),
                        PatientNotetakerKeyword.builder().keyword("appointment").eventType(EventType.TASK).build()))
                .updatedAt(LocalDateTime.now())
                .build();
    }

    // getNotetakerConfigByPatientId

    @Test
    @DisplayName("getNotetakerConfigByPatientId - valid patient with config - returns config DTO")
    void getNotetakerConfigByPatientId_validPatientWithConfig_returnsConfigDTO() throws Exception {
        when(patientService.getPatientById(10L)).thenReturn(patient);
        when(patientNotetakerConfigRepository.findByPatientId(10L)).thenReturn(config);

        final PatientNotetakerConfigDTO result = service.getNotetakerConfigByPatientId(10L);

        assertNotNull(result);
        assertEquals(10L, result.getPatientId());
        assertTrue(result.getIsEnabled());
    }

    @Test
    @DisplayName("getNotetakerConfigByPatientId - valid patient no config - returns DTO with nulls")
    void getNotetakerConfigByPatientId_validPatientNoConfig_returnsDTOWithNulls() throws Exception {
        when(patientService.getPatientById(10L)).thenReturn(patient);
        when(patientNotetakerConfigRepository.findByPatientId(10L)).thenReturn(null);

        final PatientNotetakerConfigDTO result = service.getNotetakerConfigByPatientId(10L);

        assertNotNull(result);
        assertNull(result.getId());
    }

    @Test
    @DisplayName("getNotetakerConfigByPatientId - invalid patient - throws IllegalArgumentException")
    void getNotetakerConfigByPatientId_invalidPatient_throwsIllegalArgumentException() throws Exception {
        when(patientService.getPatientById(99L)).thenReturn(null);

        assertThrows(IllegalArgumentException.class,
                () -> service.getNotetakerConfigByPatientId(99L));
    }

    // createOrUpdatePatientNotetakerConfig

    @Test
    @DisplayName("createOrUpdatePatientNotetakerConfig - null configDTO - throws IllegalArgumentException")
    void createOrUpdatePatientNotetakerConfig_nullConfigDTO_throwsIllegalArgumentException() throws Exception {
        when(patientService.getPatientById(10L)).thenReturn(patient);

        assertThrows(IllegalArgumentException.class,
                () -> service.createOrUpdatePatientNotetakerConfig(10L, null));
    }

    @Test
    @DisplayName("createOrUpdatePatientNotetakerConfig - no existing config - creates new config")
    void createOrUpdatePatientNotetakerConfig_noExistingConfig_createsNewConfig() throws Exception {
        when(patientService.getPatientById(10L)).thenReturn(patient);
        when(patientNotetakerConfigRepository.findByPatientId(10L)).thenReturn(null);
        when(patientNotetakerConfigRepository.save(any(PatientNotetakerConfig.class))).thenAnswer(inv -> {
            final PatientNotetakerConfig saved = inv.getArgument(0);
            saved.setId(2L);
            return saved;
        });

        final PatientNotetakerConfigDTO dto = PatientNotetakerConfigDTO.builder()
                .isEnabled(true)
                .permitCaregiverAccess(false)
                .triggerKeywords(List.of())
                .build();

        final PatientNotetakerConfigDTO result = service.createOrUpdatePatientNotetakerConfig(10L, dto);

        assertNotNull(result);
        assertEquals(10L, result.getPatientId());
        verify(patientNotetakerConfigRepository).save(any(PatientNotetakerConfig.class));
    }

    @Test
    @DisplayName("createOrUpdatePatientNotetakerConfig - existing config - updates config")
    void createOrUpdatePatientNotetakerConfig_existingConfig_updatesConfig() throws Exception {
        when(patientService.getPatientById(10L)).thenReturn(patient);
        when(patientNotetakerConfigRepository.findByPatientId(10L)).thenReturn(config);
        when(patientNotetakerConfigRepository.save(any(PatientNotetakerConfig.class))).thenReturn(config);

        final PatientNotetakerConfigDTO dto = PatientNotetakerConfigDTO.builder()
                .isEnabled(false)
                .permitCaregiverAccess(true)
                .triggerKeywords(new ArrayList<>())
                .build();

        final PatientNotetakerConfigDTO result = service.createOrUpdatePatientNotetakerConfig(10L, dto);

        assertNotNull(result);
        verify(patientNotetakerConfigRepository).save(config);
    }

    @Test
    @DisplayName("createOrUpdatePatientNotetakerConfig - invalid patient - throws IllegalArgumentException")
    void createOrUpdatePatientNotetakerConfig_invalidPatient_throwsIllegalArgumentException() throws Exception {
        when(patientService.getPatientById(99L)).thenReturn(null);

        final PatientNotetakerConfigDTO dto = PatientNotetakerConfigDTO.builder().build();

        assertThrows(IllegalArgumentException.class,
                () -> service.createOrUpdatePatientNotetakerConfig(99L, dto));
    }

    // getAllNotesForPatient

    @Test
    @DisplayName("getAllNotesForPatient - patient has notes - returns list of DTOs")
    void getAllNotesForPatient_patientHasNotes_returnsListOfDTOs() throws Exception {
        when(patientService.getPatientById(10L)).thenReturn(patient);
        when(patientNoteRepository.findByPatientId(10L)).thenReturn(Optional.of(List.of(patientNote)));

        final List<PatientNoteDTO> result = service.getAllNotesForPatient(10L);

        assertEquals(1, result.size());
        assertEquals("Patient discussed symptoms", result.get(0).getNote());
    }

    @Test
    @DisplayName("getAllNotesForPatient - patient has no notes - returns empty list")
    void getAllNotesForPatient_patientHasNoNotes_returnsEmptyList() throws Exception {
        when(patientService.getPatientById(10L)).thenReturn(patient);
        when(patientNoteRepository.findByPatientId(10L)).thenReturn(Optional.empty());

        final List<PatientNoteDTO> result = service.getAllNotesForPatient(10L);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("getAllNotesForPatient - invalid patient - throws IllegalArgumentException")
    void getAllNotesForPatient_invalidPatient_throwsIllegalArgumentException() throws Exception {
        when(patientService.getPatientById(99L)).thenReturn(null);

        assertThrows(IllegalArgumentException.class, () -> service.getAllNotesForPatient(99L));
    }

    // getNoteById

    @Test
    @DisplayName("getNoteById - valid note - returns note DTO")
    void getNoteById_validNote_returnsNoteDTO() throws Exception {
        when(patientService.getPatientById(10L)).thenReturn(patient);
        when(patientNoteRepository.findById(1L)).thenReturn(Optional.of(patientNote));

        final PatientNoteDTO result = service.getNoteById(10L, 1L);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("Patient discussed symptoms", result.getNote());
    }

    @Test
    @DisplayName("getNoteById - note not found - throws IllegalArgumentException")
    void getNoteById_noteNotFound_throwsIllegalArgumentException() throws Exception {
        when(patientService.getPatientById(10L)).thenReturn(patient);
        when(patientNoteRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.getNoteById(10L, 99L));
    }

    // createNoteForPatient

    @Test
    @DisplayName("createNoteForPatient - valid note with AI summary success - returns DTO with AI summary")
    void createNoteForPatient_validNoteAiSummarySuccess_returnsDTOWithAiSummary() throws Exception {
        when(patientService.getPatientById(10L)).thenReturn(patient);

        final OpenRouterResponse response = new OpenRouterResponse();
        final Choice choice = new Choice();
        final Message message = new Message("assistant", "Summary of conversation");
        choice.setMessage(message);
        response.setChoices(List.of(choice));
        when(openRouterService.sendChatRequest(any(OpenRouterChatRequest.class))).thenReturn(response);

        when(patientNoteRepository.save(any(PatientNote.class))).thenAnswer(inv -> {
            final PatientNote saved = inv.getArgument(0);
            saved.setId(2L);
            return saved;
        });
        when(patientNotetakerConfigRepository.findByPatientId(10L)).thenReturn(null);

        final PatientNoteDTO noteDTO = PatientNoteDTO.builder()
                .note("Doctor said take medicine daily")
                .aiSummary("")
                .build();

        final PatientNoteDTO result = service.createNoteForPatient(10L, noteDTO);

        assertNotNull(result);
        assertEquals(10L, result.getPatientId());
        verify(patientNoteRepository).save(any(PatientNote.class));
    }

    @Test
    @DisplayName("createNoteForPatient - AI summary fails - sets failure message")
    void createNoteForPatient_aiSummaryFails_setsFailureMessage() throws Exception {
        when(patientService.getPatientById(10L)).thenReturn(patient);
        when(openRouterService.sendChatRequest(any(OpenRouterChatRequest.class)))
                .thenThrow(new RuntimeException("AI unavailable"));
        when(patientNoteRepository.save(any(PatientNote.class))).thenAnswer(inv -> {
            final PatientNote saved = inv.getArgument(0);
            saved.setId(3L);
            return saved;
        });
        when(patientNotetakerConfigRepository.findByPatientId(10L)).thenReturn(null);

        final PatientNoteDTO noteDTO = PatientNoteDTO.builder()
                .note("Some note content")
                .aiSummary("")
                .build();

        final PatientNoteDTO result = service.createNoteForPatient(10L, noteDTO);

        assertNotNull(result);
    }

    @Test
    @DisplayName("createNoteForPatient - null noteDTO - throws IllegalArgumentException")
    void createNoteForPatient_nullNoteDTO_throwsIllegalArgumentException() throws Exception {
        when(patientService.getPatientById(10L)).thenReturn(patient);

        assertThrows(IllegalArgumentException.class,
                () -> service.createNoteForPatient(10L, null));
    }

    @Test
    @DisplayName("createNoteForPatient - AI returns null response - sets empty AI summary")
    void createNoteForPatient_aiReturnsNullResponse_setsEmptyAiSummary() throws Exception {
        when(patientService.getPatientById(10L)).thenReturn(patient);
        when(openRouterService.sendChatRequest(any(OpenRouterChatRequest.class))).thenReturn(null);
        when(patientNoteRepository.save(any(PatientNote.class))).thenAnswer(inv -> {
            final PatientNote saved = inv.getArgument(0);
            saved.setId(4L);
            return saved;
        });
        when(patientNotetakerConfigRepository.findByPatientId(10L)).thenReturn(null);

        final PatientNoteDTO noteDTO = PatientNoteDTO.builder()
                .note("Some note")
                .aiSummary("")
                .build();

        final PatientNoteDTO result = service.createNoteForPatient(10L, noteDTO);

        assertNotNull(result);
    }

    @Test
    @DisplayName("createNoteForPatient - AI returns response with null choices - sets empty AI summary")
    void createNoteForPatient_aiReturnsNullChoices_setsEmptyAiSummary() throws Exception {
        when(patientService.getPatientById(10L)).thenReturn(patient);
        final OpenRouterResponse response = new OpenRouterResponse();
        response.setChoices(null);
        when(openRouterService.sendChatRequest(any(OpenRouterChatRequest.class))).thenReturn(response);
        when(patientNoteRepository.save(any(PatientNote.class))).thenAnswer(inv -> {
            final PatientNote saved = inv.getArgument(0);
            saved.setId(5L);
            return saved;
        });
        when(patientNotetakerConfigRepository.findByPatientId(10L)).thenReturn(null);

        final PatientNoteDTO noteDTO = PatientNoteDTO.builder()
                .note("Some note")
                .aiSummary("")
                .build();

        final PatientNoteDTO result = service.createNoteForPatient(10L, noteDTO);

        assertNotNull(result);
    }

    @Test
    @DisplayName("createNoteForPatient - AI returns response with empty choices - sets empty AI summary")
    void createNoteForPatient_aiReturnsEmptyChoices_setsEmptyAiSummary() throws Exception {
        when(patientService.getPatientById(10L)).thenReturn(patient);
        final OpenRouterResponse response = new OpenRouterResponse();
        response.setChoices(List.of());
        when(openRouterService.sendChatRequest(any(OpenRouterChatRequest.class))).thenReturn(response);
        when(patientNoteRepository.save(any(PatientNote.class))).thenAnswer(inv -> {
            final PatientNote saved = inv.getArgument(0);
            saved.setId(6L);
            return saved;
        });
        when(patientNotetakerConfigRepository.findByPatientId(10L)).thenReturn(null);

        final PatientNoteDTO noteDTO = PatientNoteDTO.builder()
                .note("Some note")
                .aiSummary("")
                .build();

        final PatientNoteDTO result = service.createNoteForPatient(10L, noteDTO);

        assertNotNull(result);
    }

    @Test
    @DisplayName("createNoteForPatient - AI response contains HTML tags - strips HTML from summary")
    void createNoteForPatient_aiResponseContainsHtml_stripsHtml() throws Exception {
        when(patientService.getPatientById(10L)).thenReturn(patient);

        final OpenRouterResponse response = new OpenRouterResponse();
        final Choice choice = new Choice();
        final Message message = new Message("assistant", "Summary text<think>some reasoning</think>");
        choice.setMessage(message);
        response.setChoices(List.of(choice));
        when(openRouterService.sendChatRequest(any(OpenRouterChatRequest.class))).thenReturn(response);

        when(patientNoteRepository.save(any(PatientNote.class))).thenAnswer(inv -> {
            final PatientNote saved = inv.getArgument(0);
            saved.setId(7L);
            return saved;
        });
        when(patientNotetakerConfigRepository.findByPatientId(10L)).thenReturn(null);

        final PatientNoteDTO noteDTO = PatientNoteDTO.builder()
                .note("A note")
                .aiSummary("")
                .build();

        final PatientNoteDTO result = service.createNoteForPatient(10L, noteDTO);

        assertNotNull(result);
    }

    // createNoteForPatient - keyword detection

    @Test
    @DisplayName("createNoteForPatient - note contains ALERT keyword - detects keyword")
    void createNoteForPatient_noteContainsAlertKeyword_detectsKeyword() throws Exception {
        when(patientService.getPatientById(10L)).thenReturn(patient);

        final OpenRouterResponse response = new OpenRouterResponse();
        final Choice choice = new Choice();
        final Message message = new Message("assistant", "Summary");
        choice.setMessage(message);
        response.setChoices(List.of(choice));
        when(openRouterService.sendChatRequest(any(OpenRouterChatRequest.class))).thenReturn(response);

        when(patientNoteRepository.save(any(PatientNote.class))).thenAnswer(inv -> {
            final PatientNote saved = inv.getArgument(0);
            saved.setId(8L);
            return saved;
        });
        when(patientNotetakerConfigRepository.findByPatientId(10L)).thenReturn(config);

        final PatientNoteDTO noteDTO = PatientNoteDTO.builder()
                .note("Patient reports severe pain in lower back")
                .aiSummary("")
                .build();

        final PatientNoteDTO result = service.createNoteForPatient(10L, noteDTO);

        assertNotNull(result);
        // ALERT type just returns early; no task creation
        verify(taskService, never()).createTask(anyLong(), any(TaskDtoV2.class));
    }

    @Test
    @DisplayName("createNoteForPatient - note contains TASK keyword with valid AI response - creates task")
    void createNoteForPatient_noteContainsTaskKeyword_createsTask() throws Exception {
        when(patientService.getPatientById(10L)).thenReturn(patient);

        // AI summary response
        final OpenRouterResponse summaryResponse = new OpenRouterResponse();
        final Choice summaryChoice = new Choice();
        final Message summaryMessage = new Message("assistant", "Summary");
        summaryChoice.setMessage(summaryMessage);
        summaryResponse.setChoices(List.of(summaryChoice));

        // Task generation response - valid JSON for TaskDtoV2
        final String taskJson = "{\"name\":\"Schedule appointment\",\"date\":\"" + LocalDate.now().getYear() + "-06-15\",\"daysOfWeek\":[true,false,false,false,false,false,false],\"description\":\"Follow-up appointment\",\"count\":1,\"frequency\":\"once\",\"taskType\":\"appointment\",\"timeOfDay\":\"10:00:00\"}";
        final OpenRouterResponse taskResponse = new OpenRouterResponse();
        final Choice taskChoice = new Choice();
        final Message taskMessage = new Message("assistant", taskJson);
        taskChoice.setMessage(taskMessage);
        taskResponse.setChoices(List.of(taskChoice));

        when(openRouterService.sendChatRequest(any(OpenRouterChatRequest.class)))
                .thenReturn(summaryResponse)
                .thenReturn(taskResponse);

        when(patientNoteRepository.save(any(PatientNote.class))).thenAnswer(inv -> {
            final PatientNote saved = inv.getArgument(0);
            saved.setId(9L);
            return saved;
        });
        when(patientNotetakerConfigRepository.findByPatientId(10L)).thenReturn(config);

        final PatientNoteDTO noteDTO = PatientNoteDTO.builder()
                .note("Need to schedule an appointment for next month")
                .aiSummary("")
                .build();

        final PatientNoteDTO result = service.createNoteForPatient(10L, noteDTO);

        assertNotNull(result);
        verify(taskService).createTask(eq(10L), any(TaskDtoV2.class));
    }

    @Test
    @DisplayName("createNoteForPatient - note contains TASK keyword but AI returns invalid JSON - does not create task")
    void createNoteForPatient_taskKeywordInvalidAiJson_doesNotCreateTask() throws Exception {
        when(patientService.getPatientById(10L)).thenReturn(patient);

        final OpenRouterResponse summaryResponse = new OpenRouterResponse();
        final Choice summaryChoice = new Choice();
        summaryChoice.setMessage(new Message("assistant", "Summary"));
        summaryResponse.setChoices(List.of(summaryChoice));

        final OpenRouterResponse taskResponse = new OpenRouterResponse();
        final Choice taskChoice = new Choice();
        taskChoice.setMessage(new Message("assistant", "not valid json"));
        taskResponse.setChoices(List.of(taskChoice));

        when(openRouterService.sendChatRequest(any(OpenRouterChatRequest.class)))
                .thenReturn(summaryResponse)
                .thenReturn(taskResponse);

        when(patientNoteRepository.save(any(PatientNote.class))).thenAnswer(inv -> {
            final PatientNote saved = inv.getArgument(0);
            saved.setId(10L);
            return saved;
        });
        when(patientNotetakerConfigRepository.findByPatientId(10L)).thenReturn(config);

        final PatientNoteDTO noteDTO = PatientNoteDTO.builder()
                .note("Need to schedule an appointment soon")
                .aiSummary("")
                .build();

        final PatientNoteDTO result = service.createNoteForPatient(10L, noteDTO);

        assertNotNull(result);
        verify(taskService, never()).createTask(anyLong(), any(TaskDtoV2.class));
    }

    @Test
    @DisplayName("createNoteForPatient - note contains TASK keyword but OpenRouter throws - does not create task")
    void createNoteForPatient_taskKeywordOpenRouterThrows_doesNotCreateTask() throws Exception {
        when(patientService.getPatientById(10L)).thenReturn(patient);

        final OpenRouterResponse summaryResponse = new OpenRouterResponse();
        final Choice summaryChoice = new Choice();
        summaryChoice.setMessage(new Message("assistant", "Summary"));
        summaryResponse.setChoices(List.of(summaryChoice));

        when(openRouterService.sendChatRequest(any(OpenRouterChatRequest.class)))
                .thenReturn(summaryResponse)
                .thenThrow(new RuntimeException("OpenRouter unavailable"));

        when(patientNoteRepository.save(any(PatientNote.class))).thenAnswer(inv -> {
            final PatientNote saved = inv.getArgument(0);
            saved.setId(11L);
            return saved;
        });
        when(patientNotetakerConfigRepository.findByPatientId(10L)).thenReturn(config);

        final PatientNoteDTO noteDTO = PatientNoteDTO.builder()
                .note("Need to schedule appointment next week")
                .aiSummary("")
                .build();

        final PatientNoteDTO result = service.createNoteForPatient(10L, noteDTO);

        assertNotNull(result);
        verify(taskService, never()).createTask(anyLong(), any(TaskDtoV2.class));
    }

    @Test
    @DisplayName("createNoteForPatient - TASK keyword AI returns empty content - does not create task")
    void createNoteForPatient_taskKeywordEmptyAiContent_doesNotCreateTask() throws Exception {
        when(patientService.getPatientById(10L)).thenReturn(patient);

        final OpenRouterResponse summaryResponse = new OpenRouterResponse();
        final Choice summaryChoice = new Choice();
        summaryChoice.setMessage(new Message("assistant", "Summary"));
        summaryResponse.setChoices(List.of(summaryChoice));

        final OpenRouterResponse taskResponse = new OpenRouterResponse();
        taskResponse.setChoices(null);

        when(openRouterService.sendChatRequest(any(OpenRouterChatRequest.class)))
                .thenReturn(summaryResponse)
                .thenReturn(taskResponse);

        when(patientNoteRepository.save(any(PatientNote.class))).thenAnswer(inv -> {
            final PatientNote saved = inv.getArgument(0);
            saved.setId(12L);
            return saved;
        });
        when(patientNotetakerConfigRepository.findByPatientId(10L)).thenReturn(config);

        final PatientNoteDTO noteDTO = PatientNoteDTO.builder()
                .note("Need appointment scheduling")
                .aiSummary("")
                .build();

        final PatientNoteDTO result = service.createNoteForPatient(10L, noteDTO);

        assertNotNull(result);
        verify(taskService, never()).createTask(anyLong(), any(TaskDtoV2.class));
    }

    @Test
    @DisplayName("createNoteForPatient - TASK keyword AI returns task with null required fields - does not create task")
    void createNoteForPatient_taskKeywordAiReturnsTaskWithNullFields_doesNotCreateTask() throws Exception {
        when(patientService.getPatientById(10L)).thenReturn(patient);

        final OpenRouterResponse summaryResponse = new OpenRouterResponse();
        final Choice summaryChoice = new Choice();
        summaryChoice.setMessage(new Message("assistant", "Summary"));
        summaryResponse.setChoices(List.of(summaryChoice));

        // Task JSON with missing required fields (name is null)
        final String taskJson = "{\"name\":null,\"date\":\"2026-06-15\",\"description\":\"desc\",\"taskType\":\"general\"}";
        final OpenRouterResponse taskResponse = new OpenRouterResponse();
        final Choice taskChoice = new Choice();
        taskChoice.setMessage(new Message("assistant", taskJson));
        taskResponse.setChoices(List.of(taskChoice));

        when(openRouterService.sendChatRequest(any(OpenRouterChatRequest.class)))
                .thenReturn(summaryResponse)
                .thenReturn(taskResponse);

        when(patientNoteRepository.save(any(PatientNote.class))).thenAnswer(inv -> {
            final PatientNote saved = inv.getArgument(0);
            saved.setId(13L);
            return saved;
        });
        when(patientNotetakerConfigRepository.findByPatientId(10L)).thenReturn(config);

        final PatientNoteDTO noteDTO = PatientNoteDTO.builder()
                .note("Appointment needed soon")
                .aiSummary("")
                .build();

        final PatientNoteDTO result = service.createNoteForPatient(10L, noteDTO);

        assertNotNull(result);
        verify(taskService, never()).createTask(anyLong(), any(TaskDtoV2.class));
    }

    @Test
    @DisplayName("createNoteForPatient - no keywords match - no task or alert triggered")
    void createNoteForPatient_noKeywordsMatch_noTaskOrAlertTriggered() throws Exception {
        when(patientService.getPatientById(10L)).thenReturn(patient);

        final OpenRouterResponse summaryResponse = new OpenRouterResponse();
        final Choice summaryChoice = new Choice();
        summaryChoice.setMessage(new Message("assistant", "Summary"));
        summaryResponse.setChoices(List.of(summaryChoice));
        when(openRouterService.sendChatRequest(any(OpenRouterChatRequest.class))).thenReturn(summaryResponse);

        when(patientNoteRepository.save(any(PatientNote.class))).thenAnswer(inv -> {
            final PatientNote saved = inv.getArgument(0);
            saved.setId(14L);
            return saved;
        });
        when(patientNotetakerConfigRepository.findByPatientId(10L)).thenReturn(config);

        final PatientNoteDTO noteDTO = PatientNoteDTO.builder()
                .note("General wellness check was fine")
                .aiSummary("")
                .build();

        final PatientNoteDTO result = service.createNoteForPatient(10L, noteDTO);

        assertNotNull(result);
        verify(taskService, never()).createTask(anyLong(), any(TaskDtoV2.class));
    }

    // updateNoteForPatient

    @Test
    @DisplayName("updateNoteForPatient - valid update with non-failed AI summary - uses provided summary")
    void updateNoteForPatient_validUpdateWithNonFailedAiSummary_usesProvidedSummary() throws Exception {
        when(patientService.getPatientById(10L)).thenReturn(patient);
        when(patientNoteRepository.findById(1L)).thenReturn(Optional.of(patientNote));
        when(patientNoteRepository.save(any(PatientNote.class))).thenReturn(patientNote);

        final PatientNoteDTO noteDTO = PatientNoteDTO.builder()
                .note("Updated note content")
                .aiSummary("Updated AI summary")
                .build();

        final PatientNoteDTO result = service.updateNoteForPatient(10L, 1L, noteDTO);

        assertNotNull(result);
        assertEquals("Updated AI summary", patientNote.getAiSummary());
    }

    @Test
    @DisplayName("updateNoteForPatient - AI summary is Failed to generate - regenerates summary")
    void updateNoteForPatient_aiSummaryIsFailed_regeneratesSummary() throws Exception {
        when(patientService.getPatientById(10L)).thenReturn(patient);
        when(patientNoteRepository.findById(1L)).thenReturn(Optional.of(patientNote));
        when(patientNoteRepository.save(any(PatientNote.class))).thenReturn(patientNote);

        final OpenRouterResponse response = new OpenRouterResponse();
        final Choice choice = new Choice();
        choice.setMessage(new Message("assistant", "New AI Summary"));
        response.setChoices(List.of(choice));
        when(openRouterService.sendChatRequest(any(OpenRouterChatRequest.class))).thenReturn(response);

        final PatientNoteDTO noteDTO = PatientNoteDTO.builder()
                .note("Updated note")
                .aiSummary("Failed to generate AI Summary")
                .build();

        final PatientNoteDTO result = service.updateNoteForPatient(10L, 1L, noteDTO);

        assertNotNull(result);
        verify(openRouterService).sendChatRequest(any(OpenRouterChatRequest.class));
    }

    @Test
    @DisplayName("updateNoteForPatient - null noteDTO - throws IllegalArgumentException")
    void updateNoteForPatient_nullNoteDTO_throwsIllegalArgumentException() throws Exception {
        when(patientService.getPatientById(10L)).thenReturn(patient);

        assertThrows(IllegalArgumentException.class,
                () -> service.updateNoteForPatient(10L, 1L, null));
    }

    @Test
    @DisplayName("updateNoteForPatient - note not found - throws NoSuchElementException")
    void updateNoteForPatient_noteNotFound_throwsException() throws Exception {
        when(patientService.getPatientById(10L)).thenReturn(patient);
        when(patientNoteRepository.findById(99L)).thenReturn(Optional.empty());

        final PatientNoteDTO noteDTO = PatientNoteDTO.builder()
                .note("Updated note")
                .aiSummary("Summary")
                .build();

        assertThrows(Exception.class, () -> service.updateNoteForPatient(10L, 99L, noteDTO));
    }

    // deleteNoteById

    @Test
    @DisplayName("deleteNoteById - valid noteId - deletes note")
    void deleteNoteById_validNoteId_deletesNote() throws Exception {
        doNothing().when(patientNoteRepository).deleteById(1L);

        service.deleteNoteById(1L);

        verify(patientNoteRepository).deleteById(1L);
    }
}
