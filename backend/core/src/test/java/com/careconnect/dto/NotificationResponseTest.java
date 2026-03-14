package com.careconnect.dto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class NotificationResponseTest {

    // ─── Builder: all fields ───────────────────────────────────────────────────

    @Test
    void builder_allFields_setsCorrectly() throws Exception {
        final NotificationResponse response = NotificationResponse.builder()
                .success(true)
                .message("Test message")
                .messageId("msg-123")
                .error(null)
                .timestamp(1000L)
                .build();

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).isEqualTo("Test message");
        assertThat(response.getMessageId()).isEqualTo("msg-123");
        assertThat(response.getError()).isNull();
        assertThat(response.getTimestamp()).isEqualTo(1000L);
    }

    // ─── Builder: static method ────────────────────────────────────────────────

    @Test
    void builder_staticMethod_returnsBuilderInstance() throws Exception {
        final NotificationResponse.NotificationResponseBuilder builder = NotificationResponse.builder();
        assertThat(builder).isNotNull();
    }

    // ─── Static factory: success() ────────────────────────────────────────────

    @Test
    void success_setsCorrectFields() throws Exception {
        final long before = System.currentTimeMillis();
        final NotificationResponse response = NotificationResponse.success("firebase-msg-id");
        final long after = System.currentTimeMillis();

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).isEqualTo("Notification sent successfully");
        assertThat(response.getMessageId()).isEqualTo("firebase-msg-id");
        assertThat(response.getError()).isNull();
        assertThat(response.getTimestamp()).isBetween(before, after);
    }

    // ─── Static factory: failure() ────────────────────────────────────────────

    @Test
    void failure_setsCorrectFields() throws Exception {
        final long before = System.currentTimeMillis();
        final NotificationResponse response = NotificationResponse.failure("Connection timeout");
        final long after = System.currentTimeMillis();

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getMessage()).isEqualTo("Failed to send notification");
        assertThat(response.getError()).isEqualTo("Connection timeout");
        assertThat(response.getMessageId()).isNull();
        assertThat(response.getTimestamp()).isBetween(before, after);
    }

    // ─── Setters (@Data) ──────────────────────────────────────────────────────

    @Test
    void setters_updateFields() throws Exception {
        final NotificationResponse response = NotificationResponse.builder().build();

        response.setSuccess(true);
        response.setMessage("Updated message");
        response.setMessageId("new-msg-id");
        response.setError("some error");
        response.setTimestamp(9999L);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).isEqualTo("Updated message");
        assertThat(response.getMessageId()).isEqualTo("new-msg-id");
        assertThat(response.getError()).isEqualTo("some error");
        assertThat(response.getTimestamp()).isEqualTo(9999L);
    }

    // ─── equals() and hashCode() ──────────────────────────────────────────────

    @Test
    void equals_sameInstance_returnsTrue() throws Exception {
        final NotificationResponse response = NotificationResponse.builder()
                .success(true)
                .message("msg")
                .timestamp(123L)
                .build();

        assertThat(response).isEqualTo(response);
    }

    @Test
    void equals_sameFields_returnsTrue() throws Exception {
        final NotificationResponse r1 = NotificationResponse.builder()
                .success(true)
                .message("msg")
                .messageId("id-1")
                .error(null)
                .timestamp(123L)
                .build();

        final NotificationResponse r2 = NotificationResponse.builder()
                .success(true)
                .message("msg")
                .messageId("id-1")
                .error(null)
                .timestamp(123L)
                .build();

        assertThat(r1).isEqualTo(r2);
        assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
    }

    @Test
    void equals_differentFields_returnsFalse() throws Exception {
        final NotificationResponse r1 = NotificationResponse.builder()
                .success(true)
                .message("msg")
                .messageId("id-1")
                .timestamp(123L)
                .build();

        final NotificationResponse r2 = NotificationResponse.builder()
                .success(false)
                .message("other msg")
                .messageId("id-2")
                .error("some error")
                .timestamp(456L)
                .build();

        assertThat(r1).isNotEqualTo(r2);
    }

    @Test
    void equals_null_returnsFalse() throws Exception {
        final NotificationResponse response = NotificationResponse.builder().build();
        assertThat(response).isNotEqualTo(null);
    }

    @Test
    void equals_differentType_returnsFalse() throws Exception {
        final NotificationResponse response = NotificationResponse.builder().build();
        assertThat(response).isNotEqualTo("a string");
    }

    // ─── toString() ───────────────────────────────────────────────────────────

    @Test
    void toString_containsFieldValues() throws Exception {
        final NotificationResponse response = NotificationResponse.builder()
                .success(true)
                .message("Notification sent successfully")
                .messageId("abc-123")
                .error(null)
                .timestamp(100L)
                .build();

        final String str = response.toString();
        assertThat(str).contains("true");
        assertThat(str).contains("Notification sent successfully");
        assertThat(str).contains("abc-123");
        assertThat(str).contains("100");
    }
}
