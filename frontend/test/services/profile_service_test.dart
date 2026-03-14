// Tests for ProfileService.
//
// Coverage strategy:
//   ProfileService uses AuthTokenManager (FlutterSecureStorage MethodChannel)
//   and http.get (top-level, intercepted via http.runWithClient + MockClient).
//
//   Branches tested:
//     getCurrentUserProfile — no user session → null.
//     getCurrentUserProfile — PATIENT role with patientId → calls getPatientProfile.
//     getCurrentUserProfile — CAREGIVER role with caregiverId → calls getCaregiverProfile.
//     getCurrentUserProfile — role with no id → null.
//     getCurrentUserProfile — unknown role → null.
//     getProfileByUserIdAndRole — PATIENT → calls getPatientProfile path.
//     getProfileByUserIdAndRole — CAREGIVER → calls getCaregiverProfile path.
//     getProfileByUserIdAndRole — FAMILY_LINK → calls getCaregiverProfile path.
//     getProfileByUserIdAndRole — ADMIN → calls getCaregiverProfile path.
//     getProfileByUserIdAndRole — unknown role → null.
//     getPatientProfile — 200 → returns merged map with profileImageUrl.
//     getPatientProfile — non-200 → null.
//     getCaregiverProfile — 200 → returns merged map with profileImageUrl.
//     getCaregiverProfile — non-200 → null.

import 'dart:convert';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:care_connect_app/services/profile_service.dart';

// ─── Secure storage stub ──────────────────────────────────────────────────────

const MethodChannel _secureStorageChannel =
    MethodChannel('plugins.it_nomads.com/flutter_secure_storage');

final Map<String, String?> _secureStore = {};

void _setupSecureStorageStub() {
  TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
      .setMockMethodCallHandler(_secureStorageChannel, (call) async {
    switch (call.method) {
      case 'write':
        _secureStore[call.arguments['key'] as String] =
            call.arguments['value'] as String?;
        return null;
      case 'read':
        return _secureStore[call.arguments['key'] as String];
      case 'delete':
        _secureStore.remove(call.arguments['key'] as String);
        return null;
      case 'deleteAll':
        _secureStore.clear();
        return null;
      default:
        return null;
    }
  });
}

/// Seeds a user session JSON into secure storage so that
/// AuthTokenManager.getUserSession() returns it.
void _seedSession(Map<String, dynamic> session) {
  _secureStore['user_session'] = jsonEncode(session);
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    _secureStore.clear();
    SharedPreferences.setMockInitialValues({});
    _setupSecureStorageStub();
  });

  tearDownAll(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(_secureStorageChannel, null);
  });

  // ─── getCurrentUserProfile ────────────────────────────────────────────────

  group('ProfileService.getCurrentUserProfile', () {
    test('no user session → returns null', () async {
      // Secure store is empty, no user_session key.
      final result = await http.runWithClient(
        () => ProfileService.getCurrentUserProfile(),
        () => MockClient((_) async => http.Response('{}', 200)),
      );
      expect(result, isNull);
    });

    test('PATIENT role with patientId → returns profile map', () async {
      _seedSession({'role': 'PATIENT', 'patientId': 42});
      final patientData = {'id': 42, 'firstName': 'Alice'};
      // Two HTTP calls: getPatientProfile + getUserProfilePictureUrl.
      // We return valid JSON for the patient endpoint and empty for the profile image.
      int callCount = 0;
      final result = await http.runWithClient(
        () => ProfileService.getCurrentUserProfile(),
        () => MockClient((_) async {
          callCount++;
          if (callCount == 1) {
            return http.Response(jsonEncode(patientData), 200);
          }
          // Profile image endpoint — return non-200 so it gracefully falls back.
          return http.Response('', 404);
        }),
      );
      expect(result, isNotNull);
      expect(result?['firstName'], 'Alice');
    });

    test('PATIENT role with no patientId → returns null', () async {
      _seedSession({'role': 'PATIENT'});
      final result = await http.runWithClient(
        () => ProfileService.getCurrentUserProfile(),
        () => MockClient((_) async => http.Response('{}', 200)),
      );
      expect(result, isNull);
    });

    test('CAREGIVER role with caregiverId → returns profile map', () async {
      _seedSession({'role': 'CAREGIVER', 'caregiverId': 7});
      final caregiverData = {'id': 7, 'firstName': 'Bob'};
      int callCount = 0;
      final result = await http.runWithClient(
        () => ProfileService.getCurrentUserProfile(),
        () => MockClient((_) async {
          callCount++;
          if (callCount == 1) {
            return http.Response(jsonEncode(caregiverData), 200);
          }
          return http.Response('', 404);
        }),
      );
      expect(result, isNotNull);
      expect(result?['firstName'], 'Bob');
    });

    test('unknown role → returns null', () async {
      _seedSession({'role': 'UNKNOWN_ROLE'});
      final result = await http.runWithClient(
        () => ProfileService.getCurrentUserProfile(),
        () => MockClient((_) async => http.Response('{}', 200)),
      );
      expect(result, isNull);
    });
  });

  // ─── getProfileByUserIdAndRole ────────────────────────────────────────────

  group('ProfileService.getProfileByUserIdAndRole', () {
    test('PATIENT role → calls patient endpoint', () async {
      final patientData = {'id': 5, 'firstName': 'Carol'};
      int callCount = 0;
      final result = await http.runWithClient(
        () => ProfileService.getProfileByUserIdAndRole(5, 'PATIENT'),
        () => MockClient((_) async {
          callCount++;
          if (callCount == 1) return http.Response(jsonEncode(patientData), 200);
          return http.Response('', 404);
        }),
      );
      expect(result?['firstName'], 'Carol');
    });

    test('CAREGIVER role → calls caregiver endpoint', () async {
      final cgData = {'id': 9, 'firstName': 'Dave'};
      int callCount = 0;
      final result = await http.runWithClient(
        () => ProfileService.getProfileByUserIdAndRole(9, 'CAREGIVER'),
        () => MockClient((_) async {
          callCount++;
          if (callCount == 1) return http.Response(jsonEncode(cgData), 200);
          return http.Response('', 404);
        }),
      );
      expect(result?['firstName'], 'Dave');
    });

    test('FAMILY_LINK role → calls caregiver endpoint', () async {
      final data = {'id': 11, 'firstName': 'Eve'};
      int callCount = 0;
      final result = await http.runWithClient(
        () => ProfileService.getProfileByUserIdAndRole(11, 'FAMILY_LINK'),
        () => MockClient((_) async {
          callCount++;
          if (callCount == 1) return http.Response(jsonEncode(data), 200);
          return http.Response('', 404);
        }),
      );
      expect(result?['firstName'], 'Eve');
    });

    test('ADMIN role → calls caregiver endpoint', () async {
      final data = {'id': 1, 'firstName': 'Admin'};
      int callCount = 0;
      final result = await http.runWithClient(
        () => ProfileService.getProfileByUserIdAndRole(1, 'ADMIN'),
        () => MockClient((_) async {
          callCount++;
          if (callCount == 1) return http.Response(jsonEncode(data), 200);
          return http.Response('', 404);
        }),
      );
      expect(result?['firstName'], 'Admin');
    });

    test('unsupported role → returns null', () async {
      final result = await http.runWithClient(
        () => ProfileService.getProfileByUserIdAndRole(1, 'GHOST'),
        () => MockClient((_) async => http.Response('{}', 200)),
      );
      expect(result, isNull);
    });
  });

  // ─── getPatientProfile ────────────────────────────────────────────────────

  group('ProfileService.getPatientProfile', () {
    test('non-200 from patient endpoint → returns null', () async {
      final result = await http.runWithClient(
        () => ProfileService.getPatientProfile(99),
        () => MockClient((_) async => http.Response('', 404)),
      );
      expect(result, isNull);
    });

    test('200 → returns combined map with profileImageUrl key', () async {
      final patientData = {'id': 3, 'name': 'Frank'};
      int callCount = 0;
      final result = await http.runWithClient(
        () => ProfileService.getPatientProfile(3),
        () => MockClient((_) async {
          callCount++;
          if (callCount == 1) return http.Response(jsonEncode(patientData), 200);
          return http.Response('', 404);
        }),
      );
      expect(result, isNotNull);
      expect(result!.containsKey('profileImageUrl'), isTrue);
      expect(result.containsKey('profilePictureUrl'), isTrue);
    });
  });

  // ─── getCaregiverProfile ──────────────────────────────────────────────────

  group('ProfileService.getCaregiverProfile', () {
    test('non-200 from caregiver endpoint → returns null', () async {
      final result = await http.runWithClient(
        () => ProfileService.getCaregiverProfile(99),
        () => MockClient((_) async => http.Response('', 500)),
      );
      expect(result, isNull);
    });

    test('200 → returns combined map with profileImageUrl key', () async {
      final cgData = {'id': 8, 'name': 'Grace'};
      int callCount = 0;
      final result = await http.runWithClient(
        () => ProfileService.getCaregiverProfile(8),
        () => MockClient((_) async {
          callCount++;
          if (callCount == 1) return http.Response(jsonEncode(cgData), 200);
          return http.Response('', 404);
        }),
      );
      expect(result, isNotNull);
      expect(result!.containsKey('profileImageUrl'), isTrue);
    });
  });
}
