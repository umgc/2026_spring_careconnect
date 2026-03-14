import 'package:flutter_test/flutter_test.dart';
import 'package:care_connect_app/utils/role_helper.dart';

void main() {
  group('RoleHelper Tests', () {
    test('isAdmin identifies admin role correctly', () {
      expect(RoleHelper.isAdmin('ADMIN'), true);
      expect(RoleHelper.isAdmin('admin'), true);
      expect(RoleHelper.isAdmin('CAREGIVER'), false);
      expect(RoleHelper.isAdmin('PATIENT'), false);
    });

    test('isCaregiver identifies caregiver and family_link', () {
      expect(RoleHelper.isCaregiver('CAREGIVER'), true);
      expect(RoleHelper.isCaregiver('caregiver'), true);
      expect(RoleHelper.isCaregiver('FAMILY_LINK'), true);
      expect(RoleHelper.isCaregiver('family_link'), true);
      expect(RoleHelper.isCaregiver('ADMIN'), false);
      expect(RoleHelper.isCaregiver('PATIENT'), false);
    });

    test('isPatient identifies patient role correctly', () {
      expect(RoleHelper.isPatient('PATIENT'), true);
      expect(RoleHelper.isPatient('patient'), true);
      expect(RoleHelper.isPatient('ADMIN'), false);
      expect(RoleHelper.isPatient('CAREGIVER'), false);
    });

    test('isFamilyMember identifies family member role', () {
      expect(RoleHelper.isFamilyMember('FAMILY_MEMBER'), true);
      expect(RoleHelper.isFamilyMember('family_member'), true);
      expect(RoleHelper.isFamilyMember('ADMIN'), false);
      expect(RoleHelper.isFamilyMember('PATIENT'), false);
    });

    test('isCaregiverOrAdmin identifies both roles', () {
      expect(RoleHelper.isCaregiverOrAdmin('ADMIN'), true);
      expect(RoleHelper.isCaregiverOrAdmin('CAREGIVER'), true);
      expect(RoleHelper.isCaregiverOrAdmin('FAMILY_LINK'), true);
      expect(RoleHelper.isCaregiverOrAdmin('PATIENT'), false);
      expect(RoleHelper.isCaregiverOrAdmin('FAMILY_MEMBER'), false);
    });

    test('canModifyData excludes only family members', () {
      expect(RoleHelper.canModifyData('ADMIN'), true);
      expect(RoleHelper.canModifyData('CAREGIVER'), true);
      expect(RoleHelper.canModifyData('PATIENT'), true);
      expect(RoleHelper.canModifyData('FAMILY_MEMBER'), false);
    });

    test('canManagePatients allows admin and caregiver', () {
      expect(RoleHelper.canManagePatients('ADMIN'), true);
      expect(RoleHelper.canManagePatients('CAREGIVER'), true);
      expect(RoleHelper.canManagePatients('FAMILY_LINK'), true);
      expect(RoleHelper.canManagePatients('PATIENT'), false);
      expect(RoleHelper.canManagePatients('FAMILY_MEMBER'), false);
    });

    test('getRoleDisplayName returns correct display names', () {
      expect(RoleHelper.getRoleDisplayName('ADMIN'), 'Administrator');
      expect(RoleHelper.getRoleDisplayName('CAREGIVER'), 'Caregiver');
      expect(RoleHelper.getRoleDisplayName('FAMILY_LINK'), 
             'Family Link Caregiver');
      expect(RoleHelper.getRoleDisplayName('PATIENT'), 'Patient');
      expect(RoleHelper.getRoleDisplayName('FAMILY_MEMBER'), 'Family Member');
    });

    test('getRoleColorValue returns valid color integers', () {
      final adminColor = RoleHelper.getRoleColorValue('ADMIN');
      final caregiverColor = RoleHelper.getRoleColorValue('CAREGIVER');
      final patientColor = RoleHelper.getRoleColorValue('PATIENT');
      final familyColor = RoleHelper.getRoleColorValue('FAMILY_MEMBER');

      expect(adminColor, 0xFFD32F2F);
      expect(caregiverColor, 0xFF1976D2);
      expect(patientColor, 0xFF388E3C);
      expect(familyColor, 0xFF7B1FA2);
    });

    test('getLoginRoute returns correct routes', () {
      expect(RoleHelper.getLoginRoute('ADMIN'), '/login/caregiver');
      expect(RoleHelper.getLoginRoute('CAREGIVER'), '/login/caregiver');
      expect(RoleHelper.getLoginRoute('FAMILY_LINK'), '/login/caregiver');
      expect(RoleHelper.getLoginRoute('PATIENT'), '/login/patient');
      expect(RoleHelper.getLoginRoute('FAMILY_MEMBER'), '/login/patient');
    });
  });
}