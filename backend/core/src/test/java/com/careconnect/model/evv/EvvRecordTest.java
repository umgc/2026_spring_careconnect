package com.careconnect.model.evv;

import com.careconnect.model.Patient;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class EvvRecordTest {

    // ─── No-arg constructor ───────────────────────────────────────────────────

    @Test
    void noArgConstructor_createsInstance() throws Exception {
        final EvvRecord r = new EvvRecord();

        assertThat(r).isNotNull();
        assertThat(r.getId()).isNull();
        assertThat(r.getPatient()).isNull();
        assertThat(r.getServiceType()).isNull();
        assertThat(r.getIndividualName()).isNull();
        assertThat(r.getCaregiverId()).isNull();
        assertThat(r.getDateOfService()).isNull();
        assertThat(r.getTimeIn()).isNull();
        assertThat(r.getTimeOut()).isNull();
        assertThat(r.getStatus()).isNull();
        assertThat(r.getStateCode()).isNull();
        // plain field initializers – applied in no-arg ctor
        assertThat(r.getIsOffline()).isFalse();
        assertThat(r.getEorApprovalRequired()).isFalse();
        assertThat(r.getIsCorrected()).isFalse();
    }

    // ─── Builder all fields ───────────────────────────────────────────────────

    @Test
    void builder_allFields() throws Exception {
        final Patient patient = new Patient();
        final OffsetDateTime now = OffsetDateTime.now();
        final LocalDate today = LocalDate.now();

        final EvvRecord r = EvvRecord.builder()
                .id(1L)
                .patient(patient)
                .serviceType("PERSONAL_CARE")
                .individualName("John Doe")
                .caregiverId(5L)
                .scheduledVisitId(100L)
                .dateOfService(today)
                .timeIn(now.minusHours(2))
                .timeOut(now)
                .locationLat(39.2904)
                .locationLng(-76.6122)
                .locationSource("gps")
                .status("APPROVED")
                .stateCode("MD")
                .isOffline(false)
                .eorApprovalRequired(false)
                .isCorrected(false)
                .createdAt(now)
                .updatedAt(now)
                .build();

        assertThat(r.getId()).isEqualTo(1L);
        assertThat(r.getPatient()).isSameAs(patient);
        assertThat(r.getServiceType()).isEqualTo("PERSONAL_CARE");
        assertThat(r.getIndividualName()).isEqualTo("John Doe");
        assertThat(r.getCaregiverId()).isEqualTo(5L);
        assertThat(r.getScheduledVisitId()).isEqualTo(100L);
        assertThat(r.getDateOfService()).isEqualTo(today);
        assertThat(r.getTimeIn()).isEqualTo(now.minusHours(2));
        assertThat(r.getTimeOut()).isEqualTo(now);
        assertThat(r.getLocationLat()).isEqualTo(39.2904);
        assertThat(r.getLocationLng()).isEqualTo(-76.6122);
        assertThat(r.getLocationSource()).isEqualTo("gps");
        assertThat(r.getStatus()).isEqualTo("APPROVED");
        assertThat(r.getStateCode()).isEqualTo("MD");
    }

    // ─── Status-change methods ─────────────────────────────────────────────────

    @Test
    void markUnderReview_setsStatus() throws Exception {
        final EvvRecord r = new EvvRecord();
        r.setUpdatedAt(OffsetDateTime.now().minusHours(1));

        r.markUnderReview();

        assertThat(r.getStatus()).isEqualTo("UNDER_REVIEW");
        assertThat(r.getUpdatedAt()).isNotNull();
    }

    @Test
    void markApproved_setsStatus() throws Exception {
        final EvvRecord r = new EvvRecord();
        r.setUpdatedAt(OffsetDateTime.now().minusHours(1));

        r.markApproved();

        assertThat(r.getStatus()).isEqualTo("APPROVED");
    }

    @Test
    void markRejected_setsStatus() throws Exception {
        final EvvRecord r = new EvvRecord();
        r.setUpdatedAt(OffsetDateTime.now().minusHours(1));

        r.markRejected();

        assertThat(r.getStatus()).isEqualTo("REJECTED");
    }

    // ─── Offline sync methods ─────────────────────────────────────────────────

    @Test
    void markOffline_setsOfflineAndPendingSync() throws Exception {
        final EvvRecord r = new EvvRecord();
        r.setUpdatedAt(OffsetDateTime.now().minusHours(1));

        r.markOffline();

        assertThat(r.getIsOffline()).isTrue();
        assertThat(r.getSyncStatus()).isEqualTo("PENDING");
    }

    @Test
    void markSynced_clearsOfflineFlag() throws Exception {
        final EvvRecord r = new EvvRecord();
        r.setUpdatedAt(OffsetDateTime.now().minusHours(1));

        r.markSynced();

        assertThat(r.getIsOffline()).isFalse();
        assertThat(r.getSyncStatus()).isEqualTo("SYNCED");
    }

    @Test
    void markSyncFailed_setsFailedStatus() throws Exception {
        final EvvRecord r = new EvvRecord();
        r.setUpdatedAt(OffsetDateTime.now().minusHours(1));

        r.markSyncFailed();

        assertThat(r.getSyncStatus()).isEqualTo("FAILED");
        assertThat(r.getLastSyncAttempt()).isNotNull();
    }

    // ─── EOR approval ────────────────────────────────────────────────────────

    @Test
    void approveEor_setsApprovalFields() throws Exception {
        final EvvRecord r = new EvvRecord();
        r.setUpdatedAt(OffsetDateTime.now().minusHours(1));

        r.approveEor(10L, "Looks good");

        assertThat(r.getEorApprovedBy()).isEqualTo(10L);
        assertThat(r.getEorApprovedAt()).isNotNull();
        assertThat(r.getEorApprovalComment()).isEqualTo("Looks good");
    }

    // ─── correctRecord() ─────────────────────────────────────────────────────

    @Test
    void correctRecord_setsCorrectionFields() throws Exception {
        final EvvRecord r = new EvvRecord();
        r.setUpdatedAt(OffsetDateTime.now().minusHours(1));

        r.correctRecord(5L, "LATE_ENTRY", "Entered late", 42L);

        assertThat(r.getIsCorrected()).isTrue();
        assertThat(r.getCorrectedBy()).isEqualTo(5L);
        assertThat(r.getCorrectedAt()).isNotNull();
        assertThat(r.getCorrectionReasonCode()).isEqualTo("LATE_ENTRY");
        assertThat(r.getCorrectionExplanation()).isEqualTo("Entered late");
        assertThat(r.getOriginalRecordId()).isEqualTo(42L);
    }

    // ─── @Transient location fields ───────────────────────────────────────────

    @Test
    void transientLocationFields_setAndGet() throws Exception {
        final EvvRecord r = new EvvRecord();

        r.setCheckinLocationLat(39.2904);
        r.setCheckinLocationLng(-76.6122);
        r.setCheckinLocationSource("gps");
        r.setCheckoutLocationLat(39.2910);
        r.setCheckoutLocationLng(-76.6130);
        r.setCheckoutLocationSource("manual");

        assertThat(r.getCheckinLocationLat()).isEqualTo(39.2904);
        assertThat(r.getCheckinLocationLng()).isEqualTo(-76.6122);
        assertThat(r.getCheckinLocationSource()).isEqualTo("gps");
        assertThat(r.getCheckoutLocationLat()).isEqualTo(39.2910);
        assertThat(r.getCheckoutLocationLng()).isEqualTo(-76.6130);
        assertThat(r.getCheckoutLocationSource()).isEqualTo("manual");
    }
}
