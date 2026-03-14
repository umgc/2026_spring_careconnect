package com.careconnect.service;

import com.careconnect.model.USPSDigest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OutlookParserTest {

    private OutlookParser outlookParser;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        outlookParser = new OutlookParser();
    }

    // ========== toDomain tests ==========

    @Test
    @DisplayName("toDomain_validRaw_returnsUSPSDigestWithCorrectDate")
    void toDomain_validRaw_returnsUSPSDigestWithCorrectDate() throws Exception {
        final Instant received = Instant.parse("2026-02-27T10:30:00Z");
        final OutlookClient.OutlookRaw raw = new OutlookClient.OutlookRaw(
                "<html>digest content</html>",
                Map.of("cid1", "data:image/png;base64,abc"),
                received
        );

        final USPSDigest result = outlookParser.toDomain(raw);

        assertNotNull(result);
        final OffsetDateTime expectedDate = received.atOffset(ZoneOffset.UTC);
        assertEquals(expectedDate, result.digestDate());
        assertNotNull(result.mailpieces());
        assertTrue(result.mailpieces().isEmpty());
        assertNotNull(result.packages());
        assertTrue(result.packages().isEmpty());
    }

    @Test
    @DisplayName("toDomain_rawWithEmptyHtml_returnsUSPSDigestWithEmptyLists")
    void toDomain_rawWithEmptyHtml_returnsUSPSDigestWithEmptyLists() throws Exception {
        final Instant received = Instant.parse("2026-01-15T08:00:00Z");
        final OutlookClient.OutlookRaw raw = new OutlookClient.OutlookRaw(
                "",
                Map.of(),
                received
        );

        final USPSDigest result = outlookParser.toDomain(raw);

        assertNotNull(result);
        assertEquals(received.atOffset(ZoneOffset.UTC), result.digestDate());
        assertTrue(result.mailpieces().isEmpty());
        assertTrue(result.packages().isEmpty());
    }

    @Test
    @DisplayName("toDomain_rawWithNullHtml_returnsUSPSDigestSuccessfully")
    void toDomain_rawWithNullHtml_returnsUSPSDigestSuccessfully() throws Exception {
        final Instant received = Instant.parse("2026-03-01T14:45:00Z");
        final OutlookClient.OutlookRaw raw = new OutlookClient.OutlookRaw(
                null,
                null,
                received
        );

        final USPSDigest result = outlookParser.toDomain(raw);

        assertNotNull(result);
        assertEquals(received.atOffset(ZoneOffset.UTC), result.digestDate());
        assertTrue(result.mailpieces().isEmpty());
        assertTrue(result.packages().isEmpty());
    }

    @Test
    @DisplayName("toDomain_rawWithEpochReceived_returnsDigestAtEpoch")
    void toDomain_rawWithEpochReceived_returnsDigestAtEpoch() throws Exception {
        final Instant epoch = Instant.EPOCH;
        final OutlookClient.OutlookRaw raw = new OutlookClient.OutlookRaw(
                "<html></html>",
                Map.of(),
                epoch
        );

        final USPSDigest result = outlookParser.toDomain(raw);

        assertNotNull(result);
        final OffsetDateTime expectedDate = epoch.atOffset(ZoneOffset.UTC);
        assertEquals(expectedDate, result.digestDate());
        assertEquals(1970, result.digestDate().getYear());
        assertEquals(1, result.digestDate().getMonthValue());
        assertEquals(1, result.digestDate().getDayOfMonth());
    }

    @Test
    @DisplayName("toDomain_rawWithMultipleCids_returnsDigestIgnoringCids")
    void toDomain_rawWithMultipleCids_returnsDigestIgnoringCids() throws Exception {
        final Instant received = Instant.parse("2026-06-15T20:00:00Z");
        final Map<String, String> cids = Map.of(
                "cid1", "data:image/png;base64,abc",
                "cid2", "data:image/jpeg;base64,xyz",
                "cid3", "data:image/gif;base64,123"
        );
        final OutlookClient.OutlookRaw raw = new OutlookClient.OutlookRaw(
                "<html><img src='cid:cid1'/></html>",
                cids,
                received
        );

        final USPSDigest result = outlookParser.toDomain(raw);

        assertNotNull(result);
        assertEquals(received.atOffset(ZoneOffset.UTC), result.digestDate());
        assertTrue(result.mailpieces().isEmpty());
        assertTrue(result.packages().isEmpty());
    }

    @Test
    @DisplayName("toDomain_rawReceivedDatePreservesUtcOffset_returnsZeroOffset")
    void toDomain_rawReceivedDatePreservesUtcOffset_returnsZeroOffset() throws Exception {
        final Instant received = Instant.parse("2026-12-31T23:59:59Z");
        final OutlookClient.OutlookRaw raw = new OutlookClient.OutlookRaw(
                "<html>year end</html>",
                Map.of(),
                received
        );

        final USPSDigest result = outlookParser.toDomain(raw);

        assertNotNull(result);
        assertEquals(ZoneOffset.UTC, result.digestDate().getOffset());
        assertEquals(2026, result.digestDate().getYear());
        assertEquals(12, result.digestDate().getMonthValue());
        assertEquals(31, result.digestDate().getDayOfMonth());
        assertEquals(23, result.digestDate().getHour());
        assertEquals(59, result.digestDate().getMinute());
        assertEquals(59, result.digestDate().getSecond());
    }

    @Test
    @DisplayName("toDomain_rawWithRecentInstant_returnsDigestWithMatchingTimestamp")
    void toDomain_rawWithRecentInstant_returnsDigestWithMatchingTimestamp() throws Exception {
        final Instant now = Instant.now();
        final OutlookClient.OutlookRaw raw = new OutlookClient.OutlookRaw(
                "<div>recent digest</div>",
                Map.of(),
                now
        );

        final USPSDigest result = outlookParser.toDomain(raw);

        assertNotNull(result);
        assertEquals(now.atOffset(ZoneOffset.UTC), result.digestDate());
    }
}
