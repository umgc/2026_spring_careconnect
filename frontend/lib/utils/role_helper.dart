/// Role-based access control helper functions
/// Works with existing UserSession.role (String type)
class RoleHelper {
  /// Check if user is admin
  static bool isAdmin(String role) {
    return role.toUpperCase() == 'ADMIN';
  }

  /// Check if user is caregiver (includes FAMILY_LINK)
  static bool isCaregiver(String role) {
    final r = role.toUpperCase();
    return r == 'CAREGIVER' || r == 'FAMILY_LINK';
  }

  /// Check if user is patient
  static bool isPatient(String role) {
    return role.toUpperCase() == 'PATIENT';
  }

  /// Check if user is family member
  static bool isFamilyMember(String role) {
    return role.toUpperCase() == 'FAMILY_MEMBER';
  }

  /// Check if user is caregiver OR admin
  static bool isCaregiverOrAdmin(String role) {
    final r = role.toUpperCase();
    return r == 'ADMIN' || r == 'CAREGIVER' || r == 'FAMILY_LINK';
  }

  /// Check if user can modify data (not family member)
  static bool canModifyData(String role) {
    return !isFamilyMember(role);
  }

  /// Check if user can manage patients
  static bool canManagePatients(String role) {
    return isCaregiverOrAdmin(role);
  }

  /// Get role display name
  static String getRoleDisplayName(String role) {
    switch (role.toUpperCase()) {
      case 'ADMIN':
        return 'Administrator';
      case 'CAREGIVER':
        return 'Caregiver';
      case 'FAMILY_LINK':
        return 'Family Link Caregiver';
      case 'PATIENT':
        return 'Patient';
      case 'FAMILY_MEMBER':
        return 'Family Member';
      default:
        return role;
    }
  }

  /// Get role color for UI (returns int for Color constructor)
  static int getRoleColorValue(String role) {
    switch (role.toUpperCase()) {
      case 'ADMIN':
        return 0xFFD32F2F; // Red 700
      case 'CAREGIVER':
      case 'FAMILY_LINK':
        return 0xFF1976D2; // Blue 700
      case 'PATIENT':
        return 0xFF388E3C; // Green 700
      case 'FAMILY_MEMBER':
        return 0xFF7B1FA2; // Purple 700
      default:
        return 0xFF616161; // Grey 700
    }
  }

  /// Get login route for role (matches your existing RoleValidator)
  static String getLoginRoute(String role) {
    switch (role.toUpperCase()) {
      case 'CAREGIVER':
      case 'FAMILY_LINK':
      case 'ADMIN':
        return '/login/caregiver';
      case 'PATIENT':
      case 'FAMILY_MEMBER':
        return '/login/patient';
      default:
        return '/login';
    }
  }
}
