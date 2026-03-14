package com.careconnect.controller;

import com.careconnect.model.USPSDigest;
import com.careconnect.service.USPSDigestService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class USPSControllerTest {

    @Mock
    private USPSDigestService service;

    @InjectMocks
    private USPSController controller;

    private USPSDigest emptyDigest() throws Exception {
        return new USPSDigest(null, List.of(), List.of());
    }

    // ─── getDigest ────────────────────────────────────────────────────────────

    @Test
    void getDigest_jwtPresentDatePresent_callsDigestForDate() throws Exception {
        final Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("user-123");
        final LocalDate date = LocalDate.of(2025, 1, 15);
        final USPSDigest digest = emptyDigest();
        when(service.digestForDate("user-123", date)).thenReturn(Optional.of(digest));

        final ResponseEntity<USPSDigest> response = controller.getDigest(jwt, date);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(digest);
        verify(service).digestForDate("user-123", date);
    }

    @Test
    void getDigest_jwtPresentDateNull_callsLatestForUser() throws Exception {
        final Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("user-123");
        final USPSDigest digest = emptyDigest();
        when(service.latestForUser("user-123")).thenReturn(Optional.of(digest));

        final ResponseEntity<USPSDigest> response = controller.getDigest(jwt, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(digest);
        verify(service).latestForUser("user-123");
    }

    @Test
    void getDigest_jwtNullDateNull_usesDemoUserFallback() throws Exception {
        when(service.latestForUser("demo-user")).thenReturn(Optional.empty());

        final ResponseEntity<USPSDigest> response = controller.getDigest(null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // orElseGet returns a new empty digest
        assertThat(response.getBody()).isNotNull();
        verify(service).latestForUser("demo-user");
    }

    @Test
    void getDigest_jwtNullDatePresent_callsDigestForDateWithDemoUser() throws Exception {
        final LocalDate date = LocalDate.of(2025, 3, 10);
        when(service.digestForDate("demo-user", date)).thenReturn(Optional.empty());

        final ResponseEntity<USPSDigest> response = controller.getDigest(null, date);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        verify(service).digestForDate("demo-user", date);
    }
}
