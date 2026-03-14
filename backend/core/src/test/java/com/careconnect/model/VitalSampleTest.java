package com.careconnect.model;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class VitalSampleTest {

    // ─── No-arg constructor ───────────────────────────────────────────────────

    @Test
    void noArgConstructor_createsInstance() throws Exception {
        final VitalSample vs = new VitalSample();

        assertThat(vs).isNotNull();
        assertThat(vs.getId()).isNull();
        assertThat(vs.getPatient()).isNull();
        assertThat(vs.getTimestamp()).isNull();
        assertThat(vs.getHeartRate()).isNull();
        assertThat(vs.getSpo2()).isNull();
        assertThat(vs.getSystolic()).isNull();
        assertThat(vs.getDiastolic()).isNull();
        assertThat(vs.getWeight()).isNull();
        assertThat(vs.getMoodValue()).isNull();
        assertThat(vs.getPainValue()).isNull();
        assertThat(vs.getCreatedAt()).isNull();
        assertThat(vs.getUpdatedAt()).isNull();
    }

    // ─── Builder all fields ───────────────────────────────────────────────────

    @Test
    void builder_allFields() throws Exception {
        final Patient patient = new Patient();
        final Instant now = Instant.now();

        final VitalSample vs = VitalSample.builder()
                .id(1L)
                .patient(patient)
                .timestamp(now)
                .heartRate(72.0)
                .spo2(98.5)
                .systolic(120)
                .diastolic(80)
                .weight(70.5)
                .moodValue(7)
                .painValue(2)
                .createdAt(now)
                .updatedAt(now)
                .build();

        assertThat(vs.getId()).isEqualTo(1L);
        assertThat(vs.getPatient()).isSameAs(patient);
        assertThat(vs.getTimestamp()).isEqualTo(now);
        assertThat(vs.getHeartRate()).isEqualTo(72.0);
        assertThat(vs.getSpo2()).isEqualTo(98.5);
        assertThat(vs.getSystolic()).isEqualTo(120);
        assertThat(vs.getDiastolic()).isEqualTo(80);
        assertThat(vs.getWeight()).isEqualTo(70.5);
        assertThat(vs.getMoodValue()).isEqualTo(7);
        assertThat(vs.getPainValue()).isEqualTo(2);
        assertThat(vs.getCreatedAt()).isEqualTo(now);
        assertThat(vs.getUpdatedAt()).isEqualTo(now);
    }

    // ─── Setters ──────────────────────────────────────────────────────────────

    @Test
    void setters_updateFields() throws Exception {
        final VitalSample vs = new VitalSample();
        final Instant now = Instant.now();

        vs.setId(2L);
        vs.setHeartRate(80.0);
        vs.setSpo2(97.0);
        vs.setSystolic(130);
        vs.setDiastolic(85);
        vs.setTimestamp(now);
        vs.setMoodValue(6);
        vs.setPainValue(3);

        assertThat(vs.getId()).isEqualTo(2L);
        assertThat(vs.getHeartRate()).isEqualTo(80.0);
        assertThat(vs.getSpo2()).isEqualTo(97.0);
        assertThat(vs.getSystolic()).isEqualTo(130);
        assertThat(vs.getDiastolic()).isEqualTo(85);
        assertThat(vs.getTimestamp()).isEqualTo(now);
        assertThat(vs.getMoodValue()).isEqualTo(6);
        assertThat(vs.getPainValue()).isEqualTo(3);
    }

    // ─── onCreate() ───────────────────────────────────────────────────────────

    @Test
    void onCreate_setsCreatedAtAndUpdatedAt() throws Exception {
        final VitalSample vs = new VitalSample();

        final Method m = VitalSample.class.getDeclaredMethod("onCreate");
        m.setAccessible(true);
        m.invoke(vs);

        assertThat(vs.getCreatedAt()).isNotNull();
        assertThat(vs.getUpdatedAt()).isNotNull();
    }

    // ─── onUpdate() ───────────────────────────────────────────────────────────

    @Test
    void onUpdate_refreshesUpdatedAt() throws Exception {
        final VitalSample vs = new VitalSample();
        vs.setUpdatedAt(Instant.now().minusSeconds(60));
        final Instant before = vs.getUpdatedAt();

        final Method m = VitalSample.class.getDeclaredMethod("onUpdate");
        m.setAccessible(true);
        m.invoke(vs);

        assertThat(vs.getUpdatedAt()).isAfter(before);
    }

    // ─── equals() and hashCode() ──────────────────────────────────────────────

    @Test
    void equals_sameFields_returnsTrue() throws Exception {
        final Instant now = Instant.now();
        final VitalSample vs1 = VitalSample.builder().id(1L).heartRate(72.0).timestamp(now).build();
        final VitalSample vs2 = VitalSample.builder().id(1L).heartRate(72.0).timestamp(now).build();

        assertThat(vs1).isEqualTo(vs2);
        assertThat(vs1.hashCode()).isEqualTo(vs2.hashCode());
    }
}
