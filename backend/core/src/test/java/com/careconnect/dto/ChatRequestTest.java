package com.careconnect.dto;

import com.careconnect.model.ChatConversation;
import com.careconnect.service.MedicalDataAnonymizer.AnonymizationLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ChatRequestTest {

    // ─── No-arg constructor: Boolean fields are null → convenience methods return false ─

    @Test
    void noArgConstructor_booleanMethods_defaultToFalseWhenNull() throws Exception {
        final ChatRequest req = new ChatRequest();

        assertThat(req.isIncludeVitals()).isFalse();
        assertThat(req.isIncludeMedications()).isFalse();
        assertThat(req.isIncludeNotes()).isFalse();
        assertThat(req.isIncludeMoodPainLogs()).isFalse();
        assertThat(req.isIncludeAllergies()).isFalse();
        assertThat(req.isEnableDifferentialPrivacy()).isFalse();
        assertThat(req.isStatisticalSummaryOnly()).isFalse();
        assertThat(req.isPrivacyConsent()).isFalse();
    }

    // ─── isIncludeVitals ──────────────────────────────────────────────────────

    @Test
    void setIncludeVitals_true_returnsTrue() throws Exception {
        final ChatRequest req = new ChatRequest();
        req.setIncludeVitals(true);
        assertThat(req.isIncludeVitals()).isTrue();
    }

    @Test
    void setIncludeVitals_false_returnsFalse() throws Exception {
        final ChatRequest req = new ChatRequest();
        req.setIncludeVitals(false);
        assertThat(req.isIncludeVitals()).isFalse();
    }

    // ─── isIncludeMedications ─────────────────────────────────────────────────

    @Test
    void setIncludeMedications_true_returnsTrue() throws Exception {
        final ChatRequest req = new ChatRequest();
        req.setIncludeMedications(true);
        assertThat(req.isIncludeMedications()).isTrue();
    }

    @Test
    void setIncludeMedications_false_returnsFalse() throws Exception {
        final ChatRequest req = new ChatRequest();
        req.setIncludeMedications(false);
        assertThat(req.isIncludeMedications()).isFalse();
    }

    // ─── isIncludeNotes ───────────────────────────────────────────────────────

    @Test
    void setIncludeNotes_true_returnsTrue() throws Exception {
        final ChatRequest req = new ChatRequest();
        req.setIncludeNotes(true);
        assertThat(req.isIncludeNotes()).isTrue();
    }

    @Test
    void setIncludeNotes_false_returnsFalse() throws Exception {
        final ChatRequest req = new ChatRequest();
        req.setIncludeNotes(false);
        assertThat(req.isIncludeNotes()).isFalse();
    }

    // ─── isIncludeMoodPainLogs ────────────────────────────────────────────────

    @Test
    void setIncludeMoodPainLogs_true_returnsTrue() throws Exception {
        final ChatRequest req = new ChatRequest();
        req.setIncludeMoodPainLogs(true);
        assertThat(req.isIncludeMoodPainLogs()).isTrue();
    }

    @Test
    void setIncludeMoodPainLogs_false_returnsFalse() throws Exception {
        final ChatRequest req = new ChatRequest();
        req.setIncludeMoodPainLogs(false);
        assertThat(req.isIncludeMoodPainLogs()).isFalse();
    }

    // ─── isIncludeAllergies ───────────────────────────────────────────────────

    @Test
    void setIncludeAllergies_true_returnsTrue() throws Exception {
        final ChatRequest req = new ChatRequest();
        req.setIncludeAllergies(true);
        assertThat(req.isIncludeAllergies()).isTrue();
    }

    @Test
    void setIncludeAllergies_false_returnsFalse() throws Exception {
        final ChatRequest req = new ChatRequest();
        req.setIncludeAllergies(false);
        assertThat(req.isIncludeAllergies()).isFalse();
    }

    // ─── isEnableDifferentialPrivacy ──────────────────────────────────────────

    @Test
    void setEnableDifferentialPrivacy_true_returnsTrue() throws Exception {
        final ChatRequest req = new ChatRequest();
        req.setEnableDifferentialPrivacy(true);
        assertThat(req.isEnableDifferentialPrivacy()).isTrue();
    }

    @Test
    void setEnableDifferentialPrivacy_false_returnsFalse() throws Exception {
        final ChatRequest req = new ChatRequest();
        req.setEnableDifferentialPrivacy(false);
        assertThat(req.isEnableDifferentialPrivacy()).isFalse();
    }

    // ─── isStatisticalSummaryOnly ─────────────────────────────────────────────

    @Test
    void setStatisticalSummaryOnly_true_returnsTrue() throws Exception {
        final ChatRequest req = new ChatRequest();
        req.setStatisticalSummaryOnly(true);
        assertThat(req.isStatisticalSummaryOnly()).isTrue();
    }

    @Test
    void setStatisticalSummaryOnly_false_returnsFalse() throws Exception {
        final ChatRequest req = new ChatRequest();
        req.setStatisticalSummaryOnly(false);
        assertThat(req.isStatisticalSummaryOnly()).isFalse();
    }

    // ─── isPrivacyConsent ─────────────────────────────────────────────────────

    @Test
    void setPrivacyConsent_true_returnsTrue() throws Exception {
        final ChatRequest req = new ChatRequest();
        req.setPrivacyConsent(true);
        assertThat(req.isPrivacyConsent()).isTrue();
    }

    @Test
    void setPrivacyConsent_false_returnsFalse() throws Exception {
        final ChatRequest req = new ChatRequest();
        req.setPrivacyConsent(false);
        assertThat(req.isPrivacyConsent()).isFalse();
    }

    // ─── String / numeric getters and setters ─────────────────────────────────

    @Test
    void setAndGetMessage_roundTrips() throws Exception {
        final ChatRequest req = new ChatRequest();
        req.setMessage("Hello");
        assertThat(req.getMessage()).isEqualTo("Hello");
    }

    @Test
    void setAndGetConversationId_roundTrips() throws Exception {
        final ChatRequest req = new ChatRequest();
        req.setConversationId("conv-123");
        assertThat(req.getConversationId()).isEqualTo("conv-123");
    }

    @Test
    void setAndGetPatientId_roundTrips() throws Exception {
        final ChatRequest req = new ChatRequest();
        req.setPatientId(5L);
        assertThat(req.getPatientId()).isEqualTo(5L);
    }

    @Test
    void setAndGetUserId_roundTrips() throws Exception {
        final ChatRequest req = new ChatRequest();
        req.setUserId(10L);
        assertThat(req.getUserId()).isEqualTo(10L);
    }

    @Test
    void setAndGetChatType_roundTrips() throws Exception {
        final ChatRequest req = new ChatRequest();
        req.setChatType(ChatConversation.ChatType.MEDICAL_CONSULTATION);
        assertThat(req.getChatType()).isEqualTo(ChatConversation.ChatType.MEDICAL_CONSULTATION);
    }

    @Test
    void setAndGetPreferredModel_roundTrips() throws Exception {
        final ChatRequest req = new ChatRequest();
        req.setPreferredModel("gpt-4");
        assertThat(req.getPreferredModel()).isEqualTo("gpt-4");
    }

    @Test
    void setAndGetTitle_roundTrips() throws Exception {
        final ChatRequest req = new ChatRequest();
        req.setTitle("My Conversation");
        assertThat(req.getTitle()).isEqualTo("My Conversation");
    }

    @Test
    void setAndGetTemperature_roundTrips() throws Exception {
        final ChatRequest req = new ChatRequest();
        req.setTemperature(0.7);
        assertThat(req.getTemperature()).isEqualTo(0.7);
    }

    @Test
    void setAndGetMaxTokens_roundTrips() throws Exception {
        final ChatRequest req = new ChatRequest();
        req.setMaxTokens(2048);
        assertThat(req.getMaxTokens()).isEqualTo(2048);
    }

    @Test
    void setAndGetAdditionalContext_roundTrips() throws Exception {
        final ChatRequest req = new ChatRequest();
        final List<String> ctx = List.of("context1", "context2");
        req.setAdditionalContext(ctx);
        assertThat(req.getAdditionalContext()).isEqualTo(ctx);
    }

    @Test
    void setAndGetUploadedFiles_roundTrips() throws Exception {
        final ChatRequest req = new ChatRequest();
        req.setUploadedFiles(List.of());
        assertThat(req.getUploadedFiles()).isEmpty();
    }

    @Test
    void setAndGetAnonymizationLevel_roundTrips() throws Exception {
        final ChatRequest req = new ChatRequest();
        req.setAnonymizationLevel(AnonymizationLevel.AGGRESSIVE);
        assertThat(req.getAnonymizationLevel()).isEqualTo(AnonymizationLevel.AGGRESSIVE);
    }

    @Test
    void setAndGetDataRetentionDays_roundTrips() throws Exception {
        final ChatRequest req = new ChatRequest();
        req.setDataRetentionDays(30);
        assertThat(req.getDataRetentionDays()).isEqualTo(30);
    }

    // ─── Builder ──────────────────────────────────────────────────────────────

    @Test
    void builder_defaults_chatTypeIsGeneralSupport() throws Exception {
        final ChatRequest req = ChatRequest.builder().userId(1L).build();
        assertThat(req.getChatType()).isEqualTo(ChatConversation.ChatType.GENERAL_SUPPORT);
    }

    @Test
    void builder_defaults_anonymizationLevelIsModerate() throws Exception {
        final ChatRequest req = ChatRequest.builder().userId(1L).build();
        assertThat(req.getAnonymizationLevel()).isEqualTo(AnonymizationLevel.MODERATE);
    }

    @Test
    void builder_allFields_setsCorrectly() throws Exception {
        final ChatRequest req = ChatRequest.builder()
                .message("test message")
                .conversationId("conv-1")
                .patientId(2L)
                .userId(3L)
                .chatType(ChatConversation.ChatType.MEDICATION_INQUIRY)
                .preferredModel("claude-3")
                .title("Test Chat")
                .temperature(0.5)
                .maxTokens(512)
                .anonymizationLevel(AnonymizationLevel.MINIMAL)
                .build();

        assertThat(req.getMessage()).isEqualTo("test message");
        assertThat(req.getConversationId()).isEqualTo("conv-1");
        assertThat(req.getPatientId()).isEqualTo(2L);
        assertThat(req.getUserId()).isEqualTo(3L);
        assertThat(req.getChatType()).isEqualTo(ChatConversation.ChatType.MEDICATION_INQUIRY);
        assertThat(req.getPreferredModel()).isEqualTo("claude-3");
        assertThat(req.getTitle()).isEqualTo("Test Chat");
        assertThat(req.getTemperature()).isEqualTo(0.5);
        assertThat(req.getMaxTokens()).isEqualTo(512);
        assertThat(req.getAnonymizationLevel()).isEqualTo(AnonymizationLevel.MINIMAL);
    }
}
