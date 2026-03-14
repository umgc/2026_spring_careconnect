package com.careconnect.service;

import com.careconnect.dto.AiAllergyDTO;
import com.careconnect.model.Allergy;
import com.careconnect.service.DeepSeekService.Choice;
import com.careconnect.service.DeepSeekService.DeepSeekChatRequest;
import com.careconnect.service.DeepSeekService.DeepSeekResponse;
import com.careconnect.service.DeepSeekService.Message;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class AiAllergyServiceTest {

    @Mock
    private DeepSeekService deepSeekService;

    @Mock
    private DeepSeekContextBuilder contextBuilder;

    @InjectMocks
    private AiAllergyService aiAllergyService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        // Inject the real ObjectMapper since @InjectMocks won't do it for final fields
        try {
            var field = AiAllergyService.class.getDeclaredField("objectMapper");
            field.setAccessible(true);
            field.set(aiAllergyService, objectMapper);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private DeepSeekResponse buildResponse(String content) {
        Message msg = new Message();
        msg.setRole("assistant");
        msg.setContent(content);

        Choice choice = new Choice();
        choice.setMessage(msg);

        DeepSeekResponse resp = new DeepSeekResponse();
        resp.setChoices(List.of(choice));
        return resp;
    }

    private AiAllergyDTO.Request buildRequest(Long patientId, String text, Map<String, Object> context) {
        AiAllergyDTO.Request req = new AiAllergyDTO.Request();
        req.setPatientId(patientId);
        req.setText(text);
        req.setContext(context);
        return req;
    }

    // ── Valid JSON response ──

    @Test
    @DisplayName("analyze_validJsonResponse_returnsPopulatedResult")
    void analyze_validJsonResponse_returnsPopulatedResult() throws Exception {
        String json = "{\"allergen\":\"Penicillin\",\"reaction\":\"Hives\",\"severity\":\"SEVERE\"}";

        when(contextBuilder.buildAllergyContext(any(), any())).thenReturn("No known allergies.");
        when(deepSeekService.buildChatRequest(anyString(), anyString())).thenReturn(new DeepSeekChatRequest());
        when(deepSeekService.sendChatRequest(any())).thenReturn(buildResponse(json));

        AiAllergyDTO.Request req = buildRequest(1L, "I am allergic to penicillin", null);
        AiAllergyDTO.Result result = aiAllergyService.analyze(req, Collections.emptyList());

        assertEquals("Penicillin", result.getAllergen());
        assertEquals("Hives", result.getReaction());
        assertEquals("SEVERE", result.getSeverity());
    }

    @Test
    @DisplayName("analyze_mildSeverityInJson_normalizesMild")
    void analyze_mildSeverityInJson_normalizesMild() throws Exception {
        String json = "{\"allergen\":\"Dust\",\"reaction\":\"Sneezing\",\"severity\":\"mild\"}";

        when(contextBuilder.buildAllergyContext(any(), any())).thenReturn("ctx");
        when(deepSeekService.buildChatRequest(anyString(), anyString())).thenReturn(new DeepSeekChatRequest());
        when(deepSeekService.sendChatRequest(any())).thenReturn(buildResponse(json));

        AiAllergyDTO.Request req = buildRequest(1L, "dust allergy", Map.of("hint", "sneezing"));
        AiAllergyDTO.Result result = aiAllergyService.analyze(req, Collections.emptyList());

        assertEquals("Dust", result.getAllergen());
        assertEquals("MILD", result.getSeverity());
    }

    @Test
    @DisplayName("analyze_moderateSeverityInJson_normalizesModerate")
    void analyze_moderateSeverityInJson_normalizesModerate() throws Exception {
        String json = "{\"allergen\":\"Shellfish\",\"reaction\":\"Swelling\",\"severity\":\"MODERATE\"}";

        when(contextBuilder.buildAllergyContext(any(), any())).thenReturn("ctx");
        when(deepSeekService.buildChatRequest(anyString(), anyString())).thenReturn(new DeepSeekChatRequest());
        when(deepSeekService.sendChatRequest(any())).thenReturn(buildResponse(json));

        AiAllergyDTO.Request req = buildRequest(1L, "shellfish", null);
        AiAllergyDTO.Result result = aiAllergyService.analyze(req, Collections.emptyList());

        assertEquals("MODERATE", result.getSeverity());
    }

    // ── Non-JSON content (not blank) ──

    @Test
    @DisplayName("analyze_nonJsonContent_fallsBackToTranscript")
    void analyze_nonJsonContent_fallsBackToTranscript() throws Exception {
        when(contextBuilder.buildAllergyContext(any(), any())).thenReturn("ctx");
        when(deepSeekService.buildChatRequest(anyString(), anyString())).thenReturn(new DeepSeekChatRequest());
        when(deepSeekService.sendChatRequest(any())).thenReturn(buildResponse("I'm not sure about the allergy"));

        AiAllergyDTO.Request req = buildRequest(1L, "penicillin allergy", null);
        AiAllergyDTO.Result result = aiAllergyService.analyze(req, Collections.emptyList());

        assertEquals("", result.getAllergen());
        assertEquals("penicillin allergy", result.getReaction());
        assertEquals("", result.getSeverity());
    }

    // ── Empty content ──

    @Test
    @DisplayName("analyze_emptyContent_fallsBackToTranscript")
    void analyze_emptyContent_fallsBackToTranscript() throws Exception {
        when(contextBuilder.buildAllergyContext(any(), any())).thenReturn("ctx");
        when(deepSeekService.buildChatRequest(anyString(), anyString())).thenReturn(new DeepSeekChatRequest());
        when(deepSeekService.sendChatRequest(any())).thenReturn(buildResponse(""));

        AiAllergyDTO.Request req = buildRequest(1L, "my transcript", null);
        AiAllergyDTO.Result result = aiAllergyService.analyze(req, Collections.emptyList());

        assertEquals("", result.getAllergen());
        assertEquals("my transcript", result.getReaction());
    }

    @Test
    @DisplayName("analyze_nullText_fallsBackToEmptyString")
    void analyze_nullText_fallsBackToEmptyString() throws Exception {
        when(contextBuilder.buildAllergyContext(any(), any())).thenReturn("ctx");
        when(deepSeekService.buildChatRequest(anyString(), anyString())).thenReturn(new DeepSeekChatRequest());
        when(deepSeekService.sendChatRequest(any())).thenReturn(buildResponse(""));

        AiAllergyDTO.Request req = buildRequest(1L, null, null);
        AiAllergyDTO.Result result = aiAllergyService.analyze(req, Collections.emptyList());

        assertEquals("", result.getReaction());
    }

    @Test
    @DisplayName("analyze_nullTextWithNonJsonContent_fallsBackToEmptyString")
    void analyze_nullTextWithNonJsonContent_fallsBackToEmptyString() throws Exception {
        when(contextBuilder.buildAllergyContext(any(), any())).thenReturn("ctx");
        when(deepSeekService.buildChatRequest(anyString(), anyString())).thenReturn(new DeepSeekChatRequest());
        when(deepSeekService.sendChatRequest(any())).thenReturn(buildResponse("some non-json content"));

        AiAllergyDTO.Request req = buildRequest(1L, null, null);
        AiAllergyDTO.Result result = aiAllergyService.analyze(req, Collections.emptyList());

        assertEquals("", result.getReaction());
    }

    // ── JSON with missing fields ──

    @Test
    @DisplayName("analyze_jsonMissingFields_returnsEmptyStrings")
    void analyze_jsonMissingFields_returnsEmptyStrings() throws Exception {
        String json = "{\"allergen\":\"Pollen\"}";

        when(contextBuilder.buildAllergyContext(any(), any())).thenReturn("ctx");
        when(deepSeekService.buildChatRequest(anyString(), anyString())).thenReturn(new DeepSeekChatRequest());
        when(deepSeekService.sendChatRequest(any())).thenReturn(buildResponse(json));

        AiAllergyDTO.Request req = buildRequest(1L, "pollen", null);
        AiAllergyDTO.Result result = aiAllergyService.analyze(req, Collections.emptyList());

        assertEquals("Pollen", result.getAllergen());
        assertEquals("", result.getReaction());
        assertEquals("", result.getSeverity());
    }

    @Test
    @DisplayName("analyze_jsonWithUnknownSeverity_returnsEmptySeverity")
    void analyze_jsonWithUnknownSeverity_returnsEmptySeverity() throws Exception {
        String json = "{\"allergen\":\"Eggs\",\"reaction\":\"Rash\",\"severity\":\"UNKNOWN\"}";

        when(contextBuilder.buildAllergyContext(any(), any())).thenReturn("ctx");
        when(deepSeekService.buildChatRequest(anyString(), anyString())).thenReturn(new DeepSeekChatRequest());
        when(deepSeekService.sendChatRequest(any())).thenReturn(buildResponse(json));

        AiAllergyDTO.Request req = buildRequest(1L, "egg allergy", null);
        AiAllergyDTO.Result result = aiAllergyService.analyze(req, Collections.emptyList());

        assertEquals("", result.getSeverity());
    }

    // ── With allergy history ──

    @Test
    @DisplayName("analyze_withAllergyHistory_passesHistoryToContextBuilder")
    void analyze_withAllergyHistory_passesHistoryToContextBuilder() throws Exception {
        Allergy allergy = Allergy.builder()
                .allergen("Aspirin")
                .severity(Allergy.AllergySeverity.MODERATE)
                .reaction("Stomach pain")
                .isActive(true)
                .build();

        String json = "{\"allergen\":\"Latex\",\"reaction\":\"Swelling\",\"severity\":\"SEVERE\"}";

        when(contextBuilder.buildAllergyContext(eq(1L), eq(List.of(allergy)))).thenReturn("history ctx");
        when(deepSeekService.buildChatRequest(anyString(), anyString())).thenReturn(new DeepSeekChatRequest());
        when(deepSeekService.sendChatRequest(any())).thenReturn(buildResponse(json));

        AiAllergyDTO.Request req = buildRequest(1L, "latex gloves cause swelling", Map.of("source", "voice"));
        AiAllergyDTO.Result result = aiAllergyService.analyze(req, List.of(allergy));

        assertEquals("Latex", result.getAllergen());
        assertEquals("SEVERE", result.getSeverity());
    }

    // ── Null choices in response ──

    @Test
    @DisplayName("analyze_nullChoicesInResponse_fallsBackToTranscript")
    void analyze_nullChoicesInResponse_fallsBackToTranscript() throws Exception {
        DeepSeekResponse resp = new DeepSeekResponse();
        resp.setChoices(null);

        when(contextBuilder.buildAllergyContext(any(), any())).thenReturn("ctx");
        when(deepSeekService.buildChatRequest(anyString(), anyString())).thenReturn(new DeepSeekChatRequest());
        when(deepSeekService.sendChatRequest(any())).thenReturn(resp);

        AiAllergyDTO.Request req = buildRequest(1L, "some text", null);
        AiAllergyDTO.Result result = aiAllergyService.analyze(req, Collections.emptyList());

        assertEquals("some text", result.getReaction());
    }

    @Test
    @DisplayName("analyze_emptyChoicesInResponse_fallsBackToTranscript")
    void analyze_emptyChoicesInResponse_fallsBackToTranscript() throws Exception {
        DeepSeekResponse resp = new DeepSeekResponse();
        resp.setChoices(Collections.emptyList());

        when(contextBuilder.buildAllergyContext(any(), any())).thenReturn("ctx");
        when(deepSeekService.buildChatRequest(anyString(), anyString())).thenReturn(new DeepSeekChatRequest());
        when(deepSeekService.sendChatRequest(any())).thenReturn(resp);

        AiAllergyDTO.Request req = buildRequest(1L, "some text", null);
        AiAllergyDTO.Result result = aiAllergyService.analyze(req, Collections.emptyList());

        assertEquals("some text", result.getReaction());
    }

    @Test
    @DisplayName("analyze_jsonWithNullSeverity_returnsEmptySeverity")
    void analyze_jsonWithNullSeverity_returnsEmptySeverity() throws Exception {
        String json = "{\"allergen\":\"Milk\",\"reaction\":\"Cramps\",\"severity\":null}";

        when(contextBuilder.buildAllergyContext(any(), any())).thenReturn("ctx");
        when(deepSeekService.buildChatRequest(anyString(), anyString())).thenReturn(new DeepSeekChatRequest());
        when(deepSeekService.sendChatRequest(any())).thenReturn(buildResponse(json));

        AiAllergyDTO.Request req = buildRequest(1L, "milk cramps", null);
        AiAllergyDTO.Result result = aiAllergyService.analyze(req, Collections.emptyList());

        assertEquals("Milk", result.getAllergen());
        assertEquals("Cramps", result.getReaction());
        assertEquals("", result.getSeverity());
    }
}
