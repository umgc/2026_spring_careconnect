package com.careconnect.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class USPSDigestCacheTest {

    // ─── Default constructor ──────────────────────────────────────────────────

    @Test
    void defaultConstructor_createsInstance() throws Exception {
        final USPSDigestCache cache = new USPSDigestCache();
        assertThat(cache).isNotNull();
    }

    // ─── Setters / Getters ────────────────────────────────────────────────────

    @Test
    void settersAndGetters_updateAndReturnFields() throws Exception {
        final USPSDigestCache cache = new USPSDigestCache();
        final Instant digestDate = Instant.parse("2025-01-15T08:00:00Z");
        final Instant expiresAt = Instant.parse("2025-01-15T20:00:00Z");

        cache.setUserId("user-123");
        cache.setPayloadJson("{\"data\":\"test\"}");
        cache.setDigestDate(digestDate);
        cache.setExpiresAt(expiresAt);

        assertThat(cache.getUserId()).isEqualTo("user-123");
        assertThat(cache.getPayloadJson()).isEqualTo("{\"data\":\"test\"}");
        assertThat(cache.getDigestDate()).isEqualTo(digestDate);
        assertThat(cache.getExpiresAt()).isEqualTo(expiresAt);
    }

    @Test
    void getId_returnsNull_beforePersist() throws Exception {
        final USPSDigestCache cache = new USPSDigestCache();
        assertThat(cache.getId()).isNull();
    }
}
