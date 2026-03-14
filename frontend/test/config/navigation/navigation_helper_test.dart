// Tests for NavigationHelper
// (lib/config/navigation/navigation_helper.dart).
//
// Coverage strategy:
//   getTabIndexFromName — pure static, no platform channel dependency.
//   getMainScreenConfig — reads from UserRoleStorageService (SharedPreferences
//                         mock), testable for all role branches.
//   isAuthenticated     — delegates to UserRoleStorageService.isLoggedIn(),
//                         testable via SharedPreferences mock.
//
//   Branches tested (getTabIndexFromName, PATIENT role):
//     'home'     → 0
//     'health'   → 1
//     'messages' → 2
//     'profile'  → 3
//     unknown    → null
//
//   Branches tested (getTabIndexFromName, non-PATIENT role, e.g. CAREGIVER):
//     'patients' → 0
//     'tasks'    → 1
//     'analytics'→ 2
//     'messages' → 3
//     'profile'  → 4
//     unknown    → null
//
//   Additional branches (getTabIndexFromName):
//     role comparison is case-insensitive ('patient' == 'PATIENT').
//     tabName comparison is case-insensitive ('HOME' == 'home').
//
//   Branches tested (getMainScreenConfig):
//     not logged in  → null
//     PATIENT role   → MainScreenConfig.forPatient
//     CAREGIVER role → MainScreenConfig.forCaregiver
//     FAMILY_LINK    → MainScreenConfig.forFamilyMember
//     ADMIN          → MainScreenConfig with userRole='ADMIN'
//     unknown role   → null
//
//   Branches tested (isAuthenticated):
//     not logged in → false
//     logged in     → true

import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:care_connect_app/config/navigation/navigation_helper.dart';
import 'package:care_connect_app/services/user_role_storage_service.dart';

void main() {
  // ─── PATIENT role ──────────────────────────────────────────────────────────

  group('getTabIndexFromName – PATIENT role', () {
    test('"home" returns 0', () {
      // Verifies the first patient tab maps to index 0.
      expect(NavigationHelper.getTabIndexFromName('PATIENT', 'home'), 0);
    });

    test('"health" returns 1', () {
      // Verifies the health/check-in tab maps to index 1.
      expect(NavigationHelper.getTabIndexFromName('PATIENT', 'health'), 1);
    });

    test('"messages" returns 2', () {
      // Verifies the messages tab maps to index 2 for patients.
      expect(NavigationHelper.getTabIndexFromName('PATIENT', 'messages'), 2);
    });

    test('"profile" returns 3', () {
      // Verifies the profile tab maps to index 3 for patients.
      expect(NavigationHelper.getTabIndexFromName('PATIENT', 'profile'), 3);
    });

    test('unknown tab returns null', () {
      // Verifies that an unrecognised tab name returns null (no match).
      expect(
        NavigationHelper.getTabIndexFromName('PATIENT', 'dashboard'),
        isNull,
      );
    });

    test('role matching is case-insensitive', () {
      // Verifies that lowercase 'patient' is treated the same as 'PATIENT'.
      expect(NavigationHelper.getTabIndexFromName('patient', 'home'), 0);
    });

    test('tabName matching is case-insensitive', () {
      // Verifies that uppercase tab names are normalised to lowercase.
      expect(NavigationHelper.getTabIndexFromName('PATIENT', 'HOME'), 0);
      expect(NavigationHelper.getTabIndexFromName('PATIENT', 'HEALTH'), 1);
    });
  });

  // ─── Non-PATIENT roles ─────────────────────────────────────────────────────

  group('getTabIndexFromName – CAREGIVER (non-patient) role', () {
    test('"patients" returns 0', () {
      // Verifies the patient-list tab maps to index 0 for caregivers.
      expect(
        NavigationHelper.getTabIndexFromName('CAREGIVER', 'patients'),
        0,
      );
    });

    test('"tasks" returns 1', () {
      // Verifies the tasks tab maps to index 1.
      expect(NavigationHelper.getTabIndexFromName('CAREGIVER', 'tasks'), 1);
    });

    test('"analytics" returns 2', () {
      // Verifies the analytics tab maps to index 2.
      expect(
        NavigationHelper.getTabIndexFromName('CAREGIVER', 'analytics'),
        2,
      );
    });

    test('"messages" returns 3', () {
      // Verifies the messages tab maps to index 3 for non-patients.
      expect(
        NavigationHelper.getTabIndexFromName('CAREGIVER', 'messages'),
        3,
      );
    });

    test('"profile" returns 4', () {
      // Verifies the profile tab maps to index 4 for caregivers.
      expect(
        NavigationHelper.getTabIndexFromName('CAREGIVER', 'profile'),
        4,
      );
    });

    test('unknown tab returns null', () {
      // Verifies that an unrecognised tab name returns null.
      expect(
        NavigationHelper.getTabIndexFromName('CAREGIVER', 'home'),
        isNull,
      );
    });

    test('role matching is case-insensitive for non-patient', () {
      // Verifies that 'caregiver' is treated the same as 'CAREGIVER'.
      expect(
        NavigationHelper.getTabIndexFromName('caregiver', 'analytics'),
        2,
      );
    });

    test('FAMILY_LINK uses the non-patient branch', () {
      // Verifies FAMILY_LINK falls into the same branch as CAREGIVER.
      expect(
        NavigationHelper.getTabIndexFromName('FAMILY_LINK', 'tasks'),
        1,
      );
    });

    test('ADMIN uses the non-patient branch', () {
      // Verifies ADMIN falls into the same branch as CAREGIVER.
      expect(
        NavigationHelper.getTabIndexFromName('ADMIN', 'profile'),
        4,
      );
    });
  });

  // ─── getMainScreenConfig ───────────────────────────────────────────────────

  group('getMainScreenConfig', () {
    // After the first call, UserRoleStorageService caches its SharedPreferences
    // instance.  Subsequent tests update the stored values through the service
    // itself (setUserData / clearUserData) so the cache stays consistent.

    test('returns null when user is not logged in', () async {
      // Verifies the not-logged-in guard returns null without building a config.
      SharedPreferences.setMockInitialValues({});
      await UserRoleStorageService.instance.clearUserData();

      final config = await NavigationHelper.getMainScreenConfig();
      expect(config, isNull);
    });

    test('returns PATIENT config for PATIENT role', () async {
      // Verifies the PATIENT switch branch constructs forPatient.
      await UserRoleStorageService.instance.setUserData(
        role: 'PATIENT',
        userId: 5,
        patientId: 10,
      );

      final config = await NavigationHelper.getMainScreenConfig();
      expect(config, isNotNull);
      expect(config!.userRole, 'PATIENT');
    });

    test('returns CAREGIVER config for CAREGIVER role', () async {
      // Verifies the CAREGIVER switch branch constructs forCaregiver.
      await UserRoleStorageService.instance.setUserData(
        role: 'CAREGIVER',
        userId: 10,
        caregiverId: 3,
      );

      final config = await NavigationHelper.getMainScreenConfig();
      expect(config, isNotNull);
      expect(config!.userRole, 'CAREGIVER');
    });

    test('returns FAMILY_LINK config for FAMILY_LINK role', () async {
      // Verifies the FAMILY_LINK switch branch constructs forFamilyMember.
      await UserRoleStorageService.instance.setUserData(
        role: 'FAMILY_LINK',
        userId: 15,
        patientId: 20,
      );

      final config = await NavigationHelper.getMainScreenConfig();
      expect(config, isNotNull);
      expect(config!.userRole, 'FAMILY_LINK');
    });

    test('returns ADMIN config for ADMIN role', () async {
      // Verifies the ADMIN switch branch constructs a generic MainScreenConfig.
      await UserRoleStorageService.instance.setUserData(
        role: 'ADMIN',
        userId: 20,
      );

      final config = await NavigationHelper.getMainScreenConfig();
      expect(config, isNotNull);
      expect(config!.userRole, 'ADMIN');
    });

    test('returns null for unknown role', () async {
      // Verifies the default switch branch returns null.
      await UserRoleStorageService.instance.setUserData(
        role: 'UNKNOWN_ROLE',
        userId: 25,
      );

      final config = await NavigationHelper.getMainScreenConfig();
      expect(config, isNull);
    });
  });

  // ─── isAuthenticated ───────────────────────────────────────────────────────

  group('isAuthenticated', () {
    test('returns false when user is not logged in', () async {
      // Verifies delegation to UserRoleStorageService.isLoggedIn() returns false.
      await UserRoleStorageService.instance.clearUserData();

      final result = await NavigationHelper.isAuthenticated();
      expect(result, isFalse);
    });

    test('returns true when user is logged in', () async {
      // Verifies delegation to UserRoleStorageService.isLoggedIn() returns true.
      await UserRoleStorageService.instance.setUserData(
        role: 'PATIENT',
        userId: 5,
      );

      final result = await NavigationHelper.isAuthenticated();
      expect(result, isTrue);
    });
  });
}
