package com.careconnect.model.invoice;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class HistoryEntryTest {

    // ─── No-arg constructor ───────────────────────────────────────────────────

    @Test
    void noArgConstructor_createsInstance() throws Exception {
        final HistoryEntry entry = new HistoryEntry();

        assertThat(entry).isNotNull();
        assertThat(entry.getId()).isNull();
        assertThat(entry.getInvoice()).isNull();
        assertThat(entry.getVersion()).isZero();
        assertThat(entry.getChanges()).isNull();
        assertThat(entry.getUserId()).isNull();
        assertThat(entry.getAction()).isNull();
        assertThat(entry.getDetails()).isNull();
        assertThat(entry.getTimestamp()).isNull();
    }

    // ─── All-arg constructor ──────────────────────────────────────────────────

    @Test
    void allArgConstructor_setsAllFields() throws Exception {
        final Invoice invoice = Invoice.builder().id("INV-100").build();
        final OffsetDateTime timestamp = OffsetDateTime.of(2025, 5, 1, 10, 0, 0, 0, ZoneOffset.UTC);

        final HistoryEntry entry = new HistoryEntry(
                1L, invoice, 3, "{\"status\":\"paid\"}", "user-42", "STATUS_UPDATE",
                "Status changed from pending to paid", timestamp
        );

        assertThat(entry.getId()).isEqualTo(1L);
        assertThat(entry.getInvoice()).isSameAs(invoice);
        assertThat(entry.getVersion()).isEqualTo(3);
        assertThat(entry.getChanges()).isEqualTo("{\"status\":\"paid\"}");
        assertThat(entry.getUserId()).isEqualTo("user-42");
        assertThat(entry.getAction()).isEqualTo("STATUS_UPDATE");
        assertThat(entry.getDetails()).isEqualTo("Status changed from pending to paid");
        assertThat(entry.getTimestamp()).isEqualTo(timestamp);
    }

    // ─── Setters ──────────────────────────────────────────────────────────────

    @Test
    void setters_updateFields() throws Exception {
        final HistoryEntry entry = new HistoryEntry();
        final Invoice invoice = Invoice.builder().id("INV-200").build();
        final OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        entry.setId(10L);
        entry.setInvoice(invoice);
        entry.setVersion(2);
        entry.setChanges("{\"amount\":500}");
        entry.setUserId("admin-1");
        entry.setAction("AMOUNT_UPDATE");
        entry.setDetails("Amount updated");
        entry.setTimestamp(now);

        assertThat(entry.getId()).isEqualTo(10L);
        assertThat(entry.getInvoice()).isSameAs(invoice);
        assertThat(entry.getVersion()).isEqualTo(2);
        assertThat(entry.getChanges()).isEqualTo("{\"amount\":500}");
        assertThat(entry.getUserId()).isEqualTo("admin-1");
        assertThat(entry.getAction()).isEqualTo("AMOUNT_UPDATE");
        assertThat(entry.getDetails()).isEqualTo("Amount updated");
        assertThat(entry.getTimestamp()).isEqualTo(now);
    }

    // ─── equals() and hashCode() ──────────────────────────────────────────────

    @Test
    void equals_sameFields_returnsTrue() throws Exception {
        final HistoryEntry e1 = new HistoryEntry(1L, null, 1, null, "user-1", "CREATE", null, null);
        final HistoryEntry e2 = new HistoryEntry(1L, null, 1, null, "user-1", "CREATE", null, null);

        assertThat(e1).isEqualTo(e2);
        assertThat(e1.hashCode()).isEqualTo(e2.hashCode());
    }

    @Test
    void equals_differentFields_returnsFalse() throws Exception {
        final HistoryEntry e1 = new HistoryEntry(1L, null, 1, null, "user-1", "CREATE", null, null);
        final HistoryEntry e2 = new HistoryEntry(2L, null, 2, null, "user-2", "UPDATE", null, null);

        assertThat(e1).isNotEqualTo(e2);
    }

    @Test
    void equals_null_returnsFalse() throws Exception {
        final HistoryEntry entry = new HistoryEntry();
        assertThat(entry).isNotEqualTo(null);
    }

    @Test
    void equals_differentType_returnsFalse() throws Exception {
        final HistoryEntry entry = new HistoryEntry();
        assertThat(entry).isNotEqualTo("a string");
    }
}
