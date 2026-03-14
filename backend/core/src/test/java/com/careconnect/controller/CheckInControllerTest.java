package com.careconnect.controller;

import com.careconnect.model.CheckIn;
import com.careconnect.security.AuthorizationService;
import com.careconnect.service.CheckInService;
import com.careconnect.util.SecurityUtil;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class CheckInControllerTest {

    private MockMvc mockMvc;

    @Mock
    private CheckInService checkInService;
    // Mockito is used to isolate controller logic from service layer behavior.
    @Mock
    private SecurityUtil securityUtil;
    @Mock
    private AuthorizationService authorizationService;

    @InjectMocks
    private CheckInController checkInController;
    // Injects mocked service into controller.

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders
                .standaloneSetup(checkInController)
                .build();
        // standaloneSetup avoids loading full Spring context → faster unit tests.
    }

    @Test
    void patientCheckIn_shouldReturnOk() throws Exception {
        // Tests POST endpoint returns 200 and a body.
        mockMvc.perform(post("/v1/checkins")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    void getCheckIns_shouldReturnListFromService() throws Exception {
        // Ensures controller calls service and returns its result.
        when(checkInService.getAllCheckIns())
                .thenReturn(List.of(new CheckIn(), new CheckIn()));

        mockMvc.perform(get("/v1/checkins"))
                .andExpect(status().isOk());

        verify(checkInService, times(1)).getAllCheckIns();
        // Verifies service interaction.
    }

    @Test
    void getCheckIn_shouldReturnCheckInById() throws Exception {
        final Long id = 1L;
        when(checkInService.getCheckInByID(id))
                .thenReturn(new CheckIn());

        mockMvc.perform(get("/v1/checkins/{id}", id))
                .andExpect(status().isOk());

        verify(checkInService).getCheckInByID(id);
        // Confirms correct ID is passed to service.
    }

    @Test
    void updateCheckIn_shouldReturnOk() throws Exception {
        final Long id = 1L;

        mockMvc.perform(put("/v1/checkins/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        // Currently placeholder logic → just validates HTTP contract.
    }
}