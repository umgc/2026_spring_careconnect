// Tests for AuthResult and AuthErrorType from enhanced_auth_service.dart.
//
// Coverage strategy:
//   EnhancedAuthService.loginWithRoleValidation and loginWithGoogleAndRoleValidation
//   both call into AuthService which makes real HTTP calls and cannot be mocked
//   without heavy platform setup.  Those methods are excluded.
//
//   AuthResult is a pure data class with three factory constructors that are
//   fully exercised here.
//
//   Branches tested:
//     AuthResult.success() — isSuccess true, userSession set, no error fields.
//     AuthResult.authenticationFailure() — isSuccess false, errorType authentication.
//     AuthResult.roleValidationFailure() — isSuccess false, errorType roleValidation,
//       all fields set.
//     AuthErrorType enum — both values accessible.

import 'package:flutter_test/flutter_test.dart';

import 'package:care_connect_app/providers/user_provider.dart';
import 'package:care_connect_app/services/enhanced_auth_service.dart';

UserSession _fakeSession() => UserSession(
      id: 1,
      email: 'user@example.com',
      role: 'PATIENT',
      token: 'tok',
    );

void main() {
  // ─── AuthResult.success ──────────────────────────────────────────────────

  group('AuthResult.success', () {
    test('isSuccess is true', () {
      final r = AuthResult.success(userSession: _fakeSession());
      expect(r.isSuccess, isTrue);
    });

    test('userSession is the provided session', () {
      final session = _fakeSession();
      final r = AuthResult.success(userSession: session);
      expect(r.userSession, same(session));
    });

    test('error fields are null', () {
      final r = AuthResult.success(userSession: _fakeSession());
      expect(r.errorMessage, isNull);
      expect(r.errorType, isNull);
      expect(r.actualRole, isNull);
      expect(r.expectedRole, isNull);
      expect(r.correctLoginRoute, isNull);
    });
  });

  // ─── AuthResult.authenticationFailure ───────────────────────────────────

  group('AuthResult.authenticationFailure', () {
    test('isSuccess is false', () {
      final r = AuthResult.authenticationFailure(message: 'Bad credentials');
      expect(r.isSuccess, isFalse);
    });

    test('errorMessage matches the provided message', () {
      final r = AuthResult.authenticationFailure(message: 'Bad credentials');
      expect(r.errorMessage, 'Bad credentials');
    });

    test('errorType is AuthErrorType.authentication', () {
      final r = AuthResult.authenticationFailure(message: 'x');
      expect(r.errorType, AuthErrorType.authentication);
    });

    test('userSession is null', () {
      final r = AuthResult.authenticationFailure(message: 'x');
      expect(r.userSession, isNull);
    });
  });

  // ─── AuthResult.roleValidationFailure ───────────────────────────────────

  group('AuthResult.roleValidationFailure', () {
    AuthResult makeFailure() => AuthResult.roleValidationFailure(
          message: 'Wrong portal',
          actualRole: 'PATIENT',
          expectedRole: 'CAREGIVER',
          correctLoginRoute: '/login/patient',
        );

    test('isSuccess is false', () {
      expect(makeFailure().isSuccess, isFalse);
    });

    test('errorMessage is set', () {
      expect(makeFailure().errorMessage, 'Wrong portal');
    });

    test('errorType is AuthErrorType.roleValidation', () {
      expect(makeFailure().errorType, AuthErrorType.roleValidation);
    });

    test('actualRole is set', () {
      expect(makeFailure().actualRole, 'PATIENT');
    });

    test('expectedRole is set', () {
      expect(makeFailure().expectedRole, 'CAREGIVER');
    });

    test('correctLoginRoute is set', () {
      expect(makeFailure().correctLoginRoute, '/login/patient');
    });

    test('userSession is null', () {
      expect(makeFailure().userSession, isNull);
    });
  });

  // ─── AuthErrorType enum ──────────────────────────────────────────────────

  group('AuthErrorType', () {
    test('authentication value exists', () {
      expect(AuthErrorType.authentication, isNotNull);
    });

    test('roleValidation value exists', () {
      expect(AuthErrorType.roleValidation, isNotNull);
    });

    test('both values are distinct', () {
      expect(AuthErrorType.authentication, isNot(AuthErrorType.roleValidation));
    });
  });
}
