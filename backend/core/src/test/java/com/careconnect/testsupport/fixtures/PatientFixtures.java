package com.careconnect.testsupport.fixtures;

import com.careconnect.model.Patient;

/**
 * Shared patient fixture builders for backend unit tests.
 *
 * <p>
 * These fixtures keep patient identity data stable so repository/service tests
 * can focus on behavior, not ad-hoc object setup.
 * </p>
 */
public final class PatientFixtures {

    private PatientFixtures() {
        // Utility class
    }

    /**
     * Returns a baseline patient entity for task ownership test flows.
     *
     * <p>
     * Use when the test only needs a valid patient reference without extra
     * domain-specific fields.
     * </p>
     */
    public static Patient basicPatient() {
        return Patient.builder()
                .id(5L)
                .firstName("Jane")
                .lastName("Smith")
                .email("jane.smith@example.com")
                .build();
    }

    /**
     * Returns the baseline patient with an explicit ID override.
     *
     * <p>
     * Use when multiple test cases need different patient IDs with otherwise
     * identical fixture values.
     * </p>
     */
    public static Patient patientWithId(Long id) {
        final Patient patient = basicPatient();
        patient.setId(id);
        return patient;
    }
}
