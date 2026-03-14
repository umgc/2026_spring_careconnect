// Tests for RoleValidator and RoleValidationResult.
//
// Coverage strategy:
//   RoleValidator is pure Dart — no platform channels, no HTTP, no state.
//   All branches are exercised via direct calls with different role combinations.
//
//   Branches tested:
//     RoleValidationResult.success() — isValid true, error fields null.
//     RoleValidationResult.failure() — isValid false, all error fields set.
//     validateUserRole — CAREGIVER expected with all valid caregiver aliases.
//     validateUserRole — CAREGIVER expected with patient / family-member / unknown.
//     validateUserRole — PATIENT expected with all valid patient aliases.
//     validateUserRole — PATIENT expected with caregiver / family-link / admin / unknown.
//     validateUserRole — unknown expected role with exact match and mismatch.
//     validateUserRole — case-insensitive normalisation.
//     getCorrectLoginRoute — all role families and unknown.
//     getRoleDisplayName  — all known roles and unknown fall-through.

import 'package:flutter_test/flutter_test.dart';

import 'package:care_connect_app/providers/user_provider.dart';
import 'package:care_connect_app/services/role_validator.dart';

// Helper that creates a minimal UserSession with just the role set.
UserSession _session(String role) => UserSession(
      id: 1,
      email: 'test@example.com',
      role: role,
      token: 'tok',
    );

void main() {
  // ─── RoleValidationResult ────────────────────────────────────────────────

  group('RoleValidationResult', () {
    test('success() → isValid true and all error fields null', () {
      final r = RoleValidationResult.success();
      expect(r.isValid, isTrue);
      expect(r.actualRole, isNull);
      expect(r.expectedRole, isNull);
      expect(r.errorMessage, isNull);
    });

    test('failure() → isValid false with all provided fields set', () {
      final r = RoleValidationResult.failure(
        actualRole: 'PATIENT',
        expectedRole: 'CAREGIVER',
        message: 'Role mismatch.',
      );
      expect(r.isValid, isFalse);
      expect(r.actualRole, 'PATIENT');
      expect(r.expectedRole, 'CAREGIVER');
      expect(r.errorMessage, 'Role mismatch.');
    });
  });

  // ─── validateUserRole — CAREGIVER expected ──────────────────────────────

  group('validateUserRole — CAREGIVER expected', () {
    test('CAREGIVER actual → success', () {
      expect(
        RoleValidator.validateUserRole(
          expectedRole: 'CAREGIVER',
          userSession: _session('CAREGIVER'),
        ).isValid,
        isTrue,
      );
    });

    test('FAMILY_LINK actual → success (caregiver alias)', () {
      expect(
        RoleValidator.validateUserRole(
          expectedRole: 'CAREGIVER',
          userSession: _session('FAMILY_LINK'),
        ).isValid,
        isTrue,
      );
    });

    test('ADMIN actual → success (caregiver alias)', () {
      expect(
        RoleValidator.validateUserRole(
          expectedRole: 'CAREGIVER',
          userSession: _session('ADMIN'),
        ).isValid,
        isTrue,
      );
    });

    test('PATIENT actual → failure with Patient-specific message', () {
      final r = RoleValidator.validateUserRole(
        expectedRole: 'CAREGIVER',
        userSession: _session('PATIENT'),
      );
      expect(r.isValid, isFalse);
      expect(r.errorMessage, contains('Patient'));
    });

    test('FAMILY_MEMBER actual → failure with Family Member message', () {
      final r = RoleValidator.validateUserRole(
        expectedRole: 'CAREGIVER',
        userSession: _session('FAMILY_MEMBER'),
      );
      expect(r.isValid, isFalse);
      expect(r.errorMessage, contains('Family Member'));
    });

    test('UNKNOWN actual → generic caregiver failure message', () {
      final r = RoleValidator.validateUserRole(
        expectedRole: 'CAREGIVER',
        userSession: _session('UNKNOWN'),
      );
      expect(r.isValid, isFalse);
      expect(r.errorMessage, contains('appropriate login page'));
    });
  });

  // ─── validateUserRole — PATIENT expected ────────────────────────────────

  group('validateUserRole — PATIENT expected', () {
    test('PATIENT actual → success', () {
      expect(
        RoleValidator.validateUserRole(
          expectedRole: 'PATIENT',
          userSession: _session('PATIENT'),
        ).isValid,
        isTrue,
      );
    });

    test('FAMILY_MEMBER actual → success (patient alias)', () {
      expect(
        RoleValidator.validateUserRole(
          expectedRole: 'PATIENT',
          userSession: _session('FAMILY_MEMBER'),
        ).isValid,
        isTrue,
      );
    });

    test('CAREGIVER actual → failure with Caregiver message', () {
      final r = RoleValidator.validateUserRole(
        expectedRole: 'PATIENT',
        userSession: _session('CAREGIVER'),
      );
      expect(r.isValid, isFalse);
      expect(r.errorMessage, contains('Caregiver'));
    });

    test('FAMILY_LINK actual → failure with Family Link message', () {
      final r = RoleValidator.validateUserRole(
        expectedRole: 'PATIENT',
        userSession: _session('FAMILY_LINK'),
      );
      expect(r.isValid, isFalse);
      expect(r.errorMessage, contains('Family Link'));
    });

    test('ADMIN actual → failure with Admin message', () {
      final r = RoleValidator.validateUserRole(
        expectedRole: 'PATIENT',
        userSession: _session('ADMIN'),
      );
      expect(r.isValid, isFalse);
      expect(r.errorMessage, contains('Admin'));
    });

    test('OTHER actual → generic patient failure message', () {
      final r = RoleValidator.validateUserRole(
        expectedRole: 'PATIENT',
        userSession: _session('OTHER'),
      );
      expect(r.isValid, isFalse);
      expect(r.errorMessage, contains('appropriate login page'));
    });
  });

  // ─── validateUserRole — unknown expected role ────────────────────────────

  group('validateUserRole — unknown expected role', () {
    test('exact match → success', () {
      expect(
        RoleValidator.validateUserRole(
          expectedRole: 'NURSE',
          userSession: _session('NURSE'),
        ).isValid,
        isTrue,
      );
    });

    test('mismatch → failure with generic message', () {
      final r = RoleValidator.validateUserRole(
        expectedRole: 'NURSE',
        userSession: _session('DOCTOR'),
      );
      expect(r.isValid, isFalse);
      expect(r.errorMessage, contains('correct login page'));
    });

    test('case-insensitive normalisation: caregiver == CAREGIVER', () {
      expect(
        RoleValidator.validateUserRole(
          expectedRole: 'caregiver',
          userSession: _session('caregiver'),
        ).isValid,
        isTrue,
      );
    });
  });

  // ─── getCorrectLoginRoute ────────────────────────────────────────────────

  group('RoleValidator.getCorrectLoginRoute', () {
    test('CAREGIVER → /login/caregiver', () {
      expect(RoleValidator.getCorrectLoginRoute('CAREGIVER'), '/login/caregiver');
    });

    test('FAMILY_LINK → /login/caregiver', () {
      expect(RoleValidator.getCorrectLoginRoute('FAMILY_LINK'), '/login/caregiver');
    });

    test('ADMIN → /login/caregiver', () {
      expect(RoleValidator.getCorrectLoginRoute('ADMIN'), '/login/caregiver');
    });

    test('PATIENT → /login/patient', () {
      expect(RoleValidator.getCorrectLoginRoute('PATIENT'), '/login/patient');
    });

    test('FAMILY_MEMBER → /login/patient', () {
      expect(RoleValidator.getCorrectLoginRoute('FAMILY_MEMBER'), '/login/patient');
    });

    test('unknown role defaults to /login/patient', () {
      expect(RoleValidator.getCorrectLoginRoute('UNKNOWN'), '/login/patient');
    });

    test('case-insensitive: caregiver → /login/caregiver', () {
      expect(RoleValidator.getCorrectLoginRoute('caregiver'), '/login/caregiver');
    });
  });

  // ─── getRoleDisplayName ──────────────────────────────────────────────────

  group('RoleValidator.getRoleDisplayName', () {
    test('CAREGIVER → Caregiver', () {
      expect(RoleValidator.getRoleDisplayName('CAREGIVER'), 'Caregiver');
    });

    test('FAMILY_LINK → Family Link Caregiver', () {
      expect(RoleValidator.getRoleDisplayName('FAMILY_LINK'), 'Family Link Caregiver');
    });

    test('ADMIN → Administrator', () {
      expect(RoleValidator.getRoleDisplayName('ADMIN'), 'Administrator');
    });

    test('PATIENT → Patient', () {
      expect(RoleValidator.getRoleDisplayName('PATIENT'), 'Patient');
    });

    test('FAMILY_MEMBER → Family Member', () {
      expect(RoleValidator.getRoleDisplayName('FAMILY_MEMBER'), 'Family Member');
    });

    test('unknown role returns the raw role string', () {
      expect(RoleValidator.getRoleDisplayName('NURSE'), 'NURSE');
    });

    test('case-insensitive: caregiver → Caregiver', () {
      expect(RoleValidator.getRoleDisplayName('caregiver'), 'Caregiver');
    });
  });
}
