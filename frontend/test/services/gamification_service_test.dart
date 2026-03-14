// Tests for GamificationService.
//
// Coverage strategy:
//   GamificationService has four static methods, each making a single HTTP
//   request after fetching auth headers from AuthTokenManager:
//     • fetchXPProgress(userId)    — GET /api/gamification/progress/{userId}
//     • fetchAchievements(userId)  — GET /api/gamification/achievements/{userId}
//     • fetchAllAchievements(userId) — GET /api/gamification/all-achievements
//     • addXP(userId, amount)      — POST /api/gamification/award-xp
//
//   AuthTokenManager reads from flutter_secure_storage; the channel is stubbed
//   to return null, producing headers with no Authorization entry.
//
//   HTTP is mocked via http.runWithClient() + MockClient so no real network
//   calls are made.  Every branch (success, 401, other error) is tested, the
//   request shape is verified, and exception messages are asserted.

import 'dart:convert';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

import 'package:care_connect_app/services/gamification_service.dart';

// The flutter_secure_storage plugin registers under this channel.  Stubbing it
// prevents MissingPluginException when AuthTokenManager.getAuthHeaders() runs.
const MethodChannel _secureStorageChannel =
    MethodChannel('plugins.it_nomads.com/flutter_secure_storage');

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    // Intercept secure-storage channel calls; returning null simulates an
    // empty store so getJwtToken() → null → no Authorization header.
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(_secureStorageChannel, (_) async => null);
  });

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(_secureStorageChannel, null);
  });

  // ─── fetchXPProgress ──────────────────────────────────────────────────────

  group('GamificationService.fetchXPProgress()', () {
    test('returns a map on HTTP 200 with a non-empty body', () async {
      // Verifies the happy-path: the JSON body is decoded and returned as a
      // Map<String, dynamic> with the expected values.
      final result = await http.runWithClient(
        () => GamificationService.fetchXPProgress(1),
        () => MockClient((_) async =>
            http.Response(jsonEncode({'xp': 100, 'level': 3}), 200)),
      );
      expect(result, isA<Map<String, dynamic>>());
      expect(result['xp'], 100);
      expect(result['level'], 3);
    });

    test('throws "Not authorized" exception on HTTP 401', () async {
      // An unauthenticated response must surface as a specific error so
      // the UI can prompt re-login.
      await expectLater(
        http.runWithClient(
          () => GamificationService.fetchXPProgress(1),
          () => MockClient((_) async => http.Response('Unauthorized', 401)),
        ),
        throwsA(isA<Exception>().having(
          (e) => e.toString(),
          'message',
          contains('Not authorized'),
        )),
      );
    });

    test('throws "Failed to load XP Progress" on other error status', () async {
      // Any status other than 200 or 401 triggers a generic failure exception.
      await expectLater(
        http.runWithClient(
          () => GamificationService.fetchXPProgress(1),
          () => MockClient((_) async => http.Response('{}', 500)),
        ),
        throwsA(isA<Exception>().having(
          (e) => e.toString(),
          'message',
          contains('Failed to load XP Progress'),
        )),
      );
    });

    test('sends GET request to the progress/{userId} endpoint', () async {
      // Verifies the HTTP method and that the userId is embedded in the path.
      http.Request? captured;
      await http.runWithClient(
        () => GamificationService.fetchXPProgress(42),
        () => MockClient((req) async {
          captured = req;
          return http.Response(jsonEncode({'xp': 0}), 200);
        }),
      );
      expect(captured, isNotNull);
      expect(captured!.method, 'GET');
      expect(captured!.url.path, contains('progress'));
      expect(captured!.url.path, contains('42'));
    });
  });

  // ─── fetchAchievements ────────────────────────────────────────────────────

  group('GamificationService.fetchAchievements()', () {
    test('returns a list on HTTP 200', () async {
      // Verifies the happy-path: the JSON array is decoded and returned.
      final result = await http.runWithClient(
        () => GamificationService.fetchAchievements(1),
        () => MockClient((_) async =>
            http.Response(jsonEncode([{'id': 1, 'name': 'Beginner'}]), 200)),
      );
      expect(result, isA<List<dynamic>>());
      expect(result, hasLength(1));
    });

    test('returns an empty list on HTTP 200 with empty array', () async {
      // Verifies that an empty JSON array is handled correctly.
      final result = await http.runWithClient(
        () => GamificationService.fetchAchievements(1),
        () => MockClient((_) async => http.Response('[]', 200)),
      );
      expect(result, isEmpty);
    });

    test('throws "Not authorized" on HTTP 401', () async {
      // An unauthenticated response requires the user to log in again.
      await expectLater(
        http.runWithClient(
          () => GamificationService.fetchAchievements(1),
          () => MockClient((_) async => http.Response('Unauthorized', 401)),
        ),
        throwsA(isA<Exception>().having(
          (e) => e.toString(),
          'message',
          contains('Not authorized'),
        )),
      );
    });

    test('throws the "error" field from JSON body on other error status',
        () async {
      // The backend returns an {"error": "..."} payload on failure; this
      // message must be used as the exception description.
      await expectLater(
        http.runWithClient(
          () => GamificationService.fetchAchievements(1),
          () => MockClient((_) async =>
              http.Response(jsonEncode({'error': 'service unavailable'}), 503)),
        ),
        throwsA(isA<Exception>().having(
          (e) => e.toString(),
          'message',
          contains('service unavailable'),
        )),
      );
    });

    test('throws default message when error key is absent in JSON body',
        () async {
      // When the JSON has no "error" key, the fallback message must be used.
      await expectLater(
        http.runWithClient(
          () => GamificationService.fetchAchievements(1),
          () => MockClient((_) async =>
              http.Response(jsonEncode({'other': 'value'}), 503)),
        ),
        throwsA(isA<Exception>().having(
          (e) => e.toString(),
          'message',
          contains('Failed to load achievements'),
        )),
      );
    });

    test('sends GET request to the achievements/{userId} endpoint', () async {
      // Verifies the HTTP method and that userId appears in the path.
      http.Request? captured;
      await http.runWithClient(
        () => GamificationService.fetchAchievements(7),
        () => MockClient((req) async {
          captured = req;
          return http.Response('[]', 200);
        }),
      );
      expect(captured, isNotNull);
      expect(captured!.method, 'GET');
      expect(captured!.url.path, contains('achievements'));
      expect(captured!.url.path, contains('7'));
    });
  });

  // ─── fetchAllAchievements ─────────────────────────────────────────────────

  group('GamificationService.fetchAllAchievements()', () {
    test('returns a list on HTTP 200', () async {
      // Verifies the happy-path for the global achievements endpoint that
      // returns achievements regardless of which user earned them.
      final result = await http.runWithClient(
        () => GamificationService.fetchAllAchievements(1),
        () => MockClient((_) async =>
            http.Response(jsonEncode([{'id': 1}, {'id': 2}]), 200)),
      );
      expect(result, hasLength(2));
    });

    test('throws "Not authorized" on HTTP 401', () async {
      // Verifies the authentication failure path for this endpoint.
      await expectLater(
        http.runWithClient(
          () => GamificationService.fetchAllAchievements(1),
          () => MockClient((_) async => http.Response('Unauthorized', 401)),
        ),
        throwsA(isA<Exception>().having(
          (e) => e.toString(),
          'message',
          contains('Not authorized'),
        )),
      );
    });

    test('throws the "error" field from JSON body on other error status',
        () async {
      // The backend supplies an error description; it must propagate as the
      // exception message.
      await expectLater(
        http.runWithClient(
          () => GamificationService.fetchAllAchievements(1),
          () => MockClient((_) async =>
              http.Response(jsonEncode({'error': 'db error'}), 500)),
        ),
        throwsA(isA<Exception>().having(
          (e) => e.toString(),
          'message',
          contains('db error'),
        )),
      );
    });

    test('throws default message when error key is absent in JSON body',
        () async {
      // Verifies the null-coalescing fallback for the all-achievements route.
      await expectLater(
        http.runWithClient(
          () => GamificationService.fetchAllAchievements(1),
          () => MockClient((_) async => http.Response(jsonEncode({}), 500)),
        ),
        throwsA(isA<Exception>().having(
          (e) => e.toString(),
          'message',
          contains('Failed to load all achievements'),
        )),
      );
    });

    test('sends GET request to the all-achievements endpoint', () async {
      // Verifies the HTTP method and path for the global achievements fetch.
      http.Request? captured;
      await http.runWithClient(
        () => GamificationService.fetchAllAchievements(1),
        () => MockClient((req) async {
          captured = req;
          return http.Response('[]', 200);
        }),
      );
      expect(captured, isNotNull);
      expect(captured!.method, 'GET');
      expect(captured!.url.path, contains('all-achievements'));
    });
  });

  // ─── addXP ────────────────────────────────────────────────────────────────

  group('GamificationService.addXP()', () {
    test('completes without error on HTTP 200', () async {
      // Verifies the primary success path: 200 means XP was awarded.
      await expectLater(
        http.runWithClient(
          () => GamificationService.addXP(1, 50),
          () => MockClient((_) async => http.Response('', 200)),
        ),
        completes,
      );
    });

    test('completes without error on HTTP 201', () async {
      // Some backends return 201 for resource creation; the service accepts
      // both 200 and 201 as success.
      await expectLater(
        http.runWithClient(
          () => GamificationService.addXP(1, 10),
          () => MockClient((_) async => http.Response('', 201)),
        ),
        completes,
      );
    });

    test('throws an exception on HTTP 400', () async {
      // Any non-200/201 status means XP was NOT awarded; the caller must be
      // notified via an exception so it can surface an error to the user.
      await expectLater(
        http.runWithClient(
          () => GamificationService.addXP(1, 10),
          () => MockClient((_) async => http.Response('Bad Request', 400)),
        ),
        throwsA(isA<Exception>()),
      );
    });

    test('throws an exception on HTTP 500', () async {
      // Server-side errors must also propagate as exceptions.
      await expectLater(
        http.runWithClient(
          () => GamificationService.addXP(1, 10),
          () => MockClient((_) async => http.Response('Error', 500)),
        ),
        throwsA(isA<Exception>()),
      );
    });

    test('sends POST request to the award-xp endpoint', () async {
      // Verifies the HTTP method and path.
      http.Request? captured;
      await http.runWithClient(
        () => GamificationService.addXP(99, 100),
        () => MockClient((req) async {
          captured = req;
          return http.Response('', 200);
        }),
      );
      expect(captured, isNotNull);
      expect(captured!.method, 'POST');
      expect(captured!.url.path, contains('award-xp'));
    });

    test('request body contains userId and amount', () async {
      // Verifies the backend receives both required fields.
      http.Request? captured;
      await http.runWithClient(
        () => GamificationService.addXP(99, 100),
        () => MockClient((req) async {
          captured = req;
          return http.Response('', 200);
        }),
      );
      final body = jsonDecode(captured!.body) as Map<String, dynamic>;
      expect(body['userId'], 99);
      expect(body['amount'], 100);
    });

    test('request includes Content-Type: application/json header', () async {
      // The backend expects a JSON body; the Content-Type header must be set.
      http.Request? captured;
      await http.runWithClient(
        () => GamificationService.addXP(1, 5),
        () => MockClient((req) async {
          captured = req;
          return http.Response('', 200);
        }),
      );
      expect(
        captured!.headers['Content-Type'],
        contains('application/json'),
      );
    });
  });
}
