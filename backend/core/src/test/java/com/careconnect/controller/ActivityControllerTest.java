package com.careconnect.controller;

import com.careconnect.model.User;
import com.careconnect.repository.UserRepository;
import com.careconnect.security.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ActivityController.class)
@DisplayName("ActivityController – GET /v1/api/activities")
class ActivityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;

    private User caregiverUser;

    @BeforeEach
    void setUp() {
        caregiverUser = new User();
        caregiverUser.setId(1L);
        caregiverUser.setEmail("caregiver@test.com");
        caregiverUser.setRole(Role.CAREGIVER);
        when(userRepository.findByEmail("caregiver@test.com")).thenReturn(Optional.of(caregiverUser));
    }

    @Test
    @WithMockUser(username = "caregiver@test.com")
    @DisplayName("returns 200 with empty list (current stub implementation)")
    void getActivities_returnsEmptyList() throws Exception {
        mockMvc.perform(get("/v1/api/activities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @WithMockUser(username = "caregiver@test.com")
    @DisplayName("returns 200 with category=ADL filter (current stub returns empty)")
    void getActivities_withAdlCategory_returnsOk() throws Exception {
        mockMvc.perform(get("/v1/api/activities").param("category", "ADL"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"));
    }

    @Test
    @WithMockUser(username = "caregiver@test.com")
    @DisplayName("returns 200 with category=IADL filter (current stub returns empty)")
    void getActivities_withIadlCategory_returnsOk() throws Exception {
        mockMvc.perform(get("/v1/api/activities").param("category", "IADL"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"));
    }

    @Test
    @DisplayName("redirects unauthenticated request (302 to login)")
    void unauthenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/v1/api/activities"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser(username = "unknown@test.com")
    @DisplayName("returns 401 when user is not found in repository")
    void unknownUser_returns401() throws Exception {
        when(userRepository.findByEmail("unknown@test.com")).thenReturn(Optional.empty());

        mockMvc.perform(get("/v1/api/activities"))
                .andExpect(status().isUnauthorized());
    }
}
