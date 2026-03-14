package com.careconnect.service;

import com.careconnect.model.CheckIn;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CheckInServiceTest {

    private CheckInService checkInService;

    @BeforeEach
    void setUp() throws Exception {
        checkInService = new CheckInService();
    }

    @Test
    @DisplayName("getAllCheckIns returns an empty list")
    void getAllCheckIns_returnsEmptyList() throws Exception {
        final List<CheckIn> result = checkInService.getAllCheckIns();
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("getAllCheckIns returns an immutable List.of()")
    void getAllCheckIns_returnsImmutableList() throws Exception {
        final List<CheckIn> result = checkInService.getAllCheckIns();
        assertThrows(UnsupportedOperationException.class, () -> result.add(new CheckIn()));
    }

    @Test
    @DisplayName("getCheckInByID returns a non-null CheckIn instance")
    void getCheckInByID_returnsNonNullCheckIn() throws Exception {
        final CheckIn result = checkInService.getCheckInByID(1L);
        assertNotNull(result);
    }

    @Test
    @DisplayName("getCheckInByID returns a new CheckIn with null id")
    void getCheckInByID_returnsCheckInWithNullId() throws Exception {
        final CheckIn result = checkInService.getCheckInByID(1L);
        assertNull(result.getId());
    }

    @Test
    @DisplayName("getCheckInByID ignores the provided id parameter")
    void getCheckInByID_ignoresProvidedId() throws Exception {
        final CheckIn result1 = checkInService.getCheckInByID(42L);
        final CheckIn result2 = checkInService.getCheckInByID(99L);
        assertNull(result1.getId());
        assertNull(result2.getId());
        assertNotSame(result1, result2);
    }

    @Test
    @DisplayName("getCheckInByID with null id still returns a new CheckIn")
    void getCheckInByID_withNullId_returnsCheckIn() throws Exception {
        final CheckIn result = checkInService.getCheckInByID(null);
        assertNotNull(result);
    }
}
