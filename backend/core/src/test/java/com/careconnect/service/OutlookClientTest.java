package com.careconnect.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class OutlookClientTest {

    private OutlookClient client;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        client = new OutlookClient();
    }

    // ── fetchLatestDigest ────────────────────────────────────────────────────

    @Test
    @DisplayName("fetchLatestDigest_anyToken_returnsEmpty")
    void fetchLatestDigest_anyToken_returnsEmpty() throws Exception {
        final Optional<OutlookClient.OutlookRaw> result = client.fetchLatestDigest("some-access-token");

        assertTrue(result.isEmpty(), "stub should return Optional.empty()");
    }

    @Test
    @DisplayName("fetchLatestDigest_nullToken_returnsEmpty")
    void fetchLatestDigest_nullToken_returnsEmpty() throws Exception {
        final Optional<OutlookClient.OutlookRaw> result = client.fetchLatestDigest(null);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("fetchLatestDigest_blankToken_returnsEmpty")
    void fetchLatestDigest_blankToken_returnsEmpty() throws Exception {
        final Optional<OutlookClient.OutlookRaw> result = client.fetchLatestDigest("");

        assertTrue(result.isEmpty());
    }

    // ── fetchDigestForDate ───────────────────────────────────────────────────

    @Test
    @DisplayName("fetchDigestForDate_anyTokenAndDate_returnsEmpty")
    void fetchDigestForDate_anyTokenAndDate_returnsEmpty() throws Exception {
        final Optional<OutlookClient.OutlookRaw> result =
                client.fetchDigestForDate("token-123", LocalDate.of(2026, 2, 27));

        assertTrue(result.isEmpty(), "stub should return Optional.empty()");
    }

    @Test
    @DisplayName("fetchDigestForDate_nullToken_returnsEmpty")
    void fetchDigestForDate_nullToken_returnsEmpty() throws Exception {
        final Optional<OutlookClient.OutlookRaw> result =
                client.fetchDigestForDate(null, LocalDate.of(2026, 1, 15));

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("fetchDigestForDate_nullDate_returnsEmpty")
    void fetchDigestForDate_nullDate_returnsEmpty() throws Exception {
        final Optional<OutlookClient.OutlookRaw> result =
                client.fetchDigestForDate("token", null);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("fetchDigestForDate_bothNull_returnsEmpty")
    void fetchDigestForDate_bothNull_returnsEmpty() throws Exception {
        final Optional<OutlookClient.OutlookRaw> result =
                client.fetchDigestForDate(null, null);

        assertTrue(result.isEmpty());
    }

    // ── OutlookRaw record ────────────────────────────────────────────────────

    @Test
    @DisplayName("outlookRaw_constructor_storesAllFields")
    void outlookRaw_constructor_storesAllFields() throws Exception {
        final Instant now = Instant.now();
        final Map<String, String> cids = Map.of("cid1", "data:image/png;base64,abc");

        final OutlookClient.OutlookRaw raw = new OutlookClient.OutlookRaw("<html></html>", cids, now);

        assertEquals("<html></html>", raw.html());
        assertEquals(cids, raw.cidDataUrls());
        assertEquals(now, raw.received());
    }

    @Test
    @DisplayName("outlookRaw_nullFields_returnsNulls")
    void outlookRaw_nullFields_returnsNulls() throws Exception {
        final OutlookClient.OutlookRaw raw = new OutlookClient.OutlookRaw(null, null, null);

        assertNull(raw.html());
        assertNull(raw.cidDataUrls());
        assertNull(raw.received());
    }

    @Test
    @DisplayName("outlookRaw_emptyValues_returnsEmptyValues")
    void outlookRaw_emptyValues_returnsEmptyValues() throws Exception {
        final Instant now = Instant.parse("2026-02-27T12:00:00Z");
        final Map<String, String> emptyCids = Map.of();

        final OutlookClient.OutlookRaw raw = new OutlookClient.OutlookRaw("", emptyCids, now);

        assertEquals("", raw.html());
        assertTrue(raw.cidDataUrls().isEmpty());
        assertEquals(now, raw.received());
    }

    @Test
    @DisplayName("outlookRaw_identicalRecords_areEqual")
    void outlookRaw_identicalRecords_areEqual() throws Exception {
        final Instant now = Instant.parse("2026-01-01T00:00:00Z");
        final Map<String, String> cids = Map.of("k", "v");

        final OutlookClient.OutlookRaw raw1 = new OutlookClient.OutlookRaw("html", cids, now);
        final OutlookClient.OutlookRaw raw2 = new OutlookClient.OutlookRaw("html", cids, now);

        assertEquals(raw1, raw2);
        assertEquals(raw1.hashCode(), raw2.hashCode());
    }

    @Test
    @DisplayName("outlookRaw_differentRecords_notEqual")
    void outlookRaw_differentRecords_notEqual() throws Exception {
        final Instant now = Instant.now();

        final OutlookClient.OutlookRaw raw1 = new OutlookClient.OutlookRaw("html1", Map.of(), now);
        final OutlookClient.OutlookRaw raw2 = new OutlookClient.OutlookRaw("html2", Map.of(), now);

        assertNotEquals(raw1, raw2);
    }

    @Test
    @DisplayName("outlookRaw_toString_containsFieldValues")
    void outlookRaw_toString_containsFieldValues() throws Exception {
        final Instant now = Instant.parse("2026-02-27T10:00:00Z");
        final OutlookClient.OutlookRaw raw = new OutlookClient.OutlookRaw("<b>test</b>", Map.of(), now);

        final String str = raw.toString();
        assertNotNull(str);
        assertTrue(str.contains("test"));
    }

    @Test
    @DisplayName("outlookRaw_differentCidMaps_notEqual")
    void outlookRaw_differentCidMaps_notEqual() throws Exception {
        final Instant now = Instant.now();
        final OutlookClient.OutlookRaw raw1 = new OutlookClient.OutlookRaw("html", Map.of("k", "v1"), now);
        final OutlookClient.OutlookRaw raw2 = new OutlookClient.OutlookRaw("html", Map.of("k", "v2"), now);

        assertNotEquals(raw1, raw2);
    }

    @Test
    @DisplayName("outlookRaw_differentReceivedInstant_notEqual")
    void outlookRaw_differentReceivedInstant_notEqual() throws Exception {
        final Instant t1 = Instant.parse("2026-01-01T00:00:00Z");
        final Instant t2 = Instant.parse("2026-01-02T00:00:00Z");

        final OutlookClient.OutlookRaw raw1 = new OutlookClient.OutlookRaw("html", Map.of(), t1);
        final OutlookClient.OutlookRaw raw2 = new OutlookClient.OutlookRaw("html", Map.of(), t2);

        assertNotEquals(raw1, raw2);
    }
}
