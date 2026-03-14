package com.careconnect.dto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class MetricsTest {

    // ─── No-arg constructor ───────────────────────────────────────────────────

    @Test
    void noArgConstructor_fieldsAreNull() throws Exception {
        final Metrics metrics = new Metrics();

        assertThat(metrics.getId()).isNull();
        assertThat(metrics.getMetricType()).isNull();
        assertThat(metrics.getValue()).isNull();
        assertThat(metrics.getUnit()).isNull();
        assertThat(metrics.getTimestamp()).isNull();
        assertThat(metrics.getPatientId()).isNull();
        assertThat(metrics.getSource()).isNull();
    }

    // ─── All-args constructor ─────────────────────────────────────────────────

    @Test
    void allArgsConstructor_setsAllFields() throws Exception {
        final LocalDateTime ts = LocalDateTime.of(2026, 3, 10, 8, 0);

        final Metrics metrics = new Metrics(1L, "heart_rate", 72.0, "bpm", ts, 5L, "fitbit");

        assertThat(metrics.getId()).isEqualTo(1L);
        assertThat(metrics.getMetricType()).isEqualTo("heart_rate");
        assertThat(metrics.getValue()).isEqualTo(72.0);
        assertThat(metrics.getUnit()).isEqualTo("bpm");
        assertThat(metrics.getTimestamp()).isEqualTo(ts);
        assertThat(metrics.getPatientId()).isEqualTo(5L);
        assertThat(metrics.getSource()).isEqualTo("fitbit");
    }

    // ─── Lombok @Data setters/getters ─────────────────────────────────────────

    @Test
    void setAndGetId_roundTrips() throws Exception {
        final Metrics metrics = new Metrics();
        metrics.setId(10L);
        assertThat(metrics.getId()).isEqualTo(10L);
    }

    @Test
    void setAndGetValue_roundTrips() throws Exception {
        final Metrics metrics = new Metrics();
        metrics.setValue(98.6);
        assertThat(metrics.getValue()).isEqualTo(98.6);
    }

    @Test
    void setAndGetUnit_roundTrips() throws Exception {
        final Metrics metrics = new Metrics();
        metrics.setUnit("mmHg");
        assertThat(metrics.getUnit()).isEqualTo("mmHg");
    }

    @Test
    void setAndGetPatientId_roundTrips() throws Exception {
        final Metrics metrics = new Metrics();
        metrics.setPatientId(3L);
        assertThat(metrics.getPatientId()).isEqualTo(3L);
    }

    // ─── Explicit setters (override Lombok for compilation safety) ────────────

    @Test
    void setMetricType_getMetricType_roundTrips() throws Exception {
        final Metrics metrics = new Metrics();
        metrics.setMetricType("blood_pressure");
        assertThat(metrics.getMetricType()).isEqualTo("blood_pressure");
    }

    @Test
    void setSource_getSource_roundTrips() throws Exception {
        final Metrics metrics = new Metrics();
        metrics.setSource("manual");
        assertThat(metrics.getSource()).isEqualTo("manual");
    }

    @Test
    void setTimestamp_getTimestamp_roundTrips() throws Exception {
        final Metrics metrics = new Metrics();
        final LocalDateTime ts = LocalDateTime.of(2026, 5, 20, 14, 45);
        metrics.setTimestamp(ts);
        assertThat(metrics.getTimestamp()).isEqualTo(ts);
    }

    // ─── Lombok @Data equals / hashCode / toString ────────────────────────────

    @Test
    void equals_sameValues_areEqual() throws Exception {
        final LocalDateTime ts = LocalDateTime.of(2026, 1, 1, 0, 0);
        final Metrics m1 = new Metrics(1L, "steps", 10000.0, "count", ts, 2L, "manual");
        final Metrics m2 = new Metrics(1L, "steps", 10000.0, "count", ts, 2L, "manual");

        assertThat(m1).isEqualTo(m2);
        assertThat(m1.hashCode()).isEqualTo(m2.hashCode());
    }

    @Test
    void equals_differentValues_areNotEqual() throws Exception {
        final Metrics m1 = new Metrics(1L, "steps", 10000.0, "count", null, 2L, "manual");
        final Metrics m2 = new Metrics(2L, "steps", 10000.0, "count", null, 2L, "manual");

        assertThat(m1).isNotEqualTo(m2);
    }

    @Test
    void toString_containsMetricType() throws Exception {
        final Metrics metrics = new Metrics(1L, "oxygen_saturation", 98.0, "%", null, 4L, "pulse_ox");
        assertThat(metrics.toString()).contains("oxygen_saturation");
    }
}
