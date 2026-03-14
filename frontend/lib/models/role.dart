/// User roles in the CareConnect system
/// Must match backend Role enum exactly
enum Role {
  admin,
  caregiver,
  patient,
  familyMember;

  /// Convert from backend string (SCREAMING_SNAKE_CASE) to Dart enum
  static Role fromString(String roleString) {
    switch (roleString.toUpperCase()) {
      case 'ADMIN':
        return Role.admin;
      case 'CAREGIVER':
        return Role.caregiver;
      case 'PATIENT':
        return Role.patient;
      case 'FAMILY_MEMBER':
        return Role.familyMember;
      default:
        throw ArgumentError('Unknown role: $roleString');
    }
  }

  /// Convert to backend format (SCREAMING_SNAKE_CASE)
  String toBackendString() {
    switch (this) {
      case Role.admin:
        return 'ADMIN';
      case Role.caregiver:
        return 'CAREGIVER';
      case Role.patient:
        return 'PATIENT';
      case Role.familyMember:
        return 'FAMILY_MEMBER';
    }
  }

  /// Get display name for UI
  String get displayName {
    switch (this) {
      case Role.admin:
        return 'Administrator';
      case Role.caregiver:
        return 'Caregiver';
      case Role.patient:
        return 'Patient';
      case Role.familyMember:
        return 'Family Member';
    }
  }

  /// Get role description
  String get description {
    switch (this) {
      case Role.admin:
        return 'Full system access and administration';
      case Role.caregiver:
        return 'Manage assigned patients and their care';
      case Role.patient:
        return 'View and manage own health information';
      case Role.familyMember:
        return 'View assigned patient information (read-only)';
    }
  }

  /// Check if this role is admin
  bool get isAdmin => this == Role.admin;

  /// Check if this role is caregiver or admin
  bool get isCaregiverOrAdmin => this == Role.caregiver || this == Role.admin;

  /// Check if this role is patient
  bool get isPatient => this == Role.patient;

  /// Check if this role is family member
  bool get isFamilyMember => this == Role.familyMember;

  /// Check if this role can manage patients
  bool get canManagePatients => this == Role.admin || this == Role.caregiver;

  /// Check if this role has read-only access
  bool get isReadOnly => this == Role.familyMember;
}
