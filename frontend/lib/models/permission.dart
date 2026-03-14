/// Permissions in the CareConnect system
/// Must match backend Permission enum exactly (26 total)
enum Permission {
  // User Management (3)
  viewAllUsers,
  manageUsers,
  assignRoles,

  // Patient Management (4)
  viewAssignedPatients,
  createPatients,
  updatePatients,
  deletePatients,

  // Health Data (3)
  viewHealthData,
  recordHealthData,
  exportHealthData,

  // Task Management (5)
  viewTasks,
  createTasks,
  updateTasks,
  deleteTasks,
  completeTasks,

  // Medication Management (2)
  viewMedications,
  manageMedications,

  // Analytics & Reports (2)
  viewAnalytics,
  exportReports,

  // Messaging (2)
  viewMessages,
  sendMessages,

  // Billing (2)
  viewBilling,
  manageSubscriptions,

  // System (2)
  manageSystemSettings,
  viewAuditLogs;

  /// Convert from backend string (SCREAMING_SNAKE_CASE) to Dart enum
  static Permission fromString(String permissionString) {
    switch (permissionString.toUpperCase()) {
      // User Management
      case 'VIEW_ALL_USERS':
        return Permission.viewAllUsers;
      case 'MANAGE_USERS':
        return Permission.manageUsers;
      case 'ASSIGN_ROLES':
        return Permission.assignRoles;

      // Patient Management
      case 'VIEW_ASSIGNED_PATIENTS':
        return Permission.viewAssignedPatients;
      case 'CREATE_PATIENTS':
        return Permission.createPatients;
      case 'UPDATE_PATIENTS':
        return Permission.updatePatients;
      case 'DELETE_PATIENTS':
        return Permission.deletePatients;

      // Health Data
      case 'VIEW_HEALTH_DATA':
        return Permission.viewHealthData;
      case 'RECORD_HEALTH_DATA':
        return Permission.recordHealthData;
      case 'EXPORT_HEALTH_DATA':
        return Permission.exportHealthData;

      // Task Management
      case 'VIEW_TASKS':
        return Permission.viewTasks;
      case 'CREATE_TASKS':
        return Permission.createTasks;
      case 'UPDATE_TASKS':
        return Permission.updateTasks;
      case 'DELETE_TASKS':
        return Permission.deleteTasks;
      case 'COMPLETE_TASKS':
        return Permission.completeTasks;

      // Medication Management
      case 'VIEW_MEDICATIONS':
        return Permission.viewMedications;
      case 'MANAGE_MEDICATIONS':
        return Permission.manageMedications;

      // Analytics & Reports
      case 'VIEW_ANALYTICS':
        return Permission.viewAnalytics;
      case 'EXPORT_REPORTS':
        return Permission.exportReports;

      // Messaging
      case 'VIEW_MESSAGES':
        return Permission.viewMessages;
      case 'SEND_MESSAGES':
        return Permission.sendMessages;

      // Billing
      case 'VIEW_BILLING':
        return Permission.viewBilling;
      case 'MANAGE_SUBSCRIPTIONS':
        return Permission.manageSubscriptions;

      // System
      case 'MANAGE_SYSTEM_SETTINGS':
        return Permission.manageSystemSettings;
      case 'VIEW_AUDIT_LOGS':
        return Permission.viewAuditLogs;

      default:
        throw ArgumentError('Unknown permission: $permissionString');
    }
  }

  /// Convert to backend format (SCREAMING_SNAKE_CASE)
  String toBackendString() {
    switch (this) {
      // User Management
      case Permission.viewAllUsers:
        return 'VIEW_ALL_USERS';
      case Permission.manageUsers:
        return 'MANAGE_USERS';
      case Permission.assignRoles:
        return 'ASSIGN_ROLES';

      // Patient Management
      case Permission.viewAssignedPatients:
        return 'VIEW_ASSIGNED_PATIENTS';
      case Permission.createPatients:
        return 'CREATE_PATIENTS';
      case Permission.updatePatients:
        return 'UPDATE_PATIENTS';
      case Permission.deletePatients:
        return 'DELETE_PATIENTS';

      // Health Data
      case Permission.viewHealthData:
        return 'VIEW_HEALTH_DATA';
      case Permission.recordHealthData:
        return 'RECORD_HEALTH_DATA';
      case Permission.exportHealthData:
        return 'EXPORT_HEALTH_DATA';

      // Task Management
      case Permission.viewTasks:
        return 'VIEW_TASKS';
      case Permission.createTasks:
        return 'CREATE_TASKS';
      case Permission.updateTasks:
        return 'UPDATE_TASKS';
      case Permission.deleteTasks:
        return 'DELETE_TASKS';
      case Permission.completeTasks:
        return 'COMPLETE_TASKS';

      // Medication Management
      case Permission.viewMedications:
        return 'VIEW_MEDICATIONS';
      case Permission.manageMedications:
        return 'MANAGE_MEDICATIONS';

      // Analytics & Reports
      case Permission.viewAnalytics:
        return 'VIEW_ANALYTICS';
      case Permission.exportReports:
        return 'EXPORT_REPORTS';

      // Messaging
      case Permission.viewMessages:
        return 'VIEW_MESSAGES';
      case Permission.sendMessages:
        return 'SEND_MESSAGES';

      // Billing
      case Permission.viewBilling:
        return 'VIEW_BILLING';
      case Permission.manageSubscriptions:
        return 'MANAGE_SUBSCRIPTIONS';

      // System
      case Permission.manageSystemSettings:
        return 'MANAGE_SYSTEM_SETTINGS';
      case Permission.viewAuditLogs:
        return 'VIEW_AUDIT_LOGS';
    }
  }

  /// Get display name for UI (converts camelCase to Title Case)
  String get displayName {
    final name = toString().split('.').last;
    final result = name.replaceAllMapped(
      RegExp(r'([A-Z])'),
      (match) => ' ${match.group(0)}',
    );
    return result[0].toUpperCase() + result.substring(1);
  }

  /// Get permission description
  String get description {
    switch (this) {
      // User Management
      case Permission.viewAllUsers:
        return 'View all users in the system';
      case Permission.manageUsers:
        return 'Create, update, and delete users';
      case Permission.assignRoles:
        return 'Assign and modify user roles';

      // Patient Management
      case Permission.viewAssignedPatients:
        return 'View assigned patient information';
      case Permission.createPatients:
        return 'Create new patient records';
      case Permission.updatePatients:
        return 'Update existing patient information';
      case Permission.deletePatients:
        return 'Delete patient records';

      // Health Data
      case Permission.viewHealthData:
        return 'View patient health data';
      case Permission.recordHealthData:
        return 'Record and update health metrics';
      case Permission.exportHealthData:
        return 'Export health data reports';

      // Task Management
      case Permission.viewTasks:
        return 'View tasks and care plans';
      case Permission.createTasks:
        return 'Create new tasks';
      case Permission.updateTasks:
        return 'Update existing tasks';
      case Permission.deleteTasks:
        return 'Delete tasks';
      case Permission.completeTasks:
        return 'Mark tasks as complete';

      // Medication Management
      case Permission.viewMedications:
        return 'View medication schedules';
      case Permission.manageMedications:
        return 'Add, update, and remove medications';

      // Analytics & Reports
      case Permission.viewAnalytics:
        return 'View analytics and insights';
      case Permission.exportReports:
        return 'Generate and export reports';

      // Messaging
      case Permission.viewMessages:
        return 'View messages and communications';
      case Permission.sendMessages:
        return 'Send messages to other users';

      // Billing
      case Permission.viewBilling:
        return 'View billing and payment information';
      case Permission.manageSubscriptions:
        return 'Manage subscriptions and plans';

      // System
      case Permission.manageSystemSettings:
        return 'Configure system settings';
      case Permission.viewAuditLogs:
        return 'View system audit logs';
    }
  }

  /// Check if this permission is admin-only
  bool get isAdminOnly {
    return this == Permission.viewAllUsers ||
        this == Permission.manageUsers ||
        this == Permission.assignRoles ||
        this == Permission.deletePatients ||
        this == Permission.manageSystemSettings ||
        this == Permission.viewAuditLogs;
  }

  /// Get permission category
  PermissionCategory get category {
    if ([viewAllUsers, manageUsers, assignRoles].contains(this)) {
      return PermissionCategory.userManagement;
    }
    if ([viewAssignedPatients, createPatients, updatePatients, deletePatients]
        .contains(this)) {
      return PermissionCategory.patientManagement;
    }
    if ([viewHealthData, recordHealthData, exportHealthData].contains(this)) {
      return PermissionCategory.healthData;
    }
    if ([viewTasks, createTasks, updateTasks, deleteTasks, completeTasks]
        .contains(this)) {
      return PermissionCategory.taskManagement;
    }
    if ([viewMedications, manageMedications].contains(this)) {
      return PermissionCategory.medicationManagement;
    }
    if ([viewAnalytics, exportReports].contains(this)) {
      return PermissionCategory.analytics;
    }
    if ([viewMessages, sendMessages].contains(this)) {
      return PermissionCategory.messaging;
    }
    if ([viewBilling, manageSubscriptions].contains(this)) {
      return PermissionCategory.billing;
    }
    return PermissionCategory.system;
  }
}

/// Permission categories for grouping and display
enum PermissionCategory {
  userManagement,
  patientManagement,
  healthData,
  taskManagement,
  medicationManagement,
  analytics,
  messaging,
  billing,
  system;

  String get displayName {
    switch (this) {
      case PermissionCategory.userManagement:
        return 'User Management';
      case PermissionCategory.patientManagement:
        return 'Patient Management';
      case PermissionCategory.healthData:
        return 'Health Data';
      case PermissionCategory.taskManagement:
        return 'Task Management';
      case PermissionCategory.medicationManagement:
        return 'Medication Management';
      case PermissionCategory.analytics:
        return 'Analytics & Reports';
      case PermissionCategory.messaging:
        return 'Messaging';
      case PermissionCategory.billing:
        return 'Billing';
      case PermissionCategory.system:
        return 'System';
    }
  }
}