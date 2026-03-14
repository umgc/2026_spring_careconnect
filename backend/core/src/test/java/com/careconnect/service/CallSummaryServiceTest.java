package com.careconnect.service;

import com.careconnect.model.CallSummary;
import com.careconnect.model.CallTelemetryEvent;
import com.careconnect.repository.CallSummaryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CallSummaryService Tests")
class CallSummaryServiceTest {

    @Mock
    private CallSummaryRepository callSummaryRepository;

    @Mock
    private CallTranscriptService callTranscriptService;

    @Mock
    private BedrockSentimentService bedrockSentimentService;

    private CallSummaryService service;

    private static final String CALL_ID = "call-1";

    @BeforeEach
    void setUp() {
        service = new CallSummaryService(
                callSummaryRepository,
                callTranscriptService,
                bedrockSentimentService,
                new ObjectMapper()
        );
    }

    @Nested
    @DisplayName("lookup")
    class LookupTests {

        @Test
        @DisplayName("getLatestSummaryEntity returns empty for blank callId")
        void getLatestSummaryEntity_blankCallId_returnsEmpty() {
            assertThat(service.getLatestSummaryEntity(" ")).isEmpty();
        }

        @Test
        @DisplayName("getLatestSummary parses stored JSON into response map")
        void getLatestSummary_parsesStoredJson() {
            CallSummary summary = new CallSummary();
            summary.setCallId(CALL_ID);
            summary.setStatus("SUCCESS");
            summary.setGeneratedAt(LocalDateTime.now());
            summary.setTranscriptSegmentCount(3);
            summary.setGeneratedByUserId(9L);
            summary.setSummaryJson("""
                    {"headline":"Stable call","overallAssessment":"Patient remained stable.","keyConcerns":["fatigue"],"recommendedActions":["hydrate"],"followUpQuestions":["Any pain?"]}
                    """);

            when(callSummaryRepository.findTopByCallIdOrderByGeneratedAtDesc(CALL_ID)).thenReturn(Optional.of(summary));
            when(callTranscriptService.isArchived(CALL_ID)).thenReturn(true);

            Optional<Map<String, Object>> result = service.getLatestSummary(CALL_ID);

            assertThat(result).isPresent();
            assertThat(result.get()).containsEntry("callId", CALL_ID);
            assertThat(result.get()).containsEntry("transcriptArchived", true);
            assertThat((Map<String, Object>) result.get().get("summary")).containsEntry("headline", "Stable call");
        }

        @Test
        @DisplayName("getLatestSummary tolerates invalid stored JSON")
        void getLatestSummary_invalidJson_returnsEmptySummaryPayload() {
            CallSummary summary = new CallSummary();
            summary.setCallId(CALL_ID);
            summary.setStatus("ERROR");
            summary.setGeneratedAt(LocalDateTime.now());
            summary.setTranscriptSegmentCount(1);
            summary.setSummaryJson("{bad json");

            when(callSummaryRepository.findTopByCallIdOrderByGeneratedAtDesc(CALL_ID)).thenReturn(Optional.of(summary));
            when(callTranscriptService.isArchived(CALL_ID)).thenReturn(false);

            Map<String, Object> result = service.getLatestSummary(CALL_ID).orElseThrow();

            assertThat(result).containsEntry("status", "ERROR");
            assertThat((Map<String, Object>) result.get("summary")).isEqualTo(Map.of());
        }
    }

    @Nested
    @DisplayName("generateAndStoreSummary")
    class GenerateAndStoreSummaryTests {

        @Test
        @DisplayName("throws when callId is missing")
        void generateAndStoreSummary_missingCallId_throws() {
            assertThatThrownBy(() -> service.generateAndStoreSummary(" ", 1L, Map.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("callId is required");
        }

        @Test
        @DisplayName("stores NO_TRANSCRIPT summary when transcript is blank")
        void generateAndStoreSummary_noTranscript_storesNoTranscript() {
            when(callTranscriptService.buildTranscriptTextForSummary(CALL_ID)).thenReturn(" ");
            when(callTranscriptService.countSegments(CALL_ID)).thenReturn(0L);
            when(callTranscriptService.isArchived(CALL_ID)).thenReturn(false);
            when(callSummaryRepository.save(any(CallSummary.class))).thenAnswer(inv -> inv.getArgument(0));

            Map<String, Object> result = service.generateAndStoreSummary(CALL_ID, 7L, Map.of());

            assertThat(result).containsEntry("status", "NO_TRANSCRIPT");
            assertThat(result).containsEntry("transcriptArchived", false);

            ArgumentCaptor<CallSummary> captor = ArgumentCaptor.forClass(CallSummary.class);
            verify(callSummaryRepository).save(captor.capture());
            assertThat(captor.getValue().getErrorMessage()).contains("No transcript segments");
        }

        @Test
        @DisplayName("stores SUCCESS summary and converts telemetry events into channel scores")
        void generateAndStoreSummary_success_storesSummary() {
            CallTelemetryEvent voice = new CallTelemetryEvent();
            voice.setSentimentScore(0.72);
            voice.setSentimentLabel(null);
            voice.setSentimentNotes(null);
            voice.setAnalysisTimestamp(1234L);

            CallTelemetryEvent video = new CallTelemetryEvent();
            video.setSentimentScore(0.41);
            video.setSentimentLabel("CALM");
            video.setSentimentNotes("steady");

            when(callTranscriptService.buildTranscriptTextForSummary(CALL_ID)).thenReturn("[PATIENT] Feeling okay");
            when(callTranscriptService.countSegments(CALL_ID)).thenReturn(2L);
            when(callTranscriptService.archiveIfEligible(CALL_ID)).thenReturn(true);
            when(callTranscriptService.isArchived(CALL_ID)).thenReturn(true);
            when(bedrockSentimentService.summarizeTranscript(any(), any(), any())).thenReturn(Map.of(
                    "headline", "Call follow-up",
                    "overallAssessment", "Patient was mostly stable.",
                    "keyConcerns", List.of("Fatigue"),
                    "recommendedActions", List.of("Monitor symptoms"),
                    "followUpQuestions", List.of("Any worsening symptoms?")
            ));
            when(callSummaryRepository.save(any(CallSummary.class))).thenAnswer(inv -> inv.getArgument(0));

            Map<String, Object> result = service.generateAndStoreSummary(CALL_ID, 8L, Map.of(
                    " voice ", voice,
                    "VIDEO", video,
                    "TEXT", new CallTelemetryEvent()
            ));

            assertThat(result).containsEntry("status", "SUCCESS");
            assertThat(result).containsEntry("transcriptArchived", true);
            assertThat((Map<String, Object>) result.get("summary")).containsEntry("headline", "Call follow-up");

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, BedrockSentimentService.SentimentResult>> channelsCaptor =
                    ArgumentCaptor.forClass(Map.class);
            verify(bedrockSentimentService).summarizeTranscript(
                    org.mockito.ArgumentMatchers.eq(CALL_ID),
                    org.mockito.ArgumentMatchers.eq("[PATIENT] Feeling okay"),
                    channelsCaptor.capture()
            );
            Map<String, BedrockSentimentService.SentimentResult> channels = channelsCaptor.getValue();
            assertThat(channels).containsKeys("VOICE", "VIDEO");
            assertThat(channels.get("VOICE").label()).isEqualTo("ANXIOUS");
            assertThat(channels.get("VIDEO").notes()).isEqualTo("steady");
        }

        @Test
        @DisplayName("stores ERROR summary when Bedrock summarization throws")
        void generateAndStoreSummary_bedrockThrows_storesError() {
            when(callTranscriptService.buildTranscriptTextForSummary(CALL_ID)).thenReturn("[PATIENT] Needs review");
            when(callTranscriptService.countSegments(CALL_ID)).thenReturn(4L);
            when(callTranscriptService.archiveIfEligible(CALL_ID)).thenReturn(false);
            when(callTranscriptService.isArchived(CALL_ID)).thenReturn(false);
            when(bedrockSentimentService.summarizeTranscript(any(), any(), any()))
                    .thenThrow(new RuntimeException("bedrock timeout"));
            when(callSummaryRepository.save(any(CallSummary.class))).thenAnswer(inv -> inv.getArgument(0));

            Map<String, Object> result = service.generateAndStoreSummary(CALL_ID, 3L, Map.of());

            assertThat(result).containsEntry("status", "ERROR");
            assertThat(result).containsEntry("errorMessage", "bedrock timeout");
            assertThat((Map<String, Object>) result.get("summary")).containsEntry("headline", "Summary unavailable");
        }
    }

    @Nested
    @DisplayName("deleteSummariesForCall")
    class DeleteSummariesForCallTests {

        @Test
        @DisplayName("returns zero for blank callId")
        void deleteSummariesForCall_blank_returnsZero() {
            assertThat(service.deleteSummariesForCall(" ")).isZero();
        }

        @Test
        @DisplayName("trims callId and deletes summary rows")
        void deleteSummariesForCall_trimmed_deletesRows() {
            when(callSummaryRepository.deleteByCallId(CALL_ID)).thenReturn(5L);

            assertThat(service.deleteSummariesForCall("  " + CALL_ID + "  ")).isEqualTo(5L);
        }
    }
}
