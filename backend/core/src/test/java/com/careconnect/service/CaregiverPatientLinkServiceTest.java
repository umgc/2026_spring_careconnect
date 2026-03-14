package com.careconnect.service;

import com.careconnect.dto.CaregiverPatientLinkResponse;
import com.careconnect.dto.CreateLinkRequest;
import com.careconnect.dto.UpdateLinkRequest;
import com.careconnect.exception.AppException;
import com.careconnect.model.Caregiver;
import com.careconnect.model.CaregiverPatientLink;
import com.careconnect.model.Patient;
import com.careconnect.model.User;
import com.careconnect.repository.CaregiverPatientLinkRepository;
import com.careconnect.repository.CaregiverRepository;
import com.careconnect.repository.PatientRepository;
import com.careconnect.repository.UserRepository;
import com.careconnect.security.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CaregiverPatientLinkServiceTest {

    @Mock
    private CaregiverPatientLinkRepository caregiverPatientLinkRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private CaregiverRepository caregiverRepository;

    @InjectMocks
    private CaregiverPatientLinkService caregiverPatientLinkService;

    private User caregiverUser;
    private User patientUser;
    private User creatorUser;
    private Caregiver caregiver;
    private Patient patient;
    private CaregiverPatientLink link;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);

        caregiverUser = new User();
        caregiverUser.setId(1L);
        caregiverUser.setEmail("caregiver@example.com");
        caregiverUser.setRole(Role.CAREGIVER);
        caregiverUser.setPassword("password");

        patientUser = new User();
        patientUser.setId(2L);
        patientUser.setEmail("patient@example.com");
        patientUser.setRole(Role.PATIENT);
        patientUser.setPassword("password");

        creatorUser = new User();
        creatorUser.setId(3L);
        creatorUser.setEmail("admin@example.com");
        creatorUser.setRole(Role.ADMIN);
        creatorUser.setPassword("password");

        caregiver = Caregiver.builder()
                .id(1L)
                .firstName("Jane")
                .lastName("Doe")
                .user(caregiverUser)
                .build();

        patient = Patient.builder()
                .id(1L)
                .firstName("John")
                .lastName("Smith")
                .user(patientUser)
                .build();

        link = new CaregiverPatientLink();
        link.setId(100L);
        link.setCaregiverUser(caregiverUser);
        link.setPatientUser(patientUser);
        link.setCreatedBy(creatorUser);
        link.setStatus(CaregiverPatientLink.LinkStatus.ACTIVE);
        link.setLinkType(CaregiverPatientLink.LinkType.PERMANENT);
        link.setCreatedAt(LocalDateTime.now());
        link.setNotes("Test link");
    }

    // -------------------------------------------------------------------------
    // Helper: stub caregiver/patient repository to return names for response mapping
    // -------------------------------------------------------------------------
    private void stubNameLookups() throws Exception {
        when(caregiverRepository.findByUser(caregiverUser)).thenReturn(Optional.of(caregiver));
        when(patientRepository.findByUser(patientUser)).thenReturn(Optional.of(patient));
    }

    // =========================================================================
    // createLink
    // =========================================================================
    @Nested
    @DisplayName("createLink tests")
    class CreateLinkTests {

        @Test
        @DisplayName("createLink_validRequest_returnsResponse")
        void createLink_validRequest_returnsResponse() throws Exception {
            final CreateLinkRequest request = new CreateLinkRequest(2L, "PERMANENT", null, "notes");

            when(userRepository.findById(1L)).thenReturn(Optional.of(caregiverUser));
            when(userRepository.findById(2L)).thenReturn(Optional.of(patientUser));
            when(userRepository.findById(3L)).thenReturn(Optional.of(creatorUser));
            when(caregiverPatientLinkRepository.existsByCaregiverUserAndPatientUserAndStatus(
                    caregiverUser, patientUser, CaregiverPatientLink.LinkStatus.ACTIVE)).thenReturn(false);
            when(caregiverPatientLinkRepository.save(any(CaregiverPatientLink.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            stubNameLookups();
            // creatorUser has ADMIN role, so getUserName falls through to default branch
            // No additional stub needed for ADMIN role

            final CaregiverPatientLinkResponse response = caregiverPatientLinkService.createLink(1L, request, 3L);

            assertNotNull(response);
            assertEquals("PERMANENT", response.linkType());
            assertEquals("notes", response.notes());
            verify(caregiverPatientLinkRepository).save(any(CaregiverPatientLink.class));
        }

        @Test
        @DisplayName("createLink_temporaryWithExpiry_returnsResponseWithExpiry")
        void createLink_temporaryWithExpiry_returnsResponseWithExpiry() throws Exception {
            final LocalDateTime expiry = LocalDateTime.now().plusDays(7);
            final CreateLinkRequest request = new CreateLinkRequest(2L, "temporary", expiry, "temp access");

            when(userRepository.findById(1L)).thenReturn(Optional.of(caregiverUser));
            when(userRepository.findById(2L)).thenReturn(Optional.of(patientUser));
            when(userRepository.findById(3L)).thenReturn(Optional.of(creatorUser));
            when(caregiverPatientLinkRepository.existsByCaregiverUserAndPatientUserAndStatus(
                    caregiverUser, patientUser, CaregiverPatientLink.LinkStatus.ACTIVE)).thenReturn(false);
            when(caregiverPatientLinkRepository.save(any(CaregiverPatientLink.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            stubNameLookups();

            final CaregiverPatientLinkResponse response = caregiverPatientLinkService.createLink(1L, request, 3L);

            assertNotNull(response);
            assertEquals("TEMPORARY", response.linkType());
            assertEquals(expiry, response.expiresAt());
        }

        @Test
        @DisplayName("createLink_caregiverNotFound_throwsAppException")
        void createLink_caregiverNotFound_throwsAppException() throws Exception {
            final CreateLinkRequest request = new CreateLinkRequest(2L, "PERMANENT", null, null);
            when(userRepository.findById(1L)).thenReturn(Optional.empty());

            final AppException ex = assertThrows(AppException.class,
                    () -> caregiverPatientLinkService.createLink(1L, request, 3L));

            assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
            assertEquals("Caregiver not found", ex.getMessage());
        }

        @Test
        @DisplayName("createLink_patientNotFound_throwsAppException")
        void createLink_patientNotFound_throwsAppException() throws Exception {
            final CreateLinkRequest request = new CreateLinkRequest(2L, "PERMANENT", null, null);
            when(userRepository.findById(1L)).thenReturn(Optional.of(caregiverUser));
            when(userRepository.findById(2L)).thenReturn(Optional.empty());

            final AppException ex = assertThrows(AppException.class,
                    () -> caregiverPatientLinkService.createLink(1L, request, 3L));

            assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
            assertEquals("Patient not found", ex.getMessage());
        }

        @Test
        @DisplayName("createLink_creatorNotFound_throwsAppException")
        void createLink_creatorNotFound_throwsAppException() throws Exception {
            final CreateLinkRequest request = new CreateLinkRequest(2L, "PERMANENT", null, null);
            when(userRepository.findById(1L)).thenReturn(Optional.of(caregiverUser));
            when(userRepository.findById(2L)).thenReturn(Optional.of(patientUser));
            when(userRepository.findById(3L)).thenReturn(Optional.empty());

            final AppException ex = assertThrows(AppException.class,
                    () -> caregiverPatientLinkService.createLink(1L, request, 3L));

            assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
            assertEquals("Creator user not found", ex.getMessage());
        }

        @Test
        @DisplayName("createLink_activeLinkAlreadyExists_throwsConflictException")
        void createLink_activeLinkAlreadyExists_throwsConflictException() throws Exception {
            final CreateLinkRequest request = new CreateLinkRequest(2L, "PERMANENT", null, null);
            when(userRepository.findById(1L)).thenReturn(Optional.of(caregiverUser));
            when(userRepository.findById(2L)).thenReturn(Optional.of(patientUser));
            when(userRepository.findById(3L)).thenReturn(Optional.of(creatorUser));
            when(caregiverPatientLinkRepository.existsByCaregiverUserAndPatientUserAndStatus(
                    caregiverUser, patientUser, CaregiverPatientLink.LinkStatus.ACTIVE)).thenReturn(true);

            final AppException ex = assertThrows(AppException.class,
                    () -> caregiverPatientLinkService.createLink(1L, request, 3L));

            assertEquals(HttpStatus.CONFLICT, ex.getStatus());
            assertEquals("Active link already exists between caregiver and patient", ex.getMessage());
        }
    }

    // =========================================================================
    // updateLink
    // =========================================================================
    @Nested
    @DisplayName("updateLink tests")
    class UpdateLinkTests {

        @Test
        @DisplayName("updateLink_allFieldsProvided_updatesAll")
        void updateLink_allFieldsProvided_updatesAll() throws Exception {
            final LocalDateTime newExpiry = LocalDateTime.now().plusDays(30);
            final UpdateLinkRequest request = new UpdateLinkRequest("SUSPENDED", "TEMPORARY", newExpiry, "updated notes");

            when(caregiverPatientLinkRepository.findById(100L)).thenReturn(Optional.of(link));
            when(caregiverPatientLinkRepository.save(any(CaregiverPatientLink.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            stubNameLookups();

            final CaregiverPatientLinkResponse response = caregiverPatientLinkService.updateLink(100L, request, 3L);

            assertNotNull(response);
            assertEquals("SUSPENDED", response.status());
            assertEquals("TEMPORARY", response.linkType());
            assertEquals(newExpiry, response.expiresAt());
            assertEquals("updated notes", response.notes());
        }

        @Test
        @DisplayName("updateLink_allFieldsNull_noChanges")
        void updateLink_allFieldsNull_noChanges() throws Exception {
            final UpdateLinkRequest request = new UpdateLinkRequest(null, null, null, null);

            when(caregiverPatientLinkRepository.findById(100L)).thenReturn(Optional.of(link));
            when(caregiverPatientLinkRepository.save(any(CaregiverPatientLink.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            stubNameLookups();

            final CaregiverPatientLinkResponse response = caregiverPatientLinkService.updateLink(100L, request, 3L);

            assertNotNull(response);
            assertEquals("ACTIVE", response.status());
            assertEquals("PERMANENT", response.linkType());
        }

        @Test
        @DisplayName("updateLink_onlyStatusProvided_updatesOnlyStatus")
        void updateLink_onlyStatusProvided_updatesOnlyStatus() throws Exception {
            final UpdateLinkRequest request = new UpdateLinkRequest("REVOKED", null, null, null);

            when(caregiverPatientLinkRepository.findById(100L)).thenReturn(Optional.of(link));
            when(caregiverPatientLinkRepository.save(any(CaregiverPatientLink.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            stubNameLookups();

            final CaregiverPatientLinkResponse response = caregiverPatientLinkService.updateLink(100L, request, 3L);

            assertEquals("REVOKED", response.status());
            assertEquals("PERMANENT", response.linkType());
        }

        @Test
        @DisplayName("updateLink_onlyLinkTypeProvided_updatesOnlyLinkType")
        void updateLink_onlyLinkTypeProvided_updatesOnlyLinkType() throws Exception {
            final UpdateLinkRequest request = new UpdateLinkRequest(null, "EMERGENCY", null, null);

            when(caregiverPatientLinkRepository.findById(100L)).thenReturn(Optional.of(link));
            when(caregiverPatientLinkRepository.save(any(CaregiverPatientLink.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            stubNameLookups();

            final CaregiverPatientLinkResponse response = caregiverPatientLinkService.updateLink(100L, request, 3L);

            assertEquals("ACTIVE", response.status());
            assertEquals("EMERGENCY", response.linkType());
        }

        @Test
        @DisplayName("updateLink_onlyExpiresAtProvided_updatesOnlyExpiry")
        void updateLink_onlyExpiresAtProvided_updatesOnlyExpiry() throws Exception {
            final LocalDateTime newExpiry = LocalDateTime.now().plusDays(14);
            final UpdateLinkRequest request = new UpdateLinkRequest(null, null, newExpiry, null);

            when(caregiverPatientLinkRepository.findById(100L)).thenReturn(Optional.of(link));
            when(caregiverPatientLinkRepository.save(any(CaregiverPatientLink.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            stubNameLookups();

            final CaregiverPatientLinkResponse response = caregiverPatientLinkService.updateLink(100L, request, 3L);

            assertEquals(newExpiry, response.expiresAt());
        }

        @Test
        @DisplayName("updateLink_onlyNotesProvided_updatesOnlyNotes")
        void updateLink_onlyNotesProvided_updatesOnlyNotes() throws Exception {
            final UpdateLinkRequest request = new UpdateLinkRequest(null, null, null, "new notes");

            when(caregiverPatientLinkRepository.findById(100L)).thenReturn(Optional.of(link));
            when(caregiverPatientLinkRepository.save(any(CaregiverPatientLink.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            stubNameLookups();

            final CaregiverPatientLinkResponse response = caregiverPatientLinkService.updateLink(100L, request, 3L);

            assertEquals("new notes", response.notes());
        }

        @Test
        @DisplayName("updateLink_linkNotFound_throwsAppException")
        void updateLink_linkNotFound_throwsAppException() throws Exception {
            final UpdateLinkRequest request = new UpdateLinkRequest("ACTIVE", null, null, null);
            when(caregiverPatientLinkRepository.findById(999L)).thenReturn(Optional.empty());

            final AppException ex = assertThrows(AppException.class,
                    () -> caregiverPatientLinkService.updateLink(999L, request, 3L));

            assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
            assertEquals("Link not found", ex.getMessage());
        }
    }

    // =========================================================================
    // suspendLink
    // =========================================================================
    @Nested
    @DisplayName("suspendLink tests")
    class SuspendLinkTests {

        @Test
        @DisplayName("suspendLink_withValidUserId_suspendsLink")
        void suspendLink_withValidUserId_suspendsLink() throws Exception {
            when(caregiverPatientLinkRepository.findById(100L)).thenReturn(Optional.of(link));
            when(userRepository.findById(3L)).thenReturn(Optional.of(creatorUser));
            when(caregiverPatientLinkRepository.save(any(CaregiverPatientLink.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            stubNameLookups();

            final CaregiverPatientLinkResponse response = caregiverPatientLinkService.suspendLink(100L, "3");

            assertNotNull(response);
            assertEquals("SUSPENDED", response.status());
            verify(caregiverPatientLinkRepository).save(any(CaregiverPatientLink.class));
        }

        @Test
        @DisplayName("suspendLink_withValidEmail_suspendsLink")
        void suspendLink_withValidEmail_suspendsLink() throws Exception {
            when(caregiverPatientLinkRepository.findById(100L)).thenReturn(Optional.of(link));
            when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(creatorUser));
            when(caregiverPatientLinkRepository.save(any(CaregiverPatientLink.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            stubNameLookups();

            final CaregiverPatientLinkResponse response = caregiverPatientLinkService.suspendLink(100L, "admin@example.com");

            assertNotNull(response);
            assertEquals("SUSPENDED", response.status());
        }

        @Test
        @DisplayName("suspendLink_linkNotFound_throwsAppException")
        void suspendLink_linkNotFound_throwsAppException() throws Exception {
            when(caregiverPatientLinkRepository.findById(999L)).thenReturn(Optional.empty());

            final AppException ex = assertThrows(AppException.class,
                    () -> caregiverPatientLinkService.suspendLink(999L, "3"));

            assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
            assertEquals("Link not found", ex.getMessage());
        }

        @Test
        @DisplayName("suspendLink_userIdNotFound_throwsAppException")
        void suspendLink_userIdNotFound_throwsAppException() throws Exception {
            when(caregiverPatientLinkRepository.findById(100L)).thenReturn(Optional.of(link));
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            final AppException ex = assertThrows(AppException.class,
                    () -> caregiverPatientLinkService.suspendLink(100L, "999"));

            assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
            assertEquals("User not found", ex.getMessage());
        }

        @Test
        @DisplayName("suspendLink_emailNotFound_throwsAppException")
        void suspendLink_emailNotFound_throwsAppException() throws Exception {
            when(caregiverPatientLinkRepository.findById(100L)).thenReturn(Optional.of(link));
            when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

            final AppException ex = assertThrows(AppException.class,
                    () -> caregiverPatientLinkService.suspendLink(100L, "unknown@example.com"));

            assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
            assertEquals("User not found", ex.getMessage());
        }
    }

    // =========================================================================
    // reactivateLink
    // =========================================================================
    @Nested
    @DisplayName("reactivateLink tests")
    class ReactivateLinkTests {

        @Test
        @DisplayName("reactivateLink_suspendedLink_reactivatesSuccessfully")
        void reactivateLink_suspendedLink_reactivatesSuccessfully() throws Exception {
            link.setStatus(CaregiverPatientLink.LinkStatus.SUSPENDED);

            when(caregiverPatientLinkRepository.findById(100L)).thenReturn(Optional.of(link));
            when(caregiverPatientLinkRepository.save(any(CaregiverPatientLink.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            stubNameLookups();

            final CaregiverPatientLinkResponse response = caregiverPatientLinkService.reactivateLink(100L, 3L);

            assertNotNull(response);
            assertEquals("ACTIVE", response.status());
            verify(caregiverPatientLinkRepository).save(any(CaregiverPatientLink.class));
        }

        @Test
        @DisplayName("reactivateLink_linkNotFound_throwsAppException")
        void reactivateLink_linkNotFound_throwsAppException() throws Exception {
            when(caregiverPatientLinkRepository.findById(999L)).thenReturn(Optional.empty());

            final AppException ex = assertThrows(AppException.class,
                    () -> caregiverPatientLinkService.reactivateLink(999L, 3L));

            assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
            assertEquals("Link not found", ex.getMessage());
        }

        @Test
        @DisplayName("reactivateLink_activeLink_throwsBadRequest")
        void reactivateLink_activeLink_throwsBadRequest() throws Exception {
            link.setStatus(CaregiverPatientLink.LinkStatus.ACTIVE);
            when(caregiverPatientLinkRepository.findById(100L)).thenReturn(Optional.of(link));

            final AppException ex = assertThrows(AppException.class,
                    () -> caregiverPatientLinkService.reactivateLink(100L, 3L));

            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
            assertEquals("Only suspended links can be reactivated", ex.getMessage());
        }

        @Test
        @DisplayName("reactivateLink_revokedLink_throwsBadRequest")
        void reactivateLink_revokedLink_throwsBadRequest() throws Exception {
            link.setStatus(CaregiverPatientLink.LinkStatus.REVOKED);
            when(caregiverPatientLinkRepository.findById(100L)).thenReturn(Optional.of(link));

            final AppException ex = assertThrows(AppException.class,
                    () -> caregiverPatientLinkService.reactivateLink(100L, 3L));

            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        }

        @Test
        @DisplayName("reactivateLink_expiredLink_throwsBadRequest")
        void reactivateLink_expiredLink_throwsBadRequest() throws Exception {
            link.setStatus(CaregiverPatientLink.LinkStatus.EXPIRED);
            when(caregiverPatientLinkRepository.findById(100L)).thenReturn(Optional.of(link));

            final AppException ex = assertThrows(AppException.class,
                    () -> caregiverPatientLinkService.reactivateLink(100L, 3L));

            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        }
    }

    // =========================================================================
    // revokeLink
    // =========================================================================
    @Nested
    @DisplayName("revokeLink tests")
    class RevokeLinkTests {

        @Test
        @DisplayName("revokeLink_existingLink_revokesSuccessfully")
        void revokeLink_existingLink_revokesSuccessfully() throws Exception {
            when(caregiverPatientLinkRepository.findById(100L)).thenReturn(Optional.of(link));
            when(caregiverPatientLinkRepository.save(any(CaregiverPatientLink.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            caregiverPatientLinkService.revokeLink(100L, 3L);

            assertEquals(CaregiverPatientLink.LinkStatus.REVOKED, link.getStatus());
            verify(caregiverPatientLinkRepository).save(link);
        }

        @Test
        @DisplayName("revokeLink_linkNotFound_throwsAppException")
        void revokeLink_linkNotFound_throwsAppException() throws Exception {
            when(caregiverPatientLinkRepository.findById(999L)).thenReturn(Optional.empty());

            final AppException ex = assertThrows(AppException.class,
                    () -> caregiverPatientLinkService.revokeLink(999L, 3L));

            assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
            assertEquals("Link not found", ex.getMessage());
        }
    }

    // =========================================================================
    // getPatientsByCaregiver
    // =========================================================================
    @Nested
    @DisplayName("getPatientsByCaregiver tests")
    class GetPatientsByCaregiverTests {

        @Test
        @DisplayName("getPatientsByCaregiver_hasLinks_returnsList")
        void getPatientsByCaregiver_hasLinks_returnsList() throws Exception {
            when(userRepository.findById(1L)).thenReturn(Optional.of(caregiverUser));
            when(caregiverPatientLinkRepository.findActivePatientsByCaregiver(eq(caregiverUser), any(LocalDateTime.class)))
                    .thenReturn(List.of(link));
            stubNameLookups();

            final List<CaregiverPatientLinkResponse> result = caregiverPatientLinkService.getPatientsByCaregiver(1L);

            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals("John Smith", result.get(0).patientName());
        }

        @Test
        @DisplayName("getPatientsByCaregiver_noLinks_returnsEmptyList")
        void getPatientsByCaregiver_noLinks_returnsEmptyList() throws Exception {
            when(userRepository.findById(1L)).thenReturn(Optional.of(caregiverUser));
            when(caregiverPatientLinkRepository.findActivePatientsByCaregiver(eq(caregiverUser), any(LocalDateTime.class)))
                    .thenReturn(Collections.emptyList());

            final List<CaregiverPatientLinkResponse> result = caregiverPatientLinkService.getPatientsByCaregiver(1L);

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("getPatientsByCaregiver_caregiverNotFound_throwsAppException")
        void getPatientsByCaregiver_caregiverNotFound_throwsAppException() throws Exception {
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            final AppException ex = assertThrows(AppException.class,
                    () -> caregiverPatientLinkService.getPatientsByCaregiver(999L));

            assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
            assertEquals("Caregiver not found", ex.getMessage());
        }
    }

    // =========================================================================
    // getCaregiversByPatient
    // =========================================================================
    @Nested
    @DisplayName("getCaregiversByPatient tests")
    class GetCaregiversByPatientTests {

        @Test
        @DisplayName("getCaregiversByPatient_hasLinks_returnsList")
        void getCaregiversByPatient_hasLinks_returnsList() throws Exception {
            when(userRepository.findById(2L)).thenReturn(Optional.of(patientUser));
            when(caregiverPatientLinkRepository.findActiveCaregiversByPatient(eq(patientUser), any(LocalDateTime.class)))
                    .thenReturn(List.of(link));
            stubNameLookups();

            final List<CaregiverPatientLinkResponse> result = caregiverPatientLinkService.getCaregiversByPatient(2L);

            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals("Jane Doe", result.get(0).caregiverName());
        }

        @Test
        @DisplayName("getCaregiversByPatient_noLinks_returnsEmptyList")
        void getCaregiversByPatient_noLinks_returnsEmptyList() throws Exception {
            when(userRepository.findById(2L)).thenReturn(Optional.of(patientUser));
            when(caregiverPatientLinkRepository.findActiveCaregiversByPatient(eq(patientUser), any(LocalDateTime.class)))
                    .thenReturn(Collections.emptyList());

            final List<CaregiverPatientLinkResponse> result = caregiverPatientLinkService.getCaregiversByPatient(2L);

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("getCaregiversByPatient_patientNotFound_throwsAppException")
        void getCaregiversByPatient_patientNotFound_throwsAppException() throws Exception {
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            final AppException ex = assertThrows(AppException.class,
                    () -> caregiverPatientLinkService.getCaregiversByPatient(999L));

            assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
            assertEquals("Patient not found", ex.getMessage());
        }
    }

    // =========================================================================
    // hasAccessToPatient
    // =========================================================================
    @Nested
    @DisplayName("hasAccessToPatient tests")
    class HasAccessToPatientTests {

        @Test
        @DisplayName("hasAccessToPatient_activeNonExpiredLink_returnsTrue")
        void hasAccessToPatient_activeNonExpiredLink_returnsTrue() throws Exception {
            when(userRepository.findById(1L)).thenReturn(Optional.of(caregiverUser));
            when(userRepository.findById(2L)).thenReturn(Optional.of(patientUser));
            when(caregiverPatientLinkRepository.existsActiveNonExpiredLink(eq(caregiverUser), eq(patientUser), any(LocalDateTime.class)))
                    .thenReturn(true);

            final boolean result = caregiverPatientLinkService.hasAccessToPatient(1L, 2L);

            assertTrue(result);
        }

        @Test
        @DisplayName("hasAccessToPatient_noActiveLink_returnsFalse")
        void hasAccessToPatient_noActiveLink_returnsFalse() throws Exception {
            when(userRepository.findById(1L)).thenReturn(Optional.of(caregiverUser));
            when(userRepository.findById(2L)).thenReturn(Optional.of(patientUser));
            when(caregiverPatientLinkRepository.existsActiveNonExpiredLink(eq(caregiverUser), eq(patientUser), any(LocalDateTime.class)))
                    .thenReturn(false);

            final boolean result = caregiverPatientLinkService.hasAccessToPatient(1L, 2L);

            assertFalse(result);
        }

        @Test
        @DisplayName("hasAccessToPatient_caregiverUserNull_returnsFalse")
        void hasAccessToPatient_caregiverUserNull_returnsFalse() throws Exception {
            when(userRepository.findById(1L)).thenReturn(Optional.empty());
            when(userRepository.findById(2L)).thenReturn(Optional.of(patientUser));

            final boolean result = caregiverPatientLinkService.hasAccessToPatient(1L, 2L);

            assertFalse(result);
        }

        @Test
        @DisplayName("hasAccessToPatient_patientUserNull_returnsFalse")
        void hasAccessToPatient_patientUserNull_returnsFalse() throws Exception {
            when(userRepository.findById(1L)).thenReturn(Optional.of(caregiverUser));
            when(userRepository.findById(2L)).thenReturn(Optional.empty());

            final boolean result = caregiverPatientLinkService.hasAccessToPatient(1L, 2L);

            assertFalse(result);
        }

        @Test
        @DisplayName("hasAccessToPatient_bothUsersNull_returnsFalse")
        void hasAccessToPatient_bothUsersNull_returnsFalse() throws Exception {
            when(userRepository.findById(1L)).thenReturn(Optional.empty());
            when(userRepository.findById(2L)).thenReturn(Optional.empty());

            final boolean result = caregiverPatientLinkService.hasAccessToPatient(1L, 2L);

            assertFalse(result);
        }
    }

    // =========================================================================
    // getAllLinks
    // =========================================================================
    @Nested
    @DisplayName("getAllLinks tests")
    class GetAllLinksTests {

        @Test
        @DisplayName("getAllLinks_hasLinks_returnsList")
        void getAllLinks_hasLinks_returnsList() throws Exception {
            when(caregiverPatientLinkRepository.findAll()).thenReturn(List.of(link));
            stubNameLookups();

            final List<CaregiverPatientLinkResponse> result = caregiverPatientLinkService.getAllLinks();

            assertNotNull(result);
            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("getAllLinks_noLinks_returnsEmptyList")
        void getAllLinks_noLinks_returnsEmptyList() throws Exception {
            when(caregiverPatientLinkRepository.findAll()).thenReturn(Collections.emptyList());

            final List<CaregiverPatientLinkResponse> result = caregiverPatientLinkService.getAllLinks();

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }

    // =========================================================================
    // cleanupExpiredLinks
    // =========================================================================
    @Nested
    @DisplayName("cleanupExpiredLinks tests")
    class CleanupExpiredLinksTests {

        @Test
        @DisplayName("cleanupExpiredLinks_expiredLinksExist_setsExpiredStatus")
        void cleanupExpiredLinks_expiredLinksExist_setsExpiredStatus() throws Exception {
            final CaregiverPatientLink expiredLink1 = new CaregiverPatientLink();
            expiredLink1.setId(200L);
            expiredLink1.setStatus(CaregiverPatientLink.LinkStatus.ACTIVE);

            final CaregiverPatientLink expiredLink2 = new CaregiverPatientLink();
            expiredLink2.setId(201L);
            expiredLink2.setStatus(CaregiverPatientLink.LinkStatus.ACTIVE);

            when(caregiverPatientLinkRepository.findExpiredActiveLinks(any(LocalDateTime.class)))
                    .thenReturn(List.of(expiredLink1, expiredLink2));

            caregiverPatientLinkService.cleanupExpiredLinks();

            assertEquals(CaregiverPatientLink.LinkStatus.EXPIRED, expiredLink1.getStatus());
            assertEquals(CaregiverPatientLink.LinkStatus.EXPIRED, expiredLink2.getStatus());
            verify(caregiverPatientLinkRepository, times(2)).save(any(CaregiverPatientLink.class));
        }

        @Test
        @DisplayName("cleanupExpiredLinks_noExpiredLinks_noSaves")
        void cleanupExpiredLinks_noExpiredLinks_noSaves() throws Exception {
            when(caregiverPatientLinkRepository.findExpiredActiveLinks(any(LocalDateTime.class)))
                    .thenReturn(Collections.emptyList());

            caregiverPatientLinkService.cleanupExpiredLinks();

            verify(caregiverPatientLinkRepository, never()).save(any(CaregiverPatientLink.class));
        }
    }

    // =========================================================================
    // createPermanentLink
    // =========================================================================
    @Nested
    @DisplayName("createPermanentLink tests")
    class CreatePermanentLinkTests {

        @Test
        @DisplayName("createPermanentLink_noExistingLink_createsLink")
        void createPermanentLink_noExistingLink_createsLink() throws Exception {
            when(userRepository.findById(1L)).thenReturn(Optional.of(caregiverUser));
            when(userRepository.findById(2L)).thenReturn(Optional.of(patientUser));
            when(caregiverPatientLinkRepository.existsActiveNonExpiredLink(eq(caregiverUser), eq(patientUser), any(LocalDateTime.class)))
                    .thenReturn(false);

            caregiverPatientLinkService.createPermanentLink(1L, 2L, "permanent notes");

            verify(caregiverPatientLinkRepository).save(any(CaregiverPatientLink.class));
        }

        @Test
        @DisplayName("createPermanentLink_existingActiveLink_doesNotCreateDuplicate")
        void createPermanentLink_existingActiveLink_doesNotCreateDuplicate() throws Exception {
            when(userRepository.findById(1L)).thenReturn(Optional.of(caregiverUser));
            when(userRepository.findById(2L)).thenReturn(Optional.of(patientUser));
            when(caregiverPatientLinkRepository.existsActiveNonExpiredLink(eq(caregiverUser), eq(patientUser), any(LocalDateTime.class)))
                    .thenReturn(true);

            caregiverPatientLinkService.createPermanentLink(1L, 2L, "notes");

            verify(caregiverPatientLinkRepository, never()).save(any(CaregiverPatientLink.class));
        }

        @Test
        @DisplayName("createPermanentLink_caregiverNotFound_throwsAppException")
        void createPermanentLink_caregiverNotFound_throwsAppException() throws Exception {
            when(userRepository.findById(1L)).thenReturn(Optional.empty());

            final AppException ex = assertThrows(AppException.class,
                    () -> caregiverPatientLinkService.createPermanentLink(1L, 2L, "notes"));

            assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
            assertEquals("Caregiver not found", ex.getMessage());
        }

        @Test
        @DisplayName("createPermanentLink_patientNotFound_throwsAppException")
        void createPermanentLink_patientNotFound_throwsAppException() throws Exception {
            when(userRepository.findById(1L)).thenReturn(Optional.of(caregiverUser));
            when(userRepository.findById(2L)).thenReturn(Optional.empty());

            final AppException ex = assertThrows(AppException.class,
                    () -> caregiverPatientLinkService.createPermanentLink(1L, 2L, "notes"));

            assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
            assertEquals("Patient not found", ex.getMessage());
        }
    }

    // =========================================================================
    // hasActiveLink
    // =========================================================================
    @Nested
    @DisplayName("hasActiveLink tests")
    class HasActiveLinkTests {

        @Test
        @DisplayName("hasActiveLink_linkExists_returnsTrue")
        void hasActiveLink_linkExists_returnsTrue() throws Exception {
            when(userRepository.findById(1L)).thenReturn(Optional.of(caregiverUser));
            when(userRepository.findById(2L)).thenReturn(Optional.of(patientUser));
            when(caregiverPatientLinkRepository.existsByCaregiverUserAndPatientUserAndStatus(
                    caregiverUser, patientUser, CaregiverPatientLink.LinkStatus.ACTIVE)).thenReturn(true);

            final boolean result = caregiverPatientLinkService.hasActiveLink(1L, 2L);

            assertTrue(result);
        }

        @Test
        @DisplayName("hasActiveLink_noLink_returnsFalse")
        void hasActiveLink_noLink_returnsFalse() throws Exception {
            when(userRepository.findById(1L)).thenReturn(Optional.of(caregiverUser));
            when(userRepository.findById(2L)).thenReturn(Optional.of(patientUser));
            when(caregiverPatientLinkRepository.existsByCaregiverUserAndPatientUserAndStatus(
                    caregiverUser, patientUser, CaregiverPatientLink.LinkStatus.ACTIVE)).thenReturn(false);

            final boolean result = caregiverPatientLinkService.hasActiveLink(1L, 2L);

            assertFalse(result);
        }

        @Test
        @DisplayName("hasActiveLink_caregiverNotFound_throwsAppException")
        void hasActiveLink_caregiverNotFound_throwsAppException() throws Exception {
            when(userRepository.findById(1L)).thenReturn(Optional.empty());

            final AppException ex = assertThrows(AppException.class,
                    () -> caregiverPatientLinkService.hasActiveLink(1L, 2L));

            assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
            assertEquals("Caregiver user not found", ex.getMessage());
        }

        @Test
        @DisplayName("hasActiveLink_patientNotFound_throwsAppException")
        void hasActiveLink_patientNotFound_throwsAppException() throws Exception {
            when(userRepository.findById(1L)).thenReturn(Optional.of(caregiverUser));
            when(userRepository.findById(2L)).thenReturn(Optional.empty());

            final AppException ex = assertThrows(AppException.class,
                    () -> caregiverPatientLinkService.hasActiveLink(1L, 2L));

            assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
            assertEquals("Patient user not found", ex.getMessage());
        }
    }

    // =========================================================================
    // toCaregiverPatientLinkResponse (private helper - tested indirectly)
    // =========================================================================
    @Nested
    @DisplayName("toCaregiverPatientLinkResponse mapping tests")
    class ResponseMappingTests {

        @Test
        @DisplayName("toCaregiverPatientLinkResponse_createdByNull_showsSystem")
        void toCaregiverPatientLinkResponse_createdByNull_showsSystem() throws Exception {
            link.setCreatedBy(null);

            when(caregiverPatientLinkRepository.findAll()).thenReturn(List.of(link));
            stubNameLookups();

            final List<CaregiverPatientLinkResponse> result = caregiverPatientLinkService.getAllLinks();

            assertEquals(1, result.size());
            assertEquals("System", result.get(0).createdBy());
        }

        @Test
        @DisplayName("toCaregiverPatientLinkResponse_createdByPatientRole_usesPatientName")
        void toCaregiverPatientLinkResponse_createdByPatientRole_usesPatientName() throws Exception {
            final User patientCreator = new User();
            patientCreator.setId(10L);
            patientCreator.setEmail("patientcreator@example.com");
            patientCreator.setRole(Role.PATIENT);
            patientCreator.setPassword("password");

            final Patient patientProfile = Patient.builder()
                    .id(10L)
                    .firstName("Pat")
                    .lastName("Creator")
                    .user(patientCreator)
                    .build();

            link.setCreatedBy(patientCreator);

            when(caregiverPatientLinkRepository.findAll()).thenReturn(List.of(link));
            stubNameLookups();
            when(patientRepository.findByUser(patientCreator)).thenReturn(Optional.of(patientProfile));

            final List<CaregiverPatientLinkResponse> result = caregiverPatientLinkService.getAllLinks();

            assertEquals("Pat Creator", result.get(0).createdBy());
        }

        @Test
        @DisplayName("toCaregiverPatientLinkResponse_createdByCaregiverRole_usesCaregiverName")
        void toCaregiverPatientLinkResponse_createdByCaregiverRole_usesCaregiverName() throws Exception {
            final User caregiverCreator = new User();
            caregiverCreator.setId(11L);
            caregiverCreator.setEmail("caregivercreator@example.com");
            caregiverCreator.setRole(Role.CAREGIVER);
            caregiverCreator.setPassword("password");

            final Caregiver caregiverProfile = Caregiver.builder()
                    .id(11L)
                    .firstName("Care")
                    .lastName("Giver")
                    .user(caregiverCreator)
                    .build();

            link.setCreatedBy(caregiverCreator);

            when(caregiverPatientLinkRepository.findAll()).thenReturn(List.of(link));
            stubNameLookups();
            when(caregiverRepository.findByUser(caregiverCreator)).thenReturn(Optional.of(caregiverProfile));

            final List<CaregiverPatientLinkResponse> result = caregiverPatientLinkService.getAllLinks();

            assertEquals("Care Giver", result.get(0).createdBy());
        }

        @Test
        @DisplayName("toCaregiverPatientLinkResponse_createdByAdminRole_usesEmail")
        void toCaregiverPatientLinkResponse_createdByAdminRole_usesEmail() throws Exception {
            link.setCreatedBy(creatorUser); // creatorUser has ADMIN role

            when(caregiverPatientLinkRepository.findAll()).thenReturn(List.of(link));
            stubNameLookups();

            final List<CaregiverPatientLinkResponse> result = caregiverPatientLinkService.getAllLinks();

            assertEquals("admin@example.com", result.get(0).createdBy());
        }

        @Test
        @DisplayName("toCaregiverPatientLinkResponse_createdByFamilyMemberRole_usesEmail")
        void toCaregiverPatientLinkResponse_createdByFamilyMemberRole_usesEmail() throws Exception {
            final User familyUser = new User();
            familyUser.setId(20L);
            familyUser.setEmail("family@example.com");
            familyUser.setRole(Role.FAMILY_MEMBER);
            familyUser.setPassword("password");

            link.setCreatedBy(familyUser);

            when(caregiverPatientLinkRepository.findAll()).thenReturn(List.of(link));
            stubNameLookups();

            final List<CaregiverPatientLinkResponse> result = caregiverPatientLinkService.getAllLinks();

            assertEquals("family@example.com", result.get(0).createdBy());
        }

        @Test
        @DisplayName("toCaregiverPatientLinkResponse_caregiverProfileNotFound_usesEmail")
        void toCaregiverPatientLinkResponse_caregiverProfileNotFound_usesEmail() throws Exception {
            when(caregiverPatientLinkRepository.findAll()).thenReturn(List.of(link));
            when(caregiverRepository.findByUser(caregiverUser)).thenReturn(Optional.empty());
            when(patientRepository.findByUser(patientUser)).thenReturn(Optional.of(patient));

            final List<CaregiverPatientLinkResponse> result = caregiverPatientLinkService.getAllLinks();

            assertEquals("caregiver@example.com", result.get(0).caregiverName());
        }

        @Test
        @DisplayName("toCaregiverPatientLinkResponse_patientProfileNotFound_usesEmail")
        void toCaregiverPatientLinkResponse_patientProfileNotFound_usesEmail() throws Exception {
            when(caregiverPatientLinkRepository.findAll()).thenReturn(List.of(link));
            when(caregiverRepository.findByUser(caregiverUser)).thenReturn(Optional.of(caregiver));
            when(patientRepository.findByUser(patientUser)).thenReturn(Optional.empty());

            final List<CaregiverPatientLinkResponse> result = caregiverPatientLinkService.getAllLinks();

            assertEquals("patient@example.com", result.get(0).patientName());
        }

        @Test
        @DisplayName("toCaregiverPatientLinkResponse_mapsAllFields_correctly")
        void toCaregiverPatientLinkResponse_mapsAllFields_correctly() throws Exception {
            final LocalDateTime now = LocalDateTime.now();
            final LocalDateTime expiry = now.plusDays(30);
            link.setCreatedAt(now);
            link.setExpiresAt(expiry);

            when(caregiverPatientLinkRepository.findAll()).thenReturn(List.of(link));
            stubNameLookups();

            final List<CaregiverPatientLinkResponse> result = caregiverPatientLinkService.getAllLinks();

            final CaregiverPatientLinkResponse response = result.get(0);
            assertEquals(100L, response.id());
            assertEquals(1L, response.caregiverUserId());
            assertEquals("Jane Doe", response.caregiverName());
            assertEquals("caregiver@example.com", response.caregiverEmail());
            assertEquals(2L, response.patientUserId());
            assertEquals("John Smith", response.patientName());
            assertEquals("patient@example.com", response.patientEmail());
            assertEquals("ACTIVE", response.status());
            assertEquals("PERMANENT", response.linkType());
            assertEquals(now, response.createdAt());
            assertEquals(expiry, response.expiresAt());
            assertEquals("Test link", response.notes());
        }
    }

    // =========================================================================
    // getUserName switch branches (covered via createdBy mapping above, but
    // additional edge-case tests through createLink which uses getUserName)
    // =========================================================================
    @Nested
    @DisplayName("getUserName via createLink - PATIENT-role creator")
    class GetUserNameViaCreateLinkTests {

        @Test
        @DisplayName("createLink_creatorIsPatient_responseShowsPatientName")
        void createLink_creatorIsPatient_responseShowsPatientName() throws Exception {
            final User patientCreator = new User();
            patientCreator.setId(50L);
            patientCreator.setEmail("patcreator@example.com");
            patientCreator.setRole(Role.PATIENT);
            patientCreator.setPassword("password");

            final Patient patientProfile = Patient.builder()
                    .id(50L)
                    .firstName("Creator")
                    .lastName("Patient")
                    .user(patientCreator)
                    .build();

            final CreateLinkRequest request = new CreateLinkRequest(2L, "PERMANENT", null, "notes");

            when(userRepository.findById(1L)).thenReturn(Optional.of(caregiverUser));
            when(userRepository.findById(2L)).thenReturn(Optional.of(patientUser));
            when(userRepository.findById(50L)).thenReturn(Optional.of(patientCreator));
            when(caregiverPatientLinkRepository.existsByCaregiverUserAndPatientUserAndStatus(
                    caregiverUser, patientUser, CaregiverPatientLink.LinkStatus.ACTIVE)).thenReturn(false);
            when(caregiverPatientLinkRepository.save(any(CaregiverPatientLink.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            stubNameLookups();
            when(patientRepository.findByUser(patientCreator)).thenReturn(Optional.of(patientProfile));

            final CaregiverPatientLinkResponse response = caregiverPatientLinkService.createLink(1L, request, 50L);

            assertEquals("Creator Patient", response.createdBy());
        }

        @Test
        @DisplayName("createLink_creatorIsCaregiver_responseShowsCaregiverName")
        void createLink_creatorIsCaregiver_responseShowsCaregiverName() throws Exception {
            final User caregiverCreator = new User();
            caregiverCreator.setId(60L);
            caregiverCreator.setEmail("carecreator@example.com");
            caregiverCreator.setRole(Role.CAREGIVER);
            caregiverCreator.setPassword("password");

            final Caregiver caregiverProfile = Caregiver.builder()
                    .id(60L)
                    .firstName("Creator")
                    .lastName("Caregiver")
                    .user(caregiverCreator)
                    .build();

            final CreateLinkRequest request = new CreateLinkRequest(2L, "EMERGENCY", null, null);

            when(userRepository.findById(1L)).thenReturn(Optional.of(caregiverUser));
            when(userRepository.findById(2L)).thenReturn(Optional.of(patientUser));
            when(userRepository.findById(60L)).thenReturn(Optional.of(caregiverCreator));
            when(caregiverPatientLinkRepository.existsByCaregiverUserAndPatientUserAndStatus(
                    caregiverUser, patientUser, CaregiverPatientLink.LinkStatus.ACTIVE)).thenReturn(false);
            when(caregiverPatientLinkRepository.save(any(CaregiverPatientLink.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            stubNameLookups();
            when(caregiverRepository.findByUser(caregiverCreator)).thenReturn(Optional.of(caregiverProfile));

            final CaregiverPatientLinkResponse response = caregiverPatientLinkService.createLink(1L, request, 60L);

            assertEquals("Creator Caregiver", response.createdBy());
        }
    }

    // =========================================================================
    // Edge case: lowercase linkType in createLink (tests toUpperCase)
    // =========================================================================
    @Nested
    @DisplayName("case conversion tests")
    class CaseConversionTests {

        @Test
        @DisplayName("createLink_lowercaseLinkType_convertsToUpperCase")
        void createLink_lowercaseLinkType_convertsToUpperCase() throws Exception {
            final CreateLinkRequest request = new CreateLinkRequest(2L, "emergency", null, null);

            when(userRepository.findById(1L)).thenReturn(Optional.of(caregiverUser));
            when(userRepository.findById(2L)).thenReturn(Optional.of(patientUser));
            when(userRepository.findById(3L)).thenReturn(Optional.of(creatorUser));
            when(caregiverPatientLinkRepository.existsByCaregiverUserAndPatientUserAndStatus(
                    caregiverUser, patientUser, CaregiverPatientLink.LinkStatus.ACTIVE)).thenReturn(false);
            when(caregiverPatientLinkRepository.save(any(CaregiverPatientLink.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            stubNameLookups();

            final CaregiverPatientLinkResponse response = caregiverPatientLinkService.createLink(1L, request, 3L);

            assertEquals("EMERGENCY", response.linkType());
        }

        @Test
        @DisplayName("updateLink_lowercaseStatus_convertsToUpperCase")
        void updateLink_lowercaseStatus_convertsToUpperCase() throws Exception {
            final UpdateLinkRequest request = new UpdateLinkRequest("suspended", null, null, null);

            when(caregiverPatientLinkRepository.findById(100L)).thenReturn(Optional.of(link));
            when(caregiverPatientLinkRepository.save(any(CaregiverPatientLink.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            stubNameLookups();

            final CaregiverPatientLinkResponse response = caregiverPatientLinkService.updateLink(100L, request, 3L);

            assertEquals("SUSPENDED", response.status());
        }

        @Test
        @DisplayName("updateLink_lowercaseLinkType_convertsToUpperCase")
        void updateLink_lowercaseLinkType_convertsToUpperCase() throws Exception {
            final UpdateLinkRequest request = new UpdateLinkRequest(null, "temporary", null, null);

            when(caregiverPatientLinkRepository.findById(100L)).thenReturn(Optional.of(link));
            when(caregiverPatientLinkRepository.save(any(CaregiverPatientLink.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            stubNameLookups();

            final CaregiverPatientLinkResponse response = caregiverPatientLinkService.updateLink(100L, request, 3L);

            assertEquals("TEMPORARY", response.linkType());
        }
    }
}
