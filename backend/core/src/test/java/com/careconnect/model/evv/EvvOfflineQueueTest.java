package com.careconnect.model.evv;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EvvOfflineQueueTest {

    // ─── No-arg constructor ───────────────────────────────────────────────────

    @Test
    void noArgConstructor_createsInstance() throws Exception {
        final EvvOfflineQueue q = new EvvOfflineQueue();

        assertThat(q).isNotNull();
        assertThat(q.getId()).isNull();
        assertThat(q.getRecordId()).isNull();
        assertThat(q.getOperationType()).isNull();
        assertThat(q.getCaregiverId()).isNull();
        assertThat(q.getDeviceId()).isNull();
        assertThat(q.getQueuedAt()).isNull();
        // plain field initializers – applied in no-arg ctor
        assertThat(q.getSyncAttempts()).isEqualTo(0);
        assertThat(q.getSyncStatus()).isEqualTo("PENDING");
        assertThat(q.getPriority()).isEqualTo(1);
    }

    // ─── Builder all fields ───────────────────────────────────────────────────

    @Test
    void builder_allFields() throws Exception {
        final OffsetDateTime now = OffsetDateTime.now();
        final Map<String, Object> recordData = new HashMap<>();
        recordData.put("serviceType", "PERSONAL_CARE");

        final EvvOfflineQueue q = EvvOfflineQueue.builder()
                .id(1L)
                .recordId(10L)
                .operationType("CREATE")
                .caregiverId(5L)
                .deviceId("device-abc")
                .queuedAt(now)
                .syncAttempts(0)
                .lastSyncAttempt(null)
                .syncStatus("PENDING")
                .lastError(null)
                .priority(2)
                .recordData(recordData)
                .build();

        assertThat(q.getId()).isEqualTo(1L);
        assertThat(q.getRecordId()).isEqualTo(10L);
        assertThat(q.getOperationType()).isEqualTo("CREATE");
        assertThat(q.getCaregiverId()).isEqualTo(5L);
        assertThat(q.getDeviceId()).isEqualTo("device-abc");
        assertThat(q.getQueuedAt()).isEqualTo(now);
        assertThat(q.getSyncAttempts()).isEqualTo(0);
        assertThat(q.getSyncStatus()).isEqualTo("PENDING");
        assertThat(q.getPriority()).isEqualTo(2);
        assertThat(q.getRecordData()).containsEntry("serviceType", "PERSONAL_CARE");
    }

    // ─── onCreate() ───────────────────────────────────────────────────────────

    @Test
    void onCreate_setsQueuedAtWhenNull() throws Exception {
        final EvvOfflineQueue q = new EvvOfflineQueue();
        q.setQueuedAt(null);

        final Method m = EvvOfflineQueue.class.getDeclaredMethod("onCreate");
        m.setAccessible(true);
        m.invoke(q);

        assertThat(q.getQueuedAt()).isNotNull();
    }

    @Test
    void onCreate_doesNotOverwriteExistingQueuedAt() throws Exception {
        final EvvOfflineQueue q = new EvvOfflineQueue();
        final OffsetDateTime original = OffsetDateTime.now().minusDays(1);
        q.setQueuedAt(original);

        final Method m = EvvOfflineQueue.class.getDeclaredMethod("onCreate");
        m.setAccessible(true);
        m.invoke(q);

        assertThat(q.getQueuedAt()).isEqualTo(original);
    }

    // ─── markSyncing() ────────────────────────────────────────────────────────

    @Test
    void markSyncing_incrementsAttemptsAndSetsStatus() throws Exception {
        final EvvOfflineQueue q = new EvvOfflineQueue();
        q.setSyncAttempts(0);

        q.markSyncing();

        assertThat(q.getSyncStatus()).isEqualTo("SYNCING");
        assertThat(q.getSyncAttempts()).isEqualTo(1);
        assertThat(q.getLastSyncAttempt()).isNotNull();
    }

    // ─── markSynced() ─────────────────────────────────────────────────────────

    @Test
    void markSynced_setsSyncedStatus() throws Exception {
        final EvvOfflineQueue q = new EvvOfflineQueue();

        q.markSynced();

        assertThat(q.getSyncStatus()).isEqualTo("SYNCED");
    }

    // ─── markFailed() ─────────────────────────────────────────────────────────

    @Test
    void markFailed_setsFailedStatusAndError() throws Exception {
        final EvvOfflineQueue q = new EvvOfflineQueue();

        q.markFailed("Network timeout");

        assertThat(q.getSyncStatus()).isEqualTo("FAILED");
        assertThat(q.getLastError()).isEqualTo("Network timeout");
        assertThat(q.getLastSyncAttempt()).isNotNull();
    }
}
