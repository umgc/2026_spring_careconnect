import 'package:care_connect_app/providers/user_provider.dart';

/// Shared fixture builders for frontend unit/widget tests.
///
/// This module keeps test input deterministic so tests can validate widget and
/// session behavior without live backend calls or environment setup.

/// Returns a baseline patient session used by most authenticated UI tests.
///
/// Use this when testing patient-facing widgets that depend on a logged-in user.
UserSession fakePatientUser({String? name, bool includeName = true}) {
  return UserSession(
    id: 1,
    email: 'patient@example.com',
    role: 'PATIENT',
    token: 'mock_patient_token',
    patientId: 1,
    caregiverId: null,
    name: includeName ? (name ?? 'Test Patient') : null,
    emailVerified: true,
  );
}

/// Returns a baseline caregiver session used for role-specific UI tests.
///
/// Use this when testing role checks or caregiver-only widget behavior.
UserSession fakeCaregiverUser({String? name, bool includeName = true}) {
  return UserSession(
    id: 2,
    email: 'caregiver@example.com',
    role: 'CAREGIVER',
    token: 'mock_caregiver_token',
    patientId: null,
    caregiverId: 2,
    name: includeName ? (name ?? 'Test Caregiver') : null,
    emailVerified: true,
  );
}

/// Returns a canonical auth payload shape used by parsing/serialization tests.
///
/// Use this fixture to avoid repeating auth JSON literals across test files.
Map<String, dynamic> fakeAuthJson() {
  return {
    'id': 1,
    'email': 'patient@example.com',
    'role': 'PATIENT',
    'token': 'mock_patient_token',
    'patientId': 1,
    'caregiverId': null,
    'name': 'Test Patient',
    'emailVerified': true,
  };
}

/// Returns a minimal patient payload often used in dashboard name fallbacks.
///
/// Use this for widget tests that stitch together patient full names.
Map<String, dynamic> fakePatientJson() {
  return {'firstName': 'Jane', 'lastName': 'Smith'};
}
