package com.careconnect.dto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class SubscriptionCancelRequestDTOTest {

    // ─── Default constructor ──────────────────────────────────────────────────

    @Test
    void defaultConstructor_createsInstance() throws Exception {
        final SubscriptionCancelRequestDTO dto = new SubscriptionCancelRequestDTO();

        assertThat(dto).isNotNull();
        assertThat(dto.getSubscriptionId()).isNull();
    }

    // ─── Setter and Getter ────────────────────────────────────────────────────

    @Test
    void setSubscriptionId_getSubscriptionId_roundTrips() throws Exception {
        final SubscriptionCancelRequestDTO dto = new SubscriptionCancelRequestDTO();
        dto.setSubscriptionId(42L);
        assertThat(dto.getSubscriptionId()).isEqualTo(42L);
    }

    // ─── equals() and hashCode() ──────────────────────────────────────────────

    @Test
    void equals_sameInstance_returnsTrue() throws Exception {
        final SubscriptionCancelRequestDTO dto = new SubscriptionCancelRequestDTO();
        dto.setSubscriptionId(1L);
        assertThat(dto).isEqualTo(dto);
    }

    @Test
    void equals_sameFields_returnsTrue() throws Exception {
        final SubscriptionCancelRequestDTO dto1 = new SubscriptionCancelRequestDTO();
        dto1.setSubscriptionId(10L);

        final SubscriptionCancelRequestDTO dto2 = new SubscriptionCancelRequestDTO();
        dto2.setSubscriptionId(10L);

        assertThat(dto1).isEqualTo(dto2);
        assertThat(dto1.hashCode()).isEqualTo(dto2.hashCode());
    }

    @Test
    void equals_differentFields_returnsFalse() throws Exception {
        final SubscriptionCancelRequestDTO dto1 = new SubscriptionCancelRequestDTO();
        dto1.setSubscriptionId(1L);

        final SubscriptionCancelRequestDTO dto2 = new SubscriptionCancelRequestDTO();
        dto2.setSubscriptionId(2L);

        assertThat(dto1).isNotEqualTo(dto2);
    }

    @Test
    void equals_null_returnsFalse() throws Exception {
        final SubscriptionCancelRequestDTO dto = new SubscriptionCancelRequestDTO();
        assertThat(dto).isNotEqualTo(null);
    }

    @Test
    void equals_differentType_returnsFalse() throws Exception {
        final SubscriptionCancelRequestDTO dto = new SubscriptionCancelRequestDTO();
        assertThat(dto).isNotEqualTo("a string");
    }

    // ─── toString() ───────────────────────────────────────────────────────────

    @Test
    void toString_containsFieldValues() throws Exception {
        final SubscriptionCancelRequestDTO dto = new SubscriptionCancelRequestDTO();
        dto.setSubscriptionId(99L);

        assertThat(dto.toString()).contains("99");
    }
}
