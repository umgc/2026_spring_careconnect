package com.careconnect.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordResetTokenTest {

    // ─── Default constructor ──────────────────────────────────────────────────

    @Test
    void defaultConstructor_createsInstance() throws Exception {
        final PasswordResetToken token = new PasswordResetToken();

        assertThat(token).isNotNull();
        assertThat(token.getId()).isNull();
        assertThat(token.getUser()).isNull();
        assertThat(token.getTokenHash()).isNull();
        assertThat(token.getExpiresAt()).isNull();
        assertThat(token.isUsed()).isFalse();
        assertThat(token.getCreatedAt()).isNotNull(); // defaults to Instant.now()
    }

    // ─── Setters / Getters ────────────────────────────────────────────────────

    @Test
    void settersAndGetters_updateFields() throws Exception {
        final PasswordResetToken token = new PasswordResetToken();
        final User user = new User();
        final Instant now = Instant.now();

        token.setUser(user);
        token.setTokenHash("sha256hashvalue");
        token.setExpiresAt(now.plusSeconds(3600));
        token.setUsed(true);
        token.setCreatedAt(now);

        assertThat(token.getUser()).isSameAs(user);
        assertThat(token.getTokenHash()).isEqualTo("sha256hashvalue");
        assertThat(token.getExpiresAt()).isNotNull();
        assertThat(token.isUsed()).isTrue();
        assertThat(token.getCreatedAt()).isEqualTo(now);
    }
}
