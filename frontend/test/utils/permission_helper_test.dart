import 'package:flutter_test/flutter_test.dart';
import 'package:care_connect_app/utils/permission_helper.dart';

void main() {
  group('PermissionHelper Tests', () {
    test('Admin has all 26 permissions', () {
      final permissions = [
        'VIEW_ALL_USERS',
        'MANAGE_USERS',
        'ASSIGN_ROLES',
        'VIEW_ALL_PATIENTS',
        'VIEW_ASSIGNED_PATIENTS',
        'CREATE_PATIENTS',
        'UPDATE_PATIENTS',
        'DELETE_PATIENTS',
        'CREATE_TASKS',
        'VIEW_TASKS',
        'UPDATE_TASKS',
        'DELETE_TASKS',
        'COMPLETE_TASKS',
        'VIEW_HEALTH_DATA',
        'RECORD_HEALTH_DATA',
        'EXPORT_HEALTH_DATA',
        'VIEW_BILLING',
        'MANAGE_SUBSCRIPTIONS',
        'SEND_MESSAGES',
        'VIEW_MESSAGES',
        'VIEW_ANALYTICS',
        'EXPORT_REPORTS',
        'USE_AI_FEATURES',
        'MANAGE_DEVICES',
        'MANAGE_NOTIFICATIONS',
        'VIEW_AUDIT_LOGS',
      ];

      for (var permission in permissions) {
        expect(
          PermissionHelper.hasPermission('ADMIN', permission),
          true,
          reason: 'Admin should have $permission',
        );
      }

      expect(PermissionHelper.getPermissionCount('ADMIN'), 26);
    });

    test('Caregiver has exactly 19 permissions', () {
      final caregiverPermissions = [
        'VIEW_ASSIGNED_PATIENTS',
        'CREATE_PATIENTS',
        'UPDATE_PATIENTS',
        'CREATE_TASKS',
        'VIEW_TASKS',
        'UPDATE_TASKS',
        'DELETE_TASKS',
        'COMPLETE_TASKS',
        'VIEW_HEALTH_DATA',
        'RECORD_HEALTH_DATA',
        'EXPORT_HEALTH_DATA',
        'VIEW_BILLING',
        'MANAGE_SUBSCRIPTIONS',
        'SEND_MESSAGES',
        'VIEW_MESSAGES',
        'VIEW_ANALYTICS',
        'EXPORT_REPORTS',
        'USE_AI_FEATURES',
        'MANAGE_DEVICES',
      ];

      for (var permission in caregiverPermissions) {
        expect(
          PermissionHelper.hasPermission('CAREGIVER', permission),
          true,
          reason: 'Caregiver should have $permission',
        );
      }

      expect(PermissionHelper.getPermissionCount('CAREGIVER'), 19);
      expect(PermissionHelper.getPermissionCount('FAMILY_LINK'), 19);
    });

    test('Caregiver does NOT have admin-only permissions', () {
      expect(PermissionHelper.hasPermission('CAREGIVER', 'VIEW_ALL_USERS'), false);
      expect(PermissionHelper.hasPermission('CAREGIVER', 'MANAGE_USERS'), false);
      expect(PermissionHelper.hasPermission('CAREGIVER', 'ASSIGN_ROLES'), false);
      expect(PermissionHelper.hasPermission('CAREGIVER', 'VIEW_ALL_PATIENTS'), false);
      expect(PermissionHelper.hasPermission('CAREGIVER', 'DELETE_PATIENTS'), false);
      expect(PermissionHelper.hasPermission('CAREGIVER', 'VIEW_AUDIT_LOGS'), false);
    });

    test('Patient has exactly 6 permissions', () {
      final patientPermissions = [
        'VIEW_TASKS',
        'COMPLETE_TASKS',
        'VIEW_HEALTH_DATA',
        'RECORD_HEALTH_DATA',
        'SEND_MESSAGES',
        'VIEW_MESSAGES',
      ];

      for (var permission in patientPermissions) {
        expect(
          PermissionHelper.hasPermission('PATIENT', permission),
          true,
          reason: 'Patient should have $permission',
        );
      }

      expect(PermissionHelper.getPermissionCount('PATIENT'), 6);
    });

    test('Family Member has exactly 3 permissions', () {
      final familyPermissions = [
        'VIEW_TASKS',
        'VIEW_HEALTH_DATA',
        'VIEW_MESSAGES',
      ];

      for (var permission in familyPermissions) {
        expect(
          PermissionHelper.hasPermission('FAMILY_MEMBER', permission),
          true,
          reason: 'Family member should have $permission',
        );
      }

      expect(PermissionHelper.getPermissionCount('FAMILY_MEMBER'), 3);
    });

    test('Family Member cannot modify data', () {
      expect(PermissionHelper.hasPermission('FAMILY_MEMBER', 'CREATE_TASKS'), false);
      expect(PermissionHelper.hasPermission('FAMILY_MEMBER', 'DELETE_TASKS'), false);
      expect(PermissionHelper.hasPermission('FAMILY_MEMBER', 'RECORD_HEALTH_DATA'), false);
      expect(PermissionHelper.hasPermission('FAMILY_MEMBER', 'CREATE_PATIENTS'), false);
    });

    test('hasAnyPermission works correctly', () {
      expect(
        PermissionHelper.hasAnyPermission(
          'CAREGIVER',
          ['DELETE_PATIENTS', 'UPDATE_PATIENTS'],
        ),
        true,
      );

      expect(
        PermissionHelper.hasAnyPermission(
          'PATIENT',
          ['DELETE_TASKS', 'CREATE_TASKS'],
        ),
        false,
      );
    });

    test('hasAllPermissions works correctly', () {
      expect(
        PermissionHelper.hasAllPermissions(
          'CAREGIVER',
          ['VIEW_TASKS', 'CREATE_TASKS'],
        ),
        true,
      );

      expect(
        PermissionHelper.hasAllPermissions(
          'PATIENT',
          ['VIEW_TASKS', 'DELETE_TASKS'],
        ),
        false,
      );
    });

    test('Permission checks are case-insensitive', () {
      expect(PermissionHelper.hasPermission('caregiver', 'view_tasks'), true);
      expect(PermissionHelper.hasPermission('CAREGIVER', 'VIEW_TASKS'), true);
      expect(PermissionHelper.hasPermission('Caregiver', 'View_Tasks'), true);
    });
  });
}