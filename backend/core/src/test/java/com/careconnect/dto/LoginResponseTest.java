package com.careconnect.dto;

import com.careconnect.security.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class LoginResponseTest {

    // ─── Builder: all fields ───────────────────────────────────────────────────

    @Test
    void builder_allFields_setsCorrectly() throws Exception {
        final LoginResponse response = LoginResponse.builder()
                .id(1L)
                .email("user@example.com")
                .role(Role.PATIENT)
                .token("jwt-token-abc")
                .patientId(10L)
                .caregiverId(null)
                .name("Alice Smith")
                .status("ACTIVE")
                .emailVerified(true)
                .build();

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.email()).isEqualTo("user@example.com");
        assertThat(response.role()).isEqualTo(Role.PATIENT);
        assertThat(response.token()).isEqualTo("jwt-token-abc");
        assertThat(response.patientId()).isEqualTo(10L);
        assertThat(response.caregiverId()).isNull();
        assertThat(response.name()).isEqualTo("Alice Smith");
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.emailVerified()).isTrue();
    }

    @Test
    void builder_caregiverRole_setsCaregiversId() throws Exception {
        final LoginResponse response = LoginResponse.builder()
                .id(2L)
                .email("caregiver@example.com")
                .role(Role.CAREGIVER)
                .token("caregiver-token")
                .patientId(null)
                .caregiverId(20L)
                .name("Bob Jones")
                .status("ACTIVE")
                .emailVerified(false)
                .build();

        assertThat(response.id()).isEqualTo(2L);
        assertThat(response.role()).isEqualTo(Role.CAREGIVER);
        assertThat(response.patientId()).isNull();
        assertThat(response.caregiverId()).isEqualTo(20L);
        assertThat(response.emailVerified()).isFalse();
    }

    // ─── Builder: builder() static method ────────────────────────────────────

    @Test
    void builder_staticMethod_returnsBuilderInstance() throws Exception {
        final LoginResponse.LoginResponseBuilder builder = LoginResponse.builder();
        assertThat(builder).isNotNull();
    }

    // ─── Record accessors ─────────────────────────────────────────────────────

    @Test
    void record_accessors_allFieldsAccessible() throws Exception {
        final LoginResponse response = new LoginResponse(
                99L, "admin@example.com", Role.ADMIN, "admin-token",
                null, null, "Super Admin", "ACTIVE", true
        );

        assertThat(response.id()).isEqualTo(99L);
        assertThat(response.email()).isEqualTo("admin@example.com");
        assertThat(response.role()).isEqualTo(Role.ADMIN);
        assertThat(response.token()).isEqualTo("admin-token");
        assertThat(response.patientId()).isNull();
        assertThat(response.caregiverId()).isNull();
        assertThat(response.name()).isEqualTo("Super Admin");
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.emailVerified()).isTrue();
    }

    // ─── Builder chaining (each setter returns the builder) ───────────────────

    @Test
    void builder_chainedSetters_buildsCorrectly() throws Exception {
        final LoginResponse response = LoginResponse.builder()
                .id(5L)
                .name("Family Member")
                .email("family@example.com")
                .role(Role.FAMILY_MEMBER)
                .token("fam-token")
                .patientId(7L)
                .caregiverId(null)
                .status("PENDING")
                .emailVerified(false)
                .build();

        assertThat(response.id()).isEqualTo(5L);
        assertThat(response.name()).isEqualTo("Family Member");
        assertThat(response.role()).isEqualTo(Role.FAMILY_MEMBER);
        assertThat(response.status()).isEqualTo("PENDING");
    }
}
