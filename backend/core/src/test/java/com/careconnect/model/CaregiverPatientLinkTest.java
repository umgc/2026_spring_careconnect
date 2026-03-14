package com.careconnect.model;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class CaregiverPatientLinkTest {

    // ─── Default constructor ──────────────────────────────────────────────────

    @Test
    void defaultConstructor_setsDefaults() throws Exception {
        final CaregiverPatientLink link = new CaregiverPatientLink();

        assertThat(link).isNotNull();
        assertThat(link.getId()).isNull();
        assertThat(link.getStatus()).isEqualTo(CaregiverPatientLink.LinkStatus.ACTIVE);
        assertThat(link.getLinkType()).isEqualTo(CaregiverPatientLink.LinkType.PERMANENT);
    }

    // ─── All-arg constructor ──────────────────────────────────────────────────

    @Test
    void allArgConstructor_setsAllFields() throws Exception {
        final User caregiver = new User();
        final User patient = new User();
        final User createdBy = new User();
        final LocalDateTime now = LocalDateTime.now();

        final CaregiverPatientLink link = new CaregiverPatientLink(
                1L, caregiver, patient, createdBy, now, now,
                CaregiverPatientLink.LinkStatus.ACTIVE,
                CaregiverPatientLink.LinkType.TEMPORARY,
                null, "Test notes"
        );

        assertThat(link.getId()).isEqualTo(1L);
        assertThat(link.getCaregiverUser()).isSameAs(caregiver);
        assertThat(link.getPatientUser()).isSameAs(patient);
        assertThat(link.getCreatedBy()).isSameAs(createdBy);
        assertThat(link.getStatus()).isEqualTo(CaregiverPatientLink.LinkStatus.ACTIVE);
        assertThat(link.getLinkType()).isEqualTo(CaregiverPatientLink.LinkType.TEMPORARY);
        assertThat(link.getNotes()).isEqualTo("Test notes");
    }

    // ─── 4-arg constructor ────────────────────────────────────────────────────

    @Test
    void fourArgConstructor_setsFields() throws Exception {
        final User caregiver = new User();
        final User patient = new User();
        final User createdBy = new User();

        final CaregiverPatientLink link = new CaregiverPatientLink(
                caregiver, patient, createdBy, CaregiverPatientLink.LinkType.EMERGENCY);

        assertThat(link.getCaregiverUser()).isSameAs(caregiver);
        assertThat(link.getPatientUser()).isSameAs(patient);
        assertThat(link.getCreatedBy()).isSameAs(createdBy);
        assertThat(link.getLinkType()).isEqualTo(CaregiverPatientLink.LinkType.EMERGENCY);
    }

    // ─── isActive() ──────────────────────────────────────────────────────────

    @Test
    void isActive_statusActive_noExpiry_returnsTrue() throws Exception {
        final CaregiverPatientLink link = new CaregiverPatientLink();
        link.setStatus(CaregiverPatientLink.LinkStatus.ACTIVE);
        link.setExpiresAt(null);

        assertThat(link.isActive()).isTrue();
    }

    @Test
    void isActive_statusPending_returnsFalse() throws Exception {
        final CaregiverPatientLink link = new CaregiverPatientLink();
        link.setStatus(CaregiverPatientLink.LinkStatus.PENDING);

        assertThat(link.isActive()).isFalse();
    }

    @Test
    void isActive_statusActive_expiredDate_returnsFalse() throws Exception {
        final CaregiverPatientLink link = new CaregiverPatientLink();
        link.setStatus(CaregiverPatientLink.LinkStatus.ACTIVE);
        link.setExpiresAt(LocalDateTime.now().minusDays(1));

        assertThat(link.isActive()).isFalse();
    }

    @Test
    void isActive_statusActive_futureExpiry_returnsTrue() throws Exception {
        final CaregiverPatientLink link = new CaregiverPatientLink();
        link.setStatus(CaregiverPatientLink.LinkStatus.ACTIVE);
        link.setExpiresAt(LocalDateTime.now().plusDays(10));

        assertThat(link.isActive()).isTrue();
    }

    // ─── isExpired() ─────────────────────────────────────────────────────────

    @Test
    void isExpired_nullExpiresAt_returnsFalse() throws Exception {
        final CaregiverPatientLink link = new CaregiverPatientLink();
        link.setExpiresAt(null);
        assertThat(link.isExpired()).isFalse();
    }

    @Test
    void isExpired_pastExpiresAt_returnsTrue() throws Exception {
        final CaregiverPatientLink link = new CaregiverPatientLink();
        link.setExpiresAt(LocalDateTime.now().minusDays(1));
        assertThat(link.isExpired()).isTrue();
    }

    @Test
    void isExpired_futureExpiresAt_returnsFalse() throws Exception {
        final CaregiverPatientLink link = new CaregiverPatientLink();
        link.setExpiresAt(LocalDateTime.now().plusDays(5));
        assertThat(link.isExpired()).isFalse();
    }

    // ─── setStatus() updates updatedAt ───────────────────────────────────────

    @Test
    void setStatus_updatesUpdatedAt() throws Exception {
        final CaregiverPatientLink link = new CaregiverPatientLink();
        link.setStatus(CaregiverPatientLink.LinkStatus.REVOKED);

        assertThat(link.getStatus()).isEqualTo(CaregiverPatientLink.LinkStatus.REVOKED);
        assertThat(link.getUpdatedAt()).isNotNull();
    }

    // ─── @PrePersist: onCreate() ──────────────────────────────────────────────

    @Test
    void onCreate_setsTimestamps() throws Exception {
        final CaregiverPatientLink link = new CaregiverPatientLink();

        final Method m = CaregiverPatientLink.class.getDeclaredMethod("onCreate");
        m.setAccessible(true);
        m.invoke(link);

        assertThat(link.getCreatedAt()).isNotNull();
        assertThat(link.getUpdatedAt()).isNotNull();
    }

    // ─── @PreUpdate: onUpdate() ───────────────────────────────────────────────

    @Test
    void onUpdate_setsUpdatedAt() throws Exception {
        final CaregiverPatientLink link = new CaregiverPatientLink();

        final Method m = CaregiverPatientLink.class.getDeclaredMethod("onUpdate");
        m.setAccessible(true);
        m.invoke(link);

        assertThat(link.getUpdatedAt()).isNotNull();
    }

    // ─── LinkStatus enum ─────────────────────────────────────────────────────

    @Test
    void linkStatusEnum_containsAllValues() throws Exception {
        assertThat(CaregiverPatientLink.LinkStatus.values()).containsExactly(
                CaregiverPatientLink.LinkStatus.PENDING,
                CaregiverPatientLink.LinkStatus.ACTIVE,
                CaregiverPatientLink.LinkStatus.SUSPENDED,
                CaregiverPatientLink.LinkStatus.REVOKED,
                CaregiverPatientLink.LinkStatus.EXPIRED,
                CaregiverPatientLink.LinkStatus.REJECTED
        );
    }

    // ─── LinkType enum ────────────────────────────────────────────────────────

    @Test
    void linkTypeEnum_containsAllValues() throws Exception {
        assertThat(CaregiverPatientLink.LinkType.values()).containsExactly(
                CaregiverPatientLink.LinkType.PERMANENT,
                CaregiverPatientLink.LinkType.TEMPORARY,
                CaregiverPatientLink.LinkType.EMERGENCY
        );
    }

    // ─── Remaining setters ────────────────────────────────────────────────────

    @Test
    void remainingSetters_updateFields() throws Exception {
        final CaregiverPatientLink link = new CaregiverPatientLink();
        final User caregiver = new User();
        final User patient = new User();
        final User createdBy = new User();
        final LocalDateTime now = LocalDateTime.now();

        link.setId(99L);
        link.setCaregiverUser(caregiver);
        link.setPatientUser(patient);
        link.setCreatedBy(createdBy);
        link.setCreatedAt(now);
        link.setUpdatedAt(now);
        link.setLinkType(CaregiverPatientLink.LinkType.TEMPORARY);
        link.setNotes("notes");

        assertThat(link.getId()).isEqualTo(99L);
        assertThat(link.getCaregiverUser()).isSameAs(caregiver);
        assertThat(link.getPatientUser()).isSameAs(patient);
        assertThat(link.getCreatedBy()).isSameAs(createdBy);
        assertThat(link.getCreatedAt()).isEqualTo(now);
        assertThat(link.getLinkType()).isEqualTo(CaregiverPatientLink.LinkType.TEMPORARY);
        assertThat(link.getNotes()).isEqualTo("notes");
    }
}
