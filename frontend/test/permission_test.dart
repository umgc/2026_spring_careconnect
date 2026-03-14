import 'package:flutter_test/flutter_test.dart';
import 'package:care_connect_app/models/permission.dart';

void main() {
  group('Permission Enum Tests', () {
    test('Permission.fromString parses correctly', () {
      expect(
        Permission.fromString('VIEW_ALL_USERS'),
        Permission.viewAllUsers,
      );
      expect(
        Permission.fromString('CREATE_PATIENTS'),
        Permission.createPatients,
      );
      expect(
        Permission.fromString('DELETE_TASKS'),
        Permission.deleteTasks,
      );
    });

    test('Permission.fromString throws on invalid permission', () {
      expect(() => Permission.fromString('INVALID'), throwsArgumentError);
      expect(() => Permission.fromString(''), throwsArgumentError);
    });

    test('isAdminOnly identifies admin permissions correctly', () {
      // Admin-only permissions
      expect(Permission.viewAllUsers.isAdminOnly, true);
      expect(Permission.manageUsers.isAdminOnly, true);
      expect(Permission.assignRoles.isAdminOnly, true);
      expect(Permission.deletePatients.isAdminOnly, true);
      expect(Permission.manageSystemSettings.isAdminOnly, true);
      expect(Permission.viewAuditLogs.isAdminOnly, true);
      
      // Non-admin permissions
      expect(Permission.viewTasks.isAdminOnly, false);
      expect(Permission.createPatients.isAdminOnly, false);
      expect(Permission.sendMessages.isAdminOnly, false);
      expect(Permission.viewHealthData.isAdminOnly, false);
    });

    test('displayName formats correctly', () {
      expect(
        Permission.viewAllUsers.displayName,
        'View All Users',
      );
      expect(
        Permission.createPatients.displayName,
        'Create Patients',
      );
      expect(
        Permission.viewHealthData.displayName,
        'View Health Data',
      );
    });

    test('description returns non-empty string', () {
      for (var permission in Permission.values) {
        expect(permission.description.isNotEmpty, true,
            reason: '${permission.name} should have a description');
      }
    });

    test('toBackendString returns correct enum name', () {
      expect(Permission.viewAllUsers.toBackendString(), 'VIEW_ALL_USERS');
      expect(Permission.createTasks.toBackendString(), 'CREATE_TASKS');
      expect(Permission.viewHealthData.toBackendString(), 'VIEW_HEALTH_DATA');
    });

    test('all 26 permissions exist', () {
      expect(Permission.values.length, 26,
          reason: 'Should have exactly 26 permissions matching backend');
    });

    test('permission categories are correct', () {
      // User Management (3 permissions)
      expect(Permission.values.contains(Permission.viewAllUsers), true);
      expect(Permission.values.contains(Permission.manageUsers), true);
      expect(Permission.values.contains(Permission.assignRoles), true);
      
      // Patient Management (4 permissions)
      expect(Permission.values.contains(Permission.viewAssignedPatients), true);
      expect(Permission.values.contains(Permission.createPatients), true);
      expect(Permission.values.contains(Permission.updatePatients), true);
      expect(Permission.values.contains(Permission.deletePatients), true);
      
      // Health Data (3 permissions)
      expect(Permission.values.contains(Permission.viewHealthData), true);
      expect(Permission.values.contains(Permission.recordHealthData), true);
      expect(Permission.values.contains(Permission.exportHealthData), true);
      
      // Task Management (5 permissions)
      expect(Permission.values.contains(Permission.viewTasks), true);
      expect(Permission.values.contains(Permission.createTasks), true);
      expect(Permission.values.contains(Permission.updateTasks), true);
      expect(Permission.values.contains(Permission.deleteTasks), true);
      expect(Permission.values.contains(Permission.completeTasks), true);
      
      // Medication Management (2 permissions)
      expect(Permission.values.contains(Permission.viewMedications), true);
      expect(Permission.values.contains(Permission.manageMedications), true);
      
      // Analytics & Reports (2 permissions)
      expect(Permission.values.contains(Permission.viewAnalytics), true);
      expect(Permission.values.contains(Permission.exportReports), true);
      
      // Messaging (2 permissions)
      expect(Permission.values.contains(Permission.viewMessages), true);
      expect(Permission.values.contains(Permission.sendMessages), true);
      
      // Billing (2 permissions)
      expect(Permission.values.contains(Permission.viewBilling), true);
      expect(Permission.values.contains(Permission.manageSubscriptions), true);
      
      // System (2 permissions)
      expect(Permission.values.contains(Permission.manageSystemSettings), true);
      expect(Permission.values.contains(Permission.viewAuditLogs), true);
    });

    test('permission enum names match backend format', () {
      // Verify camelCase Dart names convert to SCREAMING_SNAKE_CASE backend names
      final testCases = {
        Permission.viewAllUsers: 'VIEW_ALL_USERS',
        Permission.createPatients: 'CREATE_PATIENTS',
        Permission.recordHealthData: 'RECORD_HEALTH_DATA',
        Permission.manageSystemSettings: 'MANAGE_SYSTEM_SETTINGS',
        Permission.viewAuditLogs: 'VIEW_AUDIT_LOGS',
      };

      testCases.forEach((permission, expectedBackendName) {
        expect(
          permission.toBackendString(),
          expectedBackendName,
          reason: '${permission.name} should convert to $expectedBackendName',
        );
      });
    });
  });
}