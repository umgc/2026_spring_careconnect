package com.careconnect.service.evv;

import com.careconnect.model.evv.EvvRecord;
import com.careconnect.repository.evv.EvvRecordRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EvvSubmissionServiceTest {

    @Mock EvvIntegrationClient  client1;
    @Mock EvvOutboxService      outbox;
    @Mock EvvRecordRepository   evvRecordRepository;
    @Mock AuditLogger           audit;

    @InjectMocks EvvSubmissionService service;

    // The @InjectMocks injects `clients` as a List via field injection.
    // We need to set it explicitly since Spring normally auto-collects them.
    private EvvSubmissionService serviceWithClients() throws Exception {
        return new EvvSubmissionService(List.of(client1), outbox, evvRecordRepository, audit);
    }

    // ─── destinationFor() ────────────────────────────────────────────────────

    @Test
    void destinationFor_maryland_returnsMarylandInfoOnly() throws Exception {
        assertThat(serviceWithClients().destinationFor("MD")).isEqualTo("maryland-info-only");
    }

    @Test
    void destinationFor_dc_returnsDcSandata() throws Exception {
        assertThat(serviceWithClients().destinationFor("DC")).isEqualTo("dc-sandata");
    }

    @Test
    void destinationFor_virginia_returnsVirginiaMco() throws Exception {
        assertThat(serviceWithClients().destinationFor("VA")).isEqualTo("virginia-mco");
    }

    @Test
    void destinationFor_lowercase_mapsCorrectly() throws Exception {
        assertThat(serviceWithClients().destinationFor("md")).isEqualTo("maryland-info-only");
    }

    @Test
    void destinationFor_unsupportedState_throwsIllegalArgument() throws Exception {
        assertThatThrownBy(() -> serviceWithClients().destinationFor("TX"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported state code: TX");
    }

    // ─── queueForSubmission() ─────────────────────────────────────────────────

    @Test
    void queueForSubmission_callsOutboxEnqueueAndAuditLog() throws Exception {
        final EvvRecord record = mock(EvvRecord.class);
        when(record.getStateCode()).thenReturn("MD");

        final EvvSubmissionService svc = serviceWithClients();
        svc.queueForSubmission(record, 7L);

        verify(outbox).enqueue(record, "maryland-info-only");
        verify(audit).log(eq(record), eq(7L), eq("SUBMISSION_QUEUED"), eq(Map.of()));
    }
}
