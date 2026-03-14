package com.careconnect.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QuestionTypeTest {

    @Test
    void values_containsAllExpected() throws Exception {
        assertThat(QuestionType.values()).containsExactly(
                QuestionType.TEXT,
                QuestionType.YES_NO,
                QuestionType.TRUE_FALSE,
                QuestionType.NUMBER
        );
    }

    @Test
    void valueOf_returnsCorrectConstant() throws Exception {
        assertThat(QuestionType.valueOf("TEXT")).isEqualTo(QuestionType.TEXT);
        assertThat(QuestionType.valueOf("YES_NO")).isEqualTo(QuestionType.YES_NO);
        assertThat(QuestionType.valueOf("TRUE_FALSE")).isEqualTo(QuestionType.TRUE_FALSE);
        assertThat(QuestionType.valueOf("NUMBER")).isEqualTo(QuestionType.NUMBER);
    }
}
