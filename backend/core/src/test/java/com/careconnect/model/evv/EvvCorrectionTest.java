package com.careconnect.model.evv;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EvvCorrectionTest {

    // ─── No-arg constructor ───────────────────────────────────────────────────

    @Test
    void noArgConstructor_createsInstance() throws Exception {
        final EvvCorrection ec = new EvvCorrection();

        assertThat(ec).isNotNull();
        assertThat(ec.getId()).isNull();
        assertThat(ec.getOriginalRecord()).isNull();
        assertThat(ec.getCorrectedRecord()).isNull();
        assertThat(ec.getReasonCode()).isNull();
        assertThat(ec.getExplanation()).isNull();
        assertThat(ec.getCorrectedBy()).isNull();
        assertThat(ec.getCorrectedAt()).isNull();
        // plain field initializer (not @Builder.Default) – applied in no-arg ctor
        assertThat(ec.getApprovalRequired()).isFalse();
    }

    // ─── Builder all fields ───────────────────────────────────────────────────

    @Test
    void builder_allFields() throws Exception {
        final EvvRecord original = new EvvRecord();
        final EvvRecord corrected = new EvvRecord();
        final OffsetDateTime now = OffsetDateTime.now();
        final Map<String, Object> origValues = new HashMap<>();
        origValues.put("timeIn", "09:00");
        final Map<String, Object> corrValues = new HashMap<>();
        corrValues.put("timeIn", "09:30");

        final EvvCorrection ec = EvvCorrection.builder()
                .id(1L)
                .originalRecord(original)
                .correctedRecord(corrected)
                .reasonCode("LATE_START")
                .explanation("Caregiver arrived late")
                .correctedBy(5L)
                .correctedAt(now)
                .approvalRequired(true)
                .approvedBy(null)
                .approvedAt(null)
                .approvalComment(null)
                .originalValues(origValues)
                .correctedValues(corrValues)
                .build();

        assertThat(ec.getId()).isEqualTo(1L);
        assertThat(ec.getOriginalRecord()).isSameAs(original);
        assertThat(ec.getCorrectedRecord()).isSameAs(corrected);
        assertThat(ec.getReasonCode()).isEqualTo("LATE_START");
        assertThat(ec.getExplanation()).isEqualTo("Caregiver arrived late");
        assertThat(ec.getCorrectedBy()).isEqualTo(5L);
        assertThat(ec.getCorrectedAt()).isEqualTo(now);
        assertThat(ec.getApprovalRequired()).isTrue();
        assertThat(ec.getOriginalValues()).containsEntry("timeIn", "09:00");
        assertThat(ec.getCorrectedValues()).containsEntry("timeIn", "09:30");
    }

    // ─── onCreate() ───────────────────────────────────────────────────────────

    @Test
    void onCreate_setsCorrectedAtWhenNull() throws Exception {
        final EvvCorrection ec = new EvvCorrection();
        assertThat(ec.getCorrectedAt()).isNull();

        final Method m = EvvCorrection.class.getDeclaredMethod("onCreate");
        m.setAccessible(true);
        m.invoke(ec);

        assertThat(ec.getCorrectedAt()).isNotNull();
    }

    @Test
    void onCreate_doesNotOverwriteExistingCorrectedAt() throws Exception {
        final EvvCorrection ec = new EvvCorrection();
        final OffsetDateTime original = OffsetDateTime.now().minusDays(1);
        ec.setCorrectedAt(original);

        final Method m = EvvCorrection.class.getDeclaredMethod("onCreate");
        m.setAccessible(true);
        m.invoke(ec);

        assertThat(ec.getCorrectedAt()).isEqualTo(original);
    }

    // ─── approve() ───────────────────────────────────────────────────────────

    @Test
    void approve_setsApprovalFields() throws Exception {
        final EvvCorrection ec = new EvvCorrection();

        ec.approve(10L, "Looks correct");

        assertThat(ec.getApprovedBy()).isEqualTo(10L);
        assertThat(ec.getApprovedAt()).isNotNull();
        assertThat(ec.getApprovalComment()).isEqualTo("Looks correct");
    }

    // ─── reject() ────────────────────────────────────────────────────────────

    @Test
    void reject_setsRejectionFields() throws Exception {
        final EvvCorrection ec = new EvvCorrection();
        ec.setApprovalRequired(true);

        ec.reject(20L, "Incorrect times");

        assertThat(ec.getApprovedBy()).isEqualTo(20L);
        assertThat(ec.getApprovedAt()).isNotNull();
        assertThat(ec.getApprovalComment()).isEqualTo("Incorrect times");
        assertThat(ec.getApprovalRequired()).isFalse();
    }
}
