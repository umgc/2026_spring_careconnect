package com.careconnect.model;

import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class FriendRequestTest {

    // ─── Default constructor ──────────────────────────────────────────────────

    @Test
    void defaultConstructor_createsInstance() throws Exception {
        final FriendRequest req = new FriendRequest();

        assertThat(req).isNotNull();
        assertThat(req.getId()).isNull();
        assertThat(req.getFromUserId()).isNull();
        assertThat(req.getToUserId()).isNull();
        assertThat(req.getStatus()).isNull();
        assertThat(req.getCreatedAt()).isNull();
    }

    // ─── Setters / Getters ────────────────────────────────────────────────────

    @Test
    void settersAndGetters_updateFields() throws Exception {
        final FriendRequest req = new FriendRequest();
        final Date now = new Date();

        req.setId(1L);
        req.setFromUserId(100L);
        req.setToUserId(200L);
        req.setStatus("pending");
        req.setCreatedAt(now);

        assertThat(req.getId()).isEqualTo(1L);
        assertThat(req.getFromUserId()).isEqualTo(100L);
        assertThat(req.getToUserId()).isEqualTo(200L);
        assertThat(req.getStatus()).isEqualTo("pending");
        assertThat(req.getCreatedAt()).isEqualTo(now);
    }
}
