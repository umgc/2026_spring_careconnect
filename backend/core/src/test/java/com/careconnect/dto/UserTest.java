package com.careconnect.dto;

import com.careconnect.model.User;
import com.careconnect.security.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Timestamp;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class UserTest {

    // ─── No-arg constructor ───────────────────────────────────────────────────

    @Test
    void noArgConstructor_createsInstance() throws Exception {
        final User user = new User();

        assertThat(user).isNotNull();
        assertThat(user.getId()).isNull();
        assertThat(user.getEmail()).isNull();
    }

    // ─── Builder ──────────────────────────────────────────────────────────────

    @Test
    void builder_allFields_setsCorrectly() throws Exception {
        final Timestamp now = new Timestamp(System.currentTimeMillis());
        final LocalDate today = LocalDate.now();

        final User user = User.builder()
                .id(1L)
                .name("Alice Smith")
                .email("alice@example.com")
                .password("secret")
                .passwordHash("hashed_secret")
                .lastLoginDate(today)
                .loginStreak(5)
                .leaderboardOptIn(true)
                .role(Role.PATIENT)
                .isVerified(true)
                .verificationToken("tok-abc")
                .stripeCustomerId("cus_stripe")
                .createdAt(now)
                .lastLogin(now)
                .profileImageUrl("https://example.com/pic.jpg")
                .status("ACTIVE")
                .build();

        assertThat(user.getId()).isEqualTo(1L);
        assertThat(user.getName()).isEqualTo("Alice Smith");
        assertThat(user.getEmail()).isEqualTo("alice@example.com");
        assertThat(user.getPassword()).isEqualTo("secret");
        assertThat(user.getPasswordHash()).isEqualTo("hashed_secret");
        assertThat(user.getLastLoginDate()).isEqualTo(today);
        assertThat(user.getLoginStreak()).isEqualTo(5);
        assertThat(user.getLeaderboardOptIn()).isTrue();
        assertThat(user.getRole()).isEqualTo(Role.PATIENT);
        assertThat(user.getIsVerified()).isTrue();
        assertThat(user.getVerificationToken()).isEqualTo("tok-abc");
        assertThat(user.getStripeCustomerId()).isEqualTo("cus_stripe");
        assertThat(user.getProfileImageUrl()).isEqualTo("https://example.com/pic.jpg");
        assertThat(user.getStatus()).isEqualTo("ACTIVE");
    }

    // ─── Builder defaults ─────────────────────────────────────────────────────

    @Test
    void builder_defaults_areApplied() throws Exception {
        final User user = User.builder()
                .email("test@example.com")
                .password("pass")
                .role(Role.CAREGIVER)
                .build();

        assertThat(user.getLoginStreak()).isEqualTo(0);
        assertThat(user.getLeaderboardOptIn()).isTrue();
        assertThat(user.getIsVerified()).isFalse();
        assertThat(user.getStatus()).isEqualTo("ACTIVE");
    }

    // ─── isActive() ───────────────────────────────────────────────────────────

    @Test
    void isActive_statusACTIVE_returnsTrue() throws Exception {
        final User user = User.builder()
                .email("a@b.com")
                .password("pass")
                .role(Role.ADMIN)
                .status("ACTIVE")
                .build();

        assertThat(user.isActive()).isTrue();
    }

    @Test
    void isActive_statusActiveLowercase_returnsTrue() throws Exception {
        final User user = new User();
        user.setStatus("active");

        assertThat(user.isActive()).isTrue();
    }

    @Test
    void isActive_statusInactive_returnsFalse() throws Exception {
        final User user = new User();
        user.setStatus("INACTIVE");

        assertThat(user.isActive()).isFalse();
    }

    // ─── Setters ──────────────────────────────────────────────────────────────

    @Test
    void setters_updateAllFields() throws Exception {
        final LocalDate today = LocalDate.now();
        final User user = new User();

        user.setId(99L);
        user.setName("Bob");
        user.setEmail("bob@example.com");
        user.setPassword("pw");
        user.setPasswordHash("hash");
        user.setLastLoginDate(today);
        user.setLoginStreak(3);
        user.setLeaderboardOptIn(false);
        user.setRole(Role.FAMILY_MEMBER);
        user.setIsVerified(true);
        user.setVerificationToken("tok-xyz");
        user.setStripeCustomerId("cus_bob");
        user.setProfileImageUrl("https://example.com/bob.jpg");
        user.setStatus("SUSPENDED");

        assertThat(user.getId()).isEqualTo(99L);
        assertThat(user.getName()).isEqualTo("Bob");
        assertThat(user.getEmail()).isEqualTo("bob@example.com");
        assertThat(user.getPassword()).isEqualTo("pw");
        assertThat(user.getPasswordHash()).isEqualTo("hash");
        assertThat(user.getLastLoginDate()).isEqualTo(today);
        assertThat(user.getLoginStreak()).isEqualTo(3);
        assertThat(user.getLeaderboardOptIn()).isFalse();
        assertThat(user.getRole()).isEqualTo(Role.FAMILY_MEMBER);
        assertThat(user.getIsVerified()).isTrue();
        assertThat(user.getVerificationToken()).isEqualTo("tok-xyz");
        assertThat(user.getStripeCustomerId()).isEqualTo("cus_bob");
        assertThat(user.getProfileImageUrl()).isEqualTo("https://example.com/bob.jpg");
        assertThat(user.getStatus()).isEqualTo("SUSPENDED");
    }

    // ─── equals() and hashCode() ──────────────────────────────────────────────

    @Test
    void equals_sameFields_returnsTrue() throws Exception {
        final User u1 = User.builder().id(1L).email("a@b.com").password("pw").role(Role.PATIENT).build();
        final User u2 = User.builder().id(1L).email("a@b.com").password("pw").role(Role.PATIENT).build();

        assertThat(u1).isEqualTo(u2);
        assertThat(u1.hashCode()).isEqualTo(u2.hashCode());
    }

    @Test
    void equals_differentFields_returnsFalse() throws Exception {
        final User u1 = User.builder().id(1L).email("a@b.com").password("pw").role(Role.PATIENT).build();
        final User u2 = User.builder().id(2L).email("c@d.com").password("pw").role(Role.ADMIN).build();

        assertThat(u1).isNotEqualTo(u2);
    }

    @Test
    void equals_null_returnsFalse() throws Exception {
        final User user = new User();
        assertThat(user).isNotEqualTo(null);
    }

    @Test
    void equals_differentType_returnsFalse() throws Exception {
        final User user = new User();
        assertThat(user).isNotEqualTo("a string");
    }

    // ─── toString() ───────────────────────────────────────────────────────────

    @Test
    void toString_containsFieldValues() throws Exception {
        final User user = User.builder()
                .id(42L)
                .email("check@example.com")
                .password("pw")
                .role(Role.CAREGIVER)
                .build();

        final String str = user.toString();
        assertThat(str).contains("42");
        assertThat(str).contains("check@example.com");
    }
}
