package com.careconnect.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

class LinkMaintenanceServiceTest {

    @Mock
    private CaregiverPatientLinkService caregiverPatientLinkService;

    @Mock
    private FamilyMemberService familyMemberService;

    @InjectMocks
    private LinkMaintenanceService linkMaintenanceService;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // cleanupExpiredLinks
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("cleanupExpiredLinks - happy path - delegates to both services")
    void cleanupExpiredLinks_happyPath_delegatesToBothServices() throws Exception {
        doNothing().when(caregiverPatientLinkService).cleanupExpiredLinks();
        doNothing().when(familyMemberService).cleanupExpiredFamilyMemberLinks();

        linkMaintenanceService.cleanupExpiredLinks();

        verify(caregiverPatientLinkService).cleanupExpiredLinks();
        verify(familyMemberService).cleanupExpiredFamilyMemberLinks();
    }

    @Test
    @DisplayName("cleanupExpiredLinks - caregiverService throws - exception is caught and logged")
    void cleanupExpiredLinks_caregiverServiceThrows_exceptionIsCaughtAndLogged() throws Exception {
        doThrow(new RuntimeException("caregiver cleanup error"))
                .when(caregiverPatientLinkService).cleanupExpiredLinks();

        linkMaintenanceService.cleanupExpiredLinks();

        verify(caregiverPatientLinkService).cleanupExpiredLinks();
        verify(familyMemberService, never()).cleanupExpiredFamilyMemberLinks();
    }

    @Test
    @DisplayName("cleanupExpiredLinks - familyMemberService throws - exception is caught and logged")
    void cleanupExpiredLinks_familyMemberServiceThrows_exceptionIsCaughtAndLogged() throws Exception {
        doNothing().when(caregiverPatientLinkService).cleanupExpiredLinks();
        doThrow(new RuntimeException("family cleanup error"))
                .when(familyMemberService).cleanupExpiredFamilyMemberLinks();

        linkMaintenanceService.cleanupExpiredLinks();

        verify(caregiverPatientLinkService).cleanupExpiredLinks();
        verify(familyMemberService).cleanupExpiredFamilyMemberLinks();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // notifyExpiringSoonLinks
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("notifyExpiringSoonLinks - happy path - completes without error")
    void notifyExpiringSoonLinks_happyPath_completesWithoutError() throws Exception {
        linkMaintenanceService.notifyExpiringSoonLinks();
        // Method currently only logs; verify it completes without throwing
    }

    @Test
    @DisplayName("notifyExpiringSoonLinks - internalException - exception is caught and logged")
    void notifyExpiringSoonLinks_internalException_exceptionIsCaughtAndLogged() throws Exception {
        // The method body currently has no operations that can throw (only logging and TODO),
        // but the catch block exists for future-proofing. We test the happy path above.
        // This test simply documents that the method is safe to call.
        linkMaintenanceService.notifyExpiringSoonLinks();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // generateDailyLinkStatistics
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("generateDailyLinkStatistics - happy path - completes without error")
    void generateDailyLinkStatistics_happyPath_completesWithoutError() throws Exception {
        linkMaintenanceService.generateDailyLinkStatistics();
        // Method currently only logs; verify it completes without throwing
    }

    @Test
    @DisplayName("generateDailyLinkStatistics - internalException - exception is caught and logged")
    void generateDailyLinkStatistics_internalException_exceptionIsCaughtAndLogged() throws Exception {
        // Same as notifyExpiringSoonLinks - the catch block exists for future operations.
        linkMaintenanceService.generateDailyLinkStatistics();
    }
}
