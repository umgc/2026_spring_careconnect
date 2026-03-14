package com.careconnect.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CustomErrorControllerTest {

    private MockMvc mockMvc;
    private CustomErrorController controller;

    @BeforeEach
    void setUp() throws Exception {
        controller = new CustomErrorController();

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .build();
        /*
         * standaloneSetup is sufficient because:
         * - No dependencies to mock
         * - No Spring Security involved
         * - We only test request mapping + view resolution
         *
         * This keeps the test lightweight and fast.
         */
    }

    @Test
    void handleError_shouldReturnErrorPageView() throws Exception {

        mockMvc.perform(get("/error"))
                .andExpect(status().isOk())
                .andExpect(view().name("errorPage"));
        /*
         * Validates:
         * - "/error" is correctly mapped
         * - HTTP 200 is returned
         * - The correct logical view name ("errorPage") is returned
         *
         * Since this is a @Controller (not @RestController),
         * the return value represents a view name.
         */
    }

    @Test
    void getErrorPath_shouldReturnErrorPath() throws Exception {

        final String path = controller.getErrorPath();

        assertEquals("/error", path);
        /*
         * Verifies the explicit error path definition.
         * Although Spring Boot no longer requires overriding
         * getErrorPath() in newer versions, this test ensures
         * backward compatibility behavior.
         */
    }
}