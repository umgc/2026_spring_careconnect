package com.careconnect.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class WebSocketConnectionTest {

    // ─── No-arg constructor ───────────────────────────────────────────────────

    @Test
    void noArgConstructor_createsInstance() throws Exception {
        final WebSocketConnection wsc = new WebSocketConnection();

        assertThat(wsc).isNotNull();
        assertThat(wsc.getId()).isNull();
        assertThat(wsc.getConnectionId()).isNull();
        assertThat(wsc.getUserEmail()).isNull();
        assertThat(wsc.getUserId()).isNull();
        assertThat(wsc.getSubscriptionType()).isNull();
        assertThat(wsc.getConnectionType()).isEqualTo("local");  // @Builder.Default initialises in no-arg ctor
        assertThat(wsc.getConnectedAt()).isNotNull();            // @Builder.Default initialises in no-arg ctor
        assertThat(wsc.getLastActivityAt()).isNotNull();         // @Builder.Default initialises in no-arg ctor
        assertThat(wsc.getExpiresAt()).isNull();
        assertThat(wsc.getIsActive()).isTrue();                  // @Builder.Default initialises in no-arg ctor
    }

    // ─── Builder defaults ─────────────────────────────────────────────────────

    @Test
    void builder_defaults() throws Exception {
        final WebSocketConnection wsc = WebSocketConnection.builder()
                .connectionId("conn-123")
                .subscriptionType("authenticated")
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();

        assertThat(wsc.getConnectionType()).isEqualTo("local");
        assertThat(wsc.getConnectedAt()).isNotNull();
        assertThat(wsc.getLastActivityAt()).isNotNull();
        assertThat(wsc.getIsActive()).isTrue();
    }

    // ─── Builder all fields ───────────────────────────────────────────────────

    @Test
    void builder_allFields() throws Exception {
        final LocalDateTime now = LocalDateTime.now();

        final WebSocketConnection wsc = WebSocketConnection.builder()
                .id(1L)
                .connectionId("conn-abc")
                .userEmail("user@example.com")
                .userId(5L)
                .subscriptionType("authenticated")
                .connectionType("aws")
                .apiGatewayEndpoint("https://api.example.com/ws")
                .metadata("{\"key\":\"value\"}")
                .connectedAt(now)
                .lastActivityAt(now)
                .expiresAt(now.plusHours(24))
                .isActive(true)
                .build();

        assertThat(wsc.getId()).isEqualTo(1L);
        assertThat(wsc.getConnectionId()).isEqualTo("conn-abc");
        assertThat(wsc.getUserEmail()).isEqualTo("user@example.com");
        assertThat(wsc.getUserId()).isEqualTo(5L);
        assertThat(wsc.getSubscriptionType()).isEqualTo("authenticated");
        assertThat(wsc.getConnectionType()).isEqualTo("aws");
        assertThat(wsc.getApiGatewayEndpoint()).isEqualTo("https://api.example.com/ws");
        assertThat(wsc.getMetadata()).isEqualTo("{\"key\":\"value\"}");
        assertThat(wsc.getConnectedAt()).isEqualTo(now);
        assertThat(wsc.getLastActivityAt()).isEqualTo(now);
        assertThat(wsc.getExpiresAt()).isEqualTo(now.plusHours(24));
        assertThat(wsc.getIsActive()).isTrue();
    }

    // ─── updateLastActivity() ─────────────────────────────────────────────────

    @Test
    void updateLastActivity_refreshesTimestamp() throws Exception {
        final WebSocketConnection wsc = new WebSocketConnection();
        wsc.setLastActivityAt(LocalDateTime.now().minusMinutes(10));
        final LocalDateTime before = wsc.getLastActivityAt();

        wsc.updateLastActivity();

        assertThat(wsc.getLastActivityAt()).isAfter(before);
    }

    // ─── isExpired() ─────────────────────────────────────────────────────────

    @Test
    void isExpired_pastExpiresAt_returnsTrue() throws Exception {
        final WebSocketConnection wsc = new WebSocketConnection();
        wsc.setExpiresAt(LocalDateTime.now().minusMinutes(1));

        assertThat(wsc.isExpired()).isTrue();
    }

    @Test
    void isExpired_futureExpiresAt_returnsFalse() throws Exception {
        final WebSocketConnection wsc = new WebSocketConnection();
        wsc.setExpiresAt(LocalDateTime.now().plusMinutes(10));

        assertThat(wsc.isExpired()).isFalse();
    }

    // ─── deactivate() ────────────────────────────────────────────────────────

    @Test
    void deactivate_setsIsActiveFalse() throws Exception {
        final WebSocketConnection wsc = new WebSocketConnection();
        wsc.setIsActive(true);

        wsc.deactivate();

        assertThat(wsc.getIsActive()).isFalse();
    }

    // ─── equals() and hashCode() ──────────────────────────────────────────────

    @Test
    void equals_sameFields_returnsTrue() throws Exception {
        final LocalDateTime now = LocalDateTime.now();
        final WebSocketConnection w1 = WebSocketConnection.builder()
                .id(1L).connectionId("c1").subscriptionType("auth")
                .connectionType("local").connectedAt(now).lastActivityAt(now)
                .expiresAt(now.plusHours(1)).isActive(true).build();
        final WebSocketConnection w2 = WebSocketConnection.builder()
                .id(1L).connectionId("c1").subscriptionType("auth")
                .connectionType("local").connectedAt(now).lastActivityAt(now)
                .expiresAt(now.plusHours(1)).isActive(true).build();

        assertThat(w1).isEqualTo(w2);
        assertThat(w1.hashCode()).isEqualTo(w2.hashCode());
    }
}
