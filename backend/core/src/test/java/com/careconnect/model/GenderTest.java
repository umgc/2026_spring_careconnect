package com.careconnect.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GenderTest {

    // ─── getDisplayName() ────────────────────────────────────────────────────

    @Test
    void getDisplayName_male() throws Exception {
        assertThat(Gender.MALE.getDisplayName()).isEqualTo("Male");
    }

    @Test
    void getDisplayName_female() throws Exception {
        assertThat(Gender.FEMALE.getDisplayName()).isEqualTo("Female");
    }

    @Test
    void getDisplayName_other() throws Exception {
        assertThat(Gender.OTHER.getDisplayName()).isEqualTo("Other");
    }

    @Test
    void getDisplayName_preferNotToSay() throws Exception {
        assertThat(Gender.PREFER_NOT_TO_SAY.getDisplayName()).isEqualTo("Prefer not to say");
    }

    // ─── getValue() ──────────────────────────────────────────────────────────

    @Test
    void getValue_returnsLowercaseName() throws Exception {
        assertThat(Gender.MALE.getValue()).isEqualTo("male");
        assertThat(Gender.FEMALE.getValue()).isEqualTo("female");
        assertThat(Gender.OTHER.getValue()).isEqualTo("other");
        assertThat(Gender.PREFER_NOT_TO_SAY.getValue()).isEqualTo("prefer_not_to_say");
    }

    // ─── fromString() ────────────────────────────────────────────────────────

    @Test
    void fromString_null_returnsNull() throws Exception {
        assertThat(Gender.fromString(null)).isNull();
    }

    @Test
    void fromString_male_uppercase() throws Exception {
        assertThat(Gender.fromString("MALE")).isEqualTo(Gender.MALE);
    }

    @Test
    void fromString_female_lowercase() throws Exception {
        assertThat(Gender.fromString("female")).isEqualTo(Gender.FEMALE);
    }

    @Test
    void fromString_other_mixedCase() throws Exception {
        assertThat(Gender.fromString("Other")).isEqualTo(Gender.OTHER);
    }

    @Test
    void fromString_M_abbreviation() throws Exception {
        assertThat(Gender.fromString("M")).isEqualTo(Gender.MALE);
    }

    @Test
    void fromString_F_abbreviation() throws Exception {
        assertThat(Gender.fromString("F")).isEqualTo(Gender.FEMALE);
    }

    @Test
    void fromString_PREFER_NOT_TO_SAY() throws Exception {
        assertThat(Gender.fromString("PREFER_NOT_TO_SAY")).isEqualTo(Gender.PREFER_NOT_TO_SAY);
    }

    @Test
    void fromString_PREFERNOTTOSAY() throws Exception {
        assertThat(Gender.fromString("PREFERNOTTOSAY")).isEqualTo(Gender.PREFER_NOT_TO_SAY);
    }

    @Test
    void fromString_NOT_SAY() throws Exception {
        assertThat(Gender.fromString("NOT_SAY")).isEqualTo(Gender.PREFER_NOT_TO_SAY);
    }

    @Test
    void fromString_preferNotToSay_withSpaces() throws Exception {
        assertThat(Gender.fromString("PREFER NOT TO SAY")).isEqualTo(Gender.PREFER_NOT_TO_SAY);
    }

    @Test
    void fromString_invalid_throwsException() throws Exception {
        assertThatThrownBy(() -> Gender.fromString("UNKNOWN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid gender value");
    }

    // ─── toString() ──────────────────────────────────────────────────────────

    @Test
    void toString_returnsDisplayName() throws Exception {
        assertThat(Gender.MALE.toString()).isEqualTo("Male");
        assertThat(Gender.FEMALE.toString()).isEqualTo("Female");
        assertThat(Gender.OTHER.toString()).isEqualTo("Other");
        assertThat(Gender.PREFER_NOT_TO_SAY.toString()).isEqualTo("Prefer not to say");
    }

    // ─── enum values ─────────────────────────────────────────────────────────

    @Test
    void values_containsAllExpected() throws Exception {
        assertThat(Gender.values()).containsExactly(
                Gender.MALE, Gender.FEMALE, Gender.OTHER, Gender.PREFER_NOT_TO_SAY);
    }
}
