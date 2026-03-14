package com.careconnect.model;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class DeviceTokenTest {

    // ─── No-arg constructor ───────────────────────────────────────────────────

    @Test
    void noArgConstructor_createsInstance() throws Exception {
        final DeviceToken dt = new DeviceToken();

        assertThat(dt).isNotNull();
        assertThat(dt.getId()).isNull();
        assertThat(dt.getUser()).isNull();
        assertThat(dt.getFcmToken()).isNull();
        assertThat(dt.getDeviceType()).isNull();
        assertThat(dt.getDeviceId()).isNull();
    }

    // ─── Builder defaults ─────────────────────────────────────────────────────

    @Test
    void builder_isActive_defaultsToTrue() throws Exception {
        final DeviceToken dt = DeviceToken.builder()
                .user(new User())
                .fcmToken("fcm-token-001")
                .deviceType(DeviceToken.DeviceType.ANDROID)
                .deviceId("device-001")
                .build();

        assertThat(dt.getIsActive()).isTrue();
    }

    @Test
    void builder_createdAt_defaultsToNow() throws Exception {
        final DeviceToken dt = DeviceToken.builder()
                .user(new User())
                .fcmToken("tok")
                .deviceType(DeviceToken.DeviceType.IOS)
                .deviceId("d1")
                .build();

        assertThat(dt.getCreatedAt()).isNotNull();
    }

    // ─── Builder all fields ───────────────────────────────────────────────────

    @Test
    void builder_allFields() throws Exception {
        final User user = new User();
        final Instant now = Instant.now();

        final DeviceToken dt = DeviceToken.builder()
                .id(1L)
                .user(user)
                .fcmToken("fcm-abc")
                .deviceType(DeviceToken.DeviceType.WEB)
                .deviceId("web-001")
                .isActive(false)
                .createdAt(now)
                .updatedAt(now)
                .lastUsedAt(now)
                .build();

        assertThat(dt.getId()).isEqualTo(1L);
        assertThat(dt.getUser()).isSameAs(user);
        assertThat(dt.getFcmToken()).isEqualTo("fcm-abc");
        assertThat(dt.getDeviceType()).isEqualTo(DeviceToken.DeviceType.WEB);
        assertThat(dt.getDeviceId()).isEqualTo("web-001");
        assertThat(dt.getIsActive()).isFalse();
        assertThat(dt.getCreatedAt()).isEqualTo(now);
        assertThat(dt.getUpdatedAt()).isEqualTo(now);
        assertThat(dt.getLastUsedAt()).isEqualTo(now);
    }

    // ─── @PreUpdate: preUpdate() ──────────────────────────────────────────────

    @Test
    void preUpdate_setsUpdatedAt() throws Exception {
        final DeviceToken dt = new DeviceToken();

        final Method m = DeviceToken.class.getDeclaredMethod("preUpdate");
        m.setAccessible(true);
        m.invoke(dt);

        assertThat(dt.getUpdatedAt()).isNotNull();
    }

    // ─── DeviceType enum ─────────────────────────────────────────────────────

    @Test
    void deviceTypeEnum_containsAllValues() throws Exception {
        assertThat(DeviceToken.DeviceType.values()).containsExactly(
                DeviceToken.DeviceType.ANDROID,
                DeviceToken.DeviceType.IOS,
                DeviceToken.DeviceType.WEB
        );
    }

    // ─── equals() and hashCode() ──────────────────────────────────────────────

    @Test
    void equals_sameFields_returnsTrue() throws Exception {
        final Instant ts = Instant.now();
        final DeviceToken d1 = DeviceToken.builder().id(1L).fcmToken("tok").createdAt(ts).build();
        final DeviceToken d2 = DeviceToken.builder().id(1L).fcmToken("tok").createdAt(ts).build();

        assertThat(d1).isEqualTo(d2);
        assertThat(d1.hashCode()).isEqualTo(d2.hashCode());
    }
}
