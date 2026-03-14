package com.careconnect.model.evv;

/**
 * Enum representing the type/source of a location
 */
public enum EvvLocationType {
    GPS,              // Location from GPS coordinates
    PATIENT_ADDRESS,  // Location from patient's registered address
    MANUAL            // Manually entered address or coordinates (e.g. community visit)
}

