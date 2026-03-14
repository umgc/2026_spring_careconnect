package com.careconnect.dto.chat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class AiRequestTest {

    // ─── Record: constructors and accessors ───────────────────────────────────

    @Test
    void constructor_allFields_setsCorrectly() throws Exception {
        final AiRequest request = new AiRequest("system context", "user prompt", "openai");

        assertThat(request.context()).isEqualTo("system context");
        assertThat(request.prompt()).isEqualTo("user prompt");
        assertThat(request.provider()).isEqualTo("openai");
    }

    @Test
    void constructor_nullFields_setsNulls() throws Exception {
        final AiRequest request = new AiRequest(null, "prompt only", null);

        assertThat(request.context()).isNull();
        assertThat(request.prompt()).isEqualTo("prompt only");
        assertThat(request.provider()).isNull();
    }

    // ─── Record: equals() and hashCode() ─────────────────────────────────────

    @Test
    void equals_sameFields_returnsTrue() throws Exception {
        final AiRequest r1 = new AiRequest("ctx", "ask something", "deepseek");
        final AiRequest r2 = new AiRequest("ctx", "ask something", "deepseek");

        assertThat(r1).isEqualTo(r2);
        assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
    }

    @Test
    void equals_differentFields_returnsFalse() throws Exception {
        final AiRequest r1 = new AiRequest("ctx1", "prompt1", "openai");
        final AiRequest r2 = new AiRequest("ctx2", "prompt2", "deepseek");

        assertThat(r1).isNotEqualTo(r2);
    }

    @Test
    void equals_null_returnsFalse() throws Exception {
        final AiRequest request = new AiRequest("ctx", "prompt", "openai");
        assertThat(request).isNotEqualTo(null);
    }

    // ─── Record: toString() ───────────────────────────────────────────────────

    @Test
    void toString_containsFieldValues() throws Exception {
        final AiRequest request = new AiRequest("my context", "my prompt", "openai");
        final String str = request.toString();

        assertThat(str).contains("my context");
        assertThat(str).contains("my prompt");
        assertThat(str).contains("openai");
    }

    // ─── AnalysisResult nested class ──────────────────────────────────────────

    @Test
    void analysisResult_constructor_setsFields() throws Exception {
        final AiRequest.AnalysisResult result = new AiRequest.AnalysisResult("raw text content", "s3/key/path");

        assertThat(result.rawText).isEqualTo("raw text content");
        assertThat(result.s3Key).isEqualTo("s3/key/path");
    }

    @Test
    void analysisResult_constructor_nullFields_setsNulls() throws Exception {
        final AiRequest.AnalysisResult result = new AiRequest.AnalysisResult(null, null);

        assertThat(result.rawText).isNull();
        assertThat(result.s3Key).isNull();
    }
}
