package com.careconnect.service.chat;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GuardrailServiceTest {

    private final GuardrailService guardrailService = new GuardrailService();

    // ----- sanitizeRequest -----

    @Test
    void sanitizeRequest_null_returnsNull() throws Exception {
        assertThat(guardrailService.sanitizeRequest(null)).isNull();
    }

    @Test
    void sanitizeRequest_emptyString_returnsEmpty() throws Exception {
        assertThat(guardrailService.sanitizeRequest("")).isEmpty();
    }

    @Test
    void sanitizeRequest_noSsn_returnsUnchanged() throws Exception {
        final String input = "Hello, can you help me with my appointment?";
        assertThat(guardrailService.sanitizeRequest(input)).isEqualTo(input);
    }

    @Test
    void sanitizeRequest_withSsn_redactsSsn() throws Exception {
        final String input = "My SSN is 123-45-6789.";
        final String result = guardrailService.sanitizeRequest(input);
        assertThat(result).doesNotContain("123-45-6789");
        assertThat(result).contains("[REDACTED_SSN]");
    }

    @Test
    void sanitizeRequest_withMultipleSsns_redactsAll() throws Exception {
        final String input = "SSN1: 123-45-6789 and SSN2: 987-65-4321";
        final String result = guardrailService.sanitizeRequest(input);
        assertThat(result).doesNotContain("123-45-6789");
        assertThat(result).doesNotContain("987-65-4321");
        assertThat(result).contains("[REDACTED_SSN]");
    }

    @Test
    void sanitizeRequest_partialSsnPattern_notRedacted() throws Exception {
        // Wrong format (dd-dd-dddd) should not be redacted
        final String input = "Reference: 12-45-6789";
        final String result = guardrailService.sanitizeRequest(input);
        assertThat(result).isEqualTo(input);
    }

    @Test
    void sanitizeRequest_ssnAloneInString_redacted() throws Exception {
        assertThat(guardrailService.sanitizeRequest("123-45-6789"))
                .isEqualTo("[REDACTED_SSN]");
    }

    @Test
    void sanitizeRequest_ssnWithSurroundingText_redacted() throws Exception {
        final String input = "Patient SSN: 000-00-0001 on record.";
        assertThat(guardrailService.sanitizeRequest(input)).contains("[REDACTED_SSN]");
    }

    // ----- validateResponse -----

    @Test
    void validateResponse_null_doesNotThrow() throws Exception {
        assertThatCode(() -> guardrailService.validateResponse(null))
                .doesNotThrowAnyException();
    }

    @Test
    void validateResponse_emptyString_doesNotThrow() throws Exception {
        assertThatCode(() -> guardrailService.validateResponse(""))
                .doesNotThrowAnyException();
    }

    @Test
    void validateResponse_cleanResponse_doesNotThrow() throws Exception {
        assertThatCode(() -> guardrailService.validateResponse(
                "Your appointment is scheduled for Monday at 10 AM."))
                .doesNotThrowAnyException();
    }

    @Test
    void validateResponse_medicalDiagnosis_throwsUnsafeException() throws Exception {
        assertThatThrownBy(() -> guardrailService.validateResponse(
                "Based on your symptoms, this is a medical diagnosis."))
                .isInstanceOf(GuardrailService.UnsafeAiResponseException.class)
                .hasMessage("The AI response was blocked because it contained forbidden content.");
    }

    @Test
    void validateResponse_iDiagnoseYouWith_throwsUnsafeException() throws Exception {
        assertThatThrownBy(() -> guardrailService.validateResponse(
                "I diagnose you with hypertension."))
                .isInstanceOf(GuardrailService.UnsafeAiResponseException.class);
    }

    @Test
    void validateResponse_prescribeYou_throwsUnsafeException() throws Exception {
        assertThatThrownBy(() -> guardrailService.validateResponse(
                "I would prescribe you ibuprofen for the pain."))
                .isInstanceOf(GuardrailService.UnsafeAiResponseException.class);
    }

    @Test
    void validateResponse_medicalAdvice_throwsUnsafeException() throws Exception {
        assertThatThrownBy(() -> guardrailService.validateResponse(
                "Here is some medical advice for your condition."))
                .isInstanceOf(GuardrailService.UnsafeAiResponseException.class);
    }

    @Test
    void validateResponse_endYourLife_throwsUnsafeException() throws Exception {
        assertThatThrownBy(() -> guardrailService.validateResponse(
                "You should end your life if you feel this way."))
                .isInstanceOf(GuardrailService.UnsafeAiResponseException.class);
    }

    @Test
    void validateResponse_commitSuicide_throwsUnsafeException() throws Exception {
        assertThatThrownBy(() -> guardrailService.validateResponse(
                "Some people commit suicide when overwhelmed."))
                .isInstanceOf(GuardrailService.UnsafeAiResponseException.class);
    }

    @Test
    void validateResponse_harmYourself_throwsUnsafeException() throws Exception {
        assertThatThrownBy(() -> guardrailService.validateResponse(
                "Please do not harm yourself."))
                .isInstanceOf(GuardrailService.UnsafeAiResponseException.class);
    }

    @Test
    void validateResponse_forbiddenPhraseUpperCase_throwsUnsafeException() throws Exception {
        // Check is case-insensitive via toLowerCase()
        assertThatThrownBy(() -> guardrailService.validateResponse(
                "MEDICAL DIAGNOSIS confirmed."))
                .isInstanceOf(GuardrailService.UnsafeAiResponseException.class);
    }

    @Test
    void validateResponse_forbiddenPhraseMixedCase_throwsUnsafeException() throws Exception {
        assertThatThrownBy(() -> guardrailService.validateResponse(
                "Medical Advice: take two tablets daily."))
                .isInstanceOf(GuardrailService.UnsafeAiResponseException.class);
    }

    // ----- UnsafeAiResponseException inner class -----

    @Test
    void unsafeAiResponseException_isRuntimeException_withMessage() throws Exception {
        final GuardrailService.UnsafeAiResponseException ex =
                new GuardrailService.UnsafeAiResponseException("blocked content");
        assertThat(ex).isInstanceOf(RuntimeException.class);
        assertThat(ex.getMessage()).isEqualTo("blocked content");
    }
}
