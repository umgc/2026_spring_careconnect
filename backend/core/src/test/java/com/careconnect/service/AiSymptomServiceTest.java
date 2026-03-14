package com.careconnect.service;

import com.careconnect.dto.AiSymptomDTO;
import com.careconnect.model.Allergy;
import com.careconnect.model.SymptomEntry;
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

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class AiSymptomServiceTest {

    @Mock
    private DeepSeekService deepSeekService;

    @Mock
    private DeepSeekContextBuilder contextBuilder;

    @InjectMocks
    private AiSymptomService aiSymptomService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        // Inject real ObjectMapper
        try {
            var field = AiSymptomService.class.getDeclaredField("objectMapper");
            field.setAccessible(true);
            field.set(aiSymptomService, objectMapper);
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

    private AiSymptomDTO.Request buildRequest(Long patientId, String text, Map<String, Object> context) {
        AiSymptomDTO.Request req = new AiSymptomDTO.Request();
        req.setPatientId(patientId);
        req.setText(text);
        req.setContext(context);
        return req;
    }

    // ── Valid JSON response ──

    @Test
    @DisplayName("analyze_validJsonResponse_returnsPopulatedResult")
    void analyze_validJsonResponse_returnsPopulatedResult() throws Exception {
        String json = "{\"symptomKey\":\"headache\",\"symptomValue\":\"throbbing pain\",\"severity\":\"MODERATE\"}";

        when(contextBuilder.buildAllergyContext(any(), any())).thenReturn("allergy ctx");
        when(contextBuilder.buildSymptomContext(any(), any())).thenReturn("symptom ctx");
        when(deepSeekService.buildChatRequest(anyString(), anyString())).thenReturn(new DeepSeekChatRequest());
        when(deepSeekService.sendChatRequest(any())).thenReturn(buildResponse(json));

        AiSymptomDTO.Request req = buildRequest(1L, "I have a headache", null);
        AiSymptomDTO.Result result = aiSymptomService.analyze(req, Collections.emptyList(), Collections.emptyList());

        assertEquals("headache", result.getSymptomKey());
        assertEquals("throbbing pain", result.getSymptomValue());
        assertEquals("MODERATE", result.getSeverity());
        assertEquals("I have a headache", result.getNotes());
    }

    @Test
    @DisplayName("analyze_mildSeverity_normalizesMild")
    void analyze_mildSeverity_normalizesMild() throws Exception {
        String json = "{\"symptomKey\":\"cough\",\"symptomValue\":\"dry cough\",\"severity\":\"mild\"}";

        when(contextBuilder.buildAllergyContext(any(), any())).thenReturn("ctx");
        when(contextBuilder.buildSymptomContext(any(), any())).thenReturn("ctx");
        when(deepSeekService.buildChatRequest(anyString(), anyString())).thenReturn(new DeepSeekChatRequest());
        when(deepSeekService.sendChatRequest(any())).thenReturn(buildResponse(json));

        AiSymptomDTO.Request req = buildRequest(1L, "coughing", Map.of("hint", "dry"));
        AiSymptomDTO.Result result = aiSymptomService.analyze(req, Collections.emptyList(), Collections.emptyList());

        assertEquals("MILD", result.getSeverity());
    }

    @Test
    @DisplayName("analyze_severeSeverity_normalizesSevere")
    void analyze_severeSeverity_normalizesSevere() throws Exception {
        String json = "{\"symptomKey\":\"chest pain\",\"symptomValue\":\"sharp\",\"severity\":\"SEVERE\"}";

        when(contextBuilder.buildAllergyContext(any(), any())).thenReturn("ctx");
        when(contextBuilder.buildSymptomContext(any(), any())).thenReturn("ctx");
        when(deepSeekService.buildChatRequest(anyString(), anyString())).thenReturn(new DeepSeekChatRequest());
        when(deepSeekService.sendChatRequest(any())).thenReturn(buildResponse(json));

        AiSymptomDTO.Request req = buildRequest(1L, "chest pain", null);
        AiSymptomDTO.Result result = aiSymptomService.analyze(req, Collections.emptyList(), Collections.emptyList());

        assertEquals("SEVERE", result.getSeverity());
    }

    // ── Non-JSON content (not blank) ──

    @Test
    @DisplayName("analyze_nonJsonContent_fallsBackWithWarning")
    void analyze_nonJsonContent_fallsBackWithWarning() throws Exception {
        when(contextBuilder.buildAllergyContext(any(), any())).thenReturn("ctx");
        when(contextBuilder.buildSymptomContext(any(), any())).thenReturn("ctx");
        when(deepSeekService.buildChatRequest(anyString(), anyString())).thenReturn(new DeepSeekChatRequest());
        when(deepSeekService.sendChatRequest(any())).thenReturn(buildResponse("I cannot determine the symptom"));

        AiSymptomDTO.Request req = buildRequest(1L, "feeling dizzy", null);
        AiSymptomDTO.Result result = aiSymptomService.analyze(req, Collections.emptyList(), Collections.emptyList());

        assertEquals("", result.getSymptomKey());
        assertEquals("", result.getSymptomValue());
        assertEquals("", result.getSeverity());
        assertEquals("feeling dizzy", result.getNotes());
    }

    // ── Empty/blank content ──

    @Test
    @DisplayName("analyze_emptyContent_keepsDefaultValues")
    void analyze_emptyContent_keepsDefaultValues() throws Exception {
        when(contextBuilder.buildAllergyContext(any(), any())).thenReturn("ctx");
        when(contextBuilder.buildSymptomContext(any(), any())).thenReturn("ctx");
        when(deepSeekService.buildChatRequest(anyString(), anyString())).thenReturn(new DeepSeekChatRequest());
        when(deepSeekService.sendChatRequest(any())).thenReturn(buildResponse(""));

        AiSymptomDTO.Request req = buildRequest(1L, "some text", null);
        AiSymptomDTO.Result result = aiSymptomService.analyze(req, Collections.emptyList(), Collections.emptyList());

        assertEquals("", result.getSymptomKey());
        assertEquals("", result.getSymptomValue());
        assertEquals("", result.getSeverity());
        assertEquals("some text", result.getNotes());
    }

    // ── Null text ──

    @Test
    @DisplayName("analyze_nullText_notesIsEmptyString")
    void analyze_nullText_notesIsEmptyString() throws Exception {
        String json = "{\"symptomKey\":\"nausea\",\"symptomValue\":\"mild\",\"severity\":\"MILD\"}";

        when(contextBuilder.buildAllergyContext(any(), any())).thenReturn("ctx");
        when(contextBuilder.buildSymptomContext(any(), any())).thenReturn("ctx");
        when(deepSeekService.buildChatRequest(anyString(), anyString())).thenReturn(new DeepSeekChatRequest());
        when(deepSeekService.sendChatRequest(any())).thenReturn(buildResponse(json));

        AiSymptomDTO.Request req = buildRequest(1L, null, null);
        AiSymptomDTO.Result result = aiSymptomService.analyze(req, Collections.emptyList(), Collections.emptyList());

        assertEquals("", result.getNotes());
        assertEquals("nausea", result.getSymptomKey());
    }

    // ── Context provided ──

    @Test
    @DisplayName("analyze_withContext_passesContextToPrompt")
    void analyze_withContext_passesContextToPrompt() throws Exception {
        String json = "{\"symptomKey\":\"fever\",\"symptomValue\":\"101F\",\"severity\":\"MODERATE\"}";

        when(contextBuilder.buildAllergyContext(any(), any())).thenReturn("allergy ctx");
        when(contextBuilder.buildSymptomContext(any(), any())).thenReturn("symptom ctx");
        when(deepSeekService.buildChatRequest(anyString(), anyString())).thenReturn(new DeepSeekChatRequest());
        when(deepSeekService.sendChatRequest(any())).thenReturn(buildResponse(json));

        Map<String, Object> ctx = Map.of("symptomKey", "fever", "severity", "MODERATE");
        AiSymptomDTO.Request req = buildRequest(1L, "I have a fever", ctx);
        AiSymptomDTO.Result result = aiSymptomService.analyze(req, Collections.emptyList(), Collections.emptyList());

        assertEquals("fever", result.getSymptomKey());
        assertEquals("MODERATE", result.getSeverity());
    }

    // ── With allergy and symptom history ──

    @Test
    @DisplayName("analyze_withAllergyAndSymptomHistory_passesHistoryToBuilder")
    void analyze_withAllergyAndSymptomHistory_passesHistoryToBuilder() throws Exception {
        Allergy allergy = Allergy.builder()
                .allergen("Pollen").severity(Allergy.AllergySeverity.MILD).build();
        SymptomEntry symptom = SymptomEntry.builder()
                .symptomKey("cough").symptomValue("dry").severity(2)
                .takenAt(Instant.now()).completed(true).build();

        String json = "{\"symptomKey\":\"runny nose\",\"symptomValue\":\"clear\",\"severity\":\"MILD\"}";

        when(contextBuilder.buildAllergyContext(eq(1L), eq(List.of(allergy)))).thenReturn("allergy history");
        when(contextBuilder.buildSymptomContext(eq(1L), eq(List.of(symptom)))).thenReturn("symptom history");
        when(deepSeekService.buildChatRequest(anyString(), anyString())).thenReturn(new DeepSeekChatRequest());
        when(deepSeekService.sendChatRequest(any())).thenReturn(buildResponse(json));

        AiSymptomDTO.Request req = buildRequest(1L, "runny nose", null);
        AiSymptomDTO.Result result = aiSymptomService.analyze(req, List.of(allergy), List.of(symptom));

        assertEquals("runny nose", result.getSymptomKey());
    }

    // ── Null choices ──

    @Test
    @DisplayName("analyze_nullChoicesInResponse_keepsDefaults")
    void analyze_nullChoicesInResponse_keepsDefaults() throws Exception {
        DeepSeekResponse resp = new DeepSeekResponse();
        resp.setChoices(null);

        when(contextBuilder.buildAllergyContext(any(), any())).thenReturn("ctx");
        when(contextBuilder.buildSymptomContext(any(), any())).thenReturn("ctx");
        when(deepSeekService.buildChatRequest(anyString(), anyString())).thenReturn(new DeepSeekChatRequest());
        when(deepSeekService.sendChatRequest(any())).thenReturn(resp);

        AiSymptomDTO.Request req = buildRequest(1L, "transcript", null);
        AiSymptomDTO.Result result = aiSymptomService.analyze(req, Collections.emptyList(), Collections.emptyList());

        assertEquals("", result.getSymptomKey());
        assertEquals("transcript", result.getNotes());
    }

    // ── JSON with missing fields ──

    @Test
    @DisplayName("analyze_jsonMissingFields_returnsEmptyStrings")
    void analyze_jsonMissingFields_returnsEmptyStrings() throws Exception {
        String json = "{\"symptomKey\":\"anxiety\"}";

        when(contextBuilder.buildAllergyContext(any(), any())).thenReturn("ctx");
        when(contextBuilder.buildSymptomContext(any(), any())).thenReturn("ctx");
        when(deepSeekService.buildChatRequest(anyString(), anyString())).thenReturn(new DeepSeekChatRequest());
        when(deepSeekService.sendChatRequest(any())).thenReturn(buildResponse(json));

        AiSymptomDTO.Request req = buildRequest(1L, "feeling anxious", null);
        AiSymptomDTO.Result result = aiSymptomService.analyze(req, Collections.emptyList(), Collections.emptyList());

        assertEquals("anxiety", result.getSymptomKey());
        assertEquals("", result.getSymptomValue());
        assertEquals("", result.getSeverity());
    }

    @Test
    @DisplayName("analyze_jsonWithUnknownSeverity_returnsEmptySeverity")
    void analyze_jsonWithUnknownSeverity_returnsEmptySeverity() throws Exception {
        String json = "{\"symptomKey\":\"rash\",\"symptomValue\":\"red\",\"severity\":\"UNKNOWN\"}";

        when(contextBuilder.buildAllergyContext(any(), any())).thenReturn("ctx");
        when(contextBuilder.buildSymptomContext(any(), any())).thenReturn("ctx");
        when(deepSeekService.buildChatRequest(anyString(), anyString())).thenReturn(new DeepSeekChatRequest());
        when(deepSeekService.sendChatRequest(any())).thenReturn(buildResponse(json));

        AiSymptomDTO.Request req = buildRequest(1L, "skin rash", null);
        AiSymptomDTO.Result result = aiSymptomService.analyze(req, Collections.emptyList(), Collections.emptyList());

        assertEquals("", result.getSeverity());
    }

    // ── Blank content (whitespace only) ──

    @Test
    @DisplayName("analyze_blankContent_keepsDefaults")
    void analyze_blankContent_keepsDefaults() throws Exception {
        when(contextBuilder.buildAllergyContext(any(), any())).thenReturn("ctx");
        when(contextBuilder.buildSymptomContext(any(), any())).thenReturn("ctx");
        when(deepSeekService.buildChatRequest(anyString(), anyString())).thenReturn(new DeepSeekChatRequest());
        when(deepSeekService.sendChatRequest(any())).thenReturn(buildResponse("   "));

        AiSymptomDTO.Request req = buildRequest(1L, "text", null);
        AiSymptomDTO.Result result = aiSymptomService.analyze(req, Collections.emptyList(), Collections.emptyList());

        assertEquals("", result.getSymptomKey());
        assertEquals("text", result.getNotes());
    }
}
