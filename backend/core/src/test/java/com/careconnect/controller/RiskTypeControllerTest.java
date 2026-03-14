package com.careconnect.controller;

import com.careconnect.model.RiskType;
import com.careconnect.repository.UserRepository;
import com.careconnect.service.PatientRiskService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RiskTypeController.class)
@DisplayName("RiskTypeController – GET /v1/api/risk-types")
class RiskTypeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PatientRiskService patientRiskService;

    // RiskTypeController does not inject UserRepository directly but Spring Security
    // context resolution may need it; include to avoid context startup failure.
    @MockitoBean
    private UserRepository userRepository;

    @Test
    @WithMockUser
    @DisplayName("returns 200 with all risk types")
    void getAllRiskTypes_returns200() throws Exception {
        RiskType r1 = new RiskType(1L, "Fall with Injury");
        RiskType r2 = new RiskType(2L, "Elopement");
        RiskType r3 = new RiskType(3L, "Seizures");
        RiskType r4 = new RiskType(4L, "Self-Harm");
        RiskType r5 = new RiskType(5L, "Aspiration Pneumonia");

        when(patientRiskService.getAllRiskTypes()).thenReturn(List.of(r1, r2, r3, r4, r5));

        mockMvc.perform(get("/v1/api/risk-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[0].name").value("Fall with Injury"))
                .andExpect(jsonPath("$[1].name").value("Elopement"))
                .andExpect(jsonPath("$[4].name").value("Aspiration Pneumonia"));
    }

    @Test
    @WithMockUser
    @DisplayName("returns 200 with empty list when no risk types exist")
    void noRiskTypes_returnsEmptyList() throws Exception {
        when(patientRiskService.getAllRiskTypes()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/v1/api/risk-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @WithMockUser
    @DisplayName("returns 200 with correct risk type id and name fields")
    void riskTypeFields_areCorrect() throws Exception {
        RiskType rt = new RiskType(10L, "Aspiration Pneumonia");
        when(patientRiskService.getAllRiskTypes()).thenReturn(List.of(rt));

        mockMvc.perform(get("/v1/api/risk-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].name").value("Aspiration Pneumonia"));
    }

    @Test
    @DisplayName("redirects unauthenticated request (302 to login)")
    void unauthenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/v1/api/risk-types"))
                .andExpect(status().is3xxRedirection());
    }
}
