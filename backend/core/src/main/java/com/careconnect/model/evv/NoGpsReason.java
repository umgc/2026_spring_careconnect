package com.careconnect.model.evv;

/**
 * Reason why GPS location could not be captured.
 * Required by federal EVV regulations when GPS is unavailable.
 */
public enum NoGpsReason {
    GPS_SERVICE_DISABLED,   // Device location services turned off
    PERMISSION_DENIED,      // App location permission not granted
    GPS_TIMEOUT,            // GPS signal timed out
    INDOOR_LOCATION,        // Indoors - no GPS signal
    COMMUNITY_VISIT,        // Visit at community site - manual address used
    HOME_VISIT_ADDRESS_USED, // Routine home visit - patient address sufficient
    OTHER                   // Other reason (see manual address notes)
}
