package com.careconnect.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectionRequestTest {

    // ─── No-arg constructor ───────────────────────────────────────────────────

    @Test
    void noArgConstructor_createsInstance() throws Exception {
        final ConnectionRequest req = new ConnectionRequest();

        assertThat(req).isNotNull();
        assertThat(req.getId()).isNull();
        assertThat(req.getCaregiver()).isNull();
        assertThat(req.getPatient()).isNull();
        assertThat(req.getStatus()).isNull();
        assertThat(req.getRelationshipType()).isNull();
        assertThat(req.getMessage()).isNull();
        assertThat(req.getRequestedAt()).isNull();
        assertThat(req.getRespondedAt()).isNull();
        assertThat(req.getToken()).isNull();
    }

    // ─── Builder ──────────────────────────────────────────────────────────────

    @Test
    void builder_setsFields() throws Exception {
        final User caregiver = new User();
        final User patient = new User();
        final Instant now = Instant.now();

        final ConnectionRequest req = ConnectionRequest.builder()
                .id(1L)
                .caregiver(caregiver)
                .patient(patient)
                .status("PENDING")
                .relationshipType("CAREGIVER")
                .message("Please connect")
                .requestedAt(now)
                .respondedAt(null)
                .token("tok-abc")
                .build();

        assertThat(req.getId()).isEqualTo(1L);
        assertThat(req.getCaregiver()).isSameAs(caregiver);
        assertThat(req.getPatient()).isSameAs(patient);
        assertThat(req.getStatus()).isEqualTo("PENDING");
        assertThat(req.getRelationshipType()).isEqualTo("CAREGIVER");
        assertThat(req.getMessage()).isEqualTo("Please connect");
        assertThat(req.getRequestedAt()).isEqualTo(now);
        assertThat(req.getToken()).isEqualTo("tok-abc");
    }

    // ─── Setters ──────────────────────────────────────────────────────────────

    @Test
    void setters_updateFields() throws Exception {
        final ConnectionRequest req = new ConnectionRequest();
        final User caregiver = new User();
        final User patient = new User();
        final Instant now = Instant.now();

        req.setId(2L);
        req.setCaregiver(caregiver);
        req.setPatient(patient);
        req.setStatus("ACCEPTED");
        req.setRelationshipType("FAMILY");
        req.setMessage("Accepted");
        req.setRequestedAt(now);
        req.setRespondedAt(now);
        req.setToken("tok-xyz");

        assertThat(req.getId()).isEqualTo(2L);
        assertThat(req.getCaregiver()).isSameAs(caregiver);
        assertThat(req.getPatient()).isSameAs(patient);
        assertThat(req.getStatus()).isEqualTo("ACCEPTED");
        assertThat(req.getRelationshipType()).isEqualTo("FAMILY");
        assertThat(req.getMessage()).isEqualTo("Accepted");
        assertThat(req.getRequestedAt()).isEqualTo(now);
        assertThat(req.getRespondedAt()).isEqualTo(now);
        assertThat(req.getToken()).isEqualTo("tok-xyz");
    }
}
