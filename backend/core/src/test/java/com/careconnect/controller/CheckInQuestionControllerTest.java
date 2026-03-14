package com.careconnect.controller;

import com.careconnect.dto.QuestionDTO;
import com.careconnect.service.QuestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class CheckInQuestionControllerTest {

    private MockMvc mockMvc;

    @Mock
    private QuestionService questionService;
    /*
     * Mockito isolates controller logic from service layer.
     * We control the returned data and verify interaction behavior.
     */

    @InjectMocks
    private CheckInQuestionController controller;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .build();
        /*
         * standaloneSetup keeps this a true unit test.
         * No Spring Boot context startup → faster execution.
         */
    }

    @Test
    void getQuestions_shouldReturnQuestions_fromPrimaryPath() throws Exception {
        final Long checkInId = 1L;

        final List<QuestionDTO> mockQuestions = List.of(
                new QuestionDTO(1L, "Question 1", "TEXT", true, true, 1),
                new QuestionDTO(2L, "Question 2", "TEXT", false, true, 2)
        );
        /*
        * Because QuestionDTO is a record, Jackson serializes
        * exactly using the component names:
        * id, prompt, type, required, active, ordinal
        */

        when(questionService.findActiveOrdered())
                .thenReturn(mockQuestions);

        mockMvc.perform(get("/api/checkins/{checkInId}/questions", checkInId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1L))
                .andExpect(jsonPath("$[0].prompt").value("Question 1"))
                .andExpect(jsonPath("$[0].required").value(true))
                .andExpect(jsonPath("$[0].ordinal").value(1));
        /*
        * We assert key fields to confirm:
        * - Proper serialization
        * - Correct field naming from record
        * - Correct array structure
        */

        verify(questionService, times(1)).findActiveOrdered();
    }

    @Test
    void getQuestions_shouldReturnQuestions_fromVersionedPath() throws Exception {

        when(questionService.findActiveOrdered())
                .thenReturn(List.of(
                        new QuestionDTO(10L, "Versioned", "TEXT", true, false, 1)
                ));

        mockMvc.perform(get("/v1/api/checkins/{checkInId}/questions", 99L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(10L));

        /*
         * Confirms alternate base path mapping works
         * and serialization remains correct.
         */

        verify(questionService).findActiveOrdered();
    }

    @Test
    void getQuestions_shouldReturnEmptyList_whenServiceReturnsEmpty() throws Exception {

        when(questionService.findActiveOrdered())
                .thenReturn(List.of());

        mockMvc.perform(get("/api/checkins/{checkInId}/questions", 5L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        /*
         * Edge case:
         * Even with no questions, API returns 200
         * and an empty JSON array (not null).
         */

        verify(questionService).findActiveOrdered();
    }
}