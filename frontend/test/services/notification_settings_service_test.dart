// Tests for NotificationSettingsService.
//
// Coverage strategy:
//   NotificationSettingsService has two static methods:
//     • getNotificationSettings(userId) — GET /v1/api/notification-settings/{id}
//     • saveNotificationSettings(settings) — POST /v1/api/notification-settings
//
//   Both methods call AuthTokenManager.getAuthHeaders(), which reads from
//   flutter_secure_storage.  The secure-storage channel is stubbed to return
//   null, so getAuthHeaders() produces headers with no Authorization entry.
//
//   HTTP is mocked via http.runWithClient() + MockClient so no real network
//   calls are made.  Every status-code branch (200, 201, 404, 500, exception)
//   is exercised, and the outgoing request shape is verified.

import 'dart:convert';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

import 'package:care_connect_app/models/notification_settings.dart';
import 'package:care_connect_app/services/notification_settings_service.dart';

// The flutter_secure_storage plugin registers under this channel.  Stubbing it
// avoids MissingPluginException when AuthTokenManager.getAuthHeaders() runs.
const MethodChannel _secureStorageChannel =
    MethodChannel('plugins.it_nomads.com/flutter_secure_storage');

// ─── Test-data helpers ────────────────────────────────────────────────────────

/// Builds a JSON map that [NotificationSettings.fromJson] can parse.
Map<String, dynamic> _settingsJson({int userId = 1}) => {
      'id': 10,
      'userId': userId,
      'gamification': true,
      'emergency': true,
      'videoCall': false,
      'audioCall': true,
      'sms': false,
      'significantVitals': true,
    };

/// Builds a [NotificationSettings] object for use as save input.
NotificationSettings _settings({int userId = 1}) => NotificationSettings(
      userId: userId,
      gamification: true,
      emergency: true,
      videoCall: false,
      audioCall: true,
      sms: false,
      significantVitals: true,
    );

// ─── Test entry point ─────────────────────────────────────────────────────────

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    // Intercept flutter_secure_storage channel calls and return null.
    // This simulates empty storage: getJwtToken() → null → no auth header.
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(_secureStorageChannel, (_) async => null);
  });

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(_secureStorageChannel, null);
  });

  // ─── getNotificationSettings ───────────────────────────────────────────────

  group('NotificationSettingsService.getNotificationSettings()', () {
    test('returns a NotificationSettings on HTTP 200', () async {
      // Verifies the happy-path: a 200 response with valid JSON is parsed into
      // a NotificationSettings object and returned to the caller.
      final result = await http.runWithClient(
        () => NotificationSettingsService.getNotificationSettings(1),
        () => MockClient((_) async =>
            http.Response(jsonEncode(_settingsJson()), 200)),
      );
      expect(result, isA<NotificationSettings>());
    });

    test('parsed settings carry the correct userId', () async {
      // Verifies that the userId from the JSON is stored in the model.
      final result = await http.runWithClient(
        () => NotificationSettingsService.getNotificationSettings(7),
        () => MockClient((_) async =>
            http.Response(jsonEncode(_settingsJson(userId: 7)), 200)),
      );
      expect(result!.userId, 7);
    });

    test('parsed settings carry the correct boolean field values', () async {
      // Verifies that individual boolean fields are correctly decoded.
      final result = await http.runWithClient(
        () => NotificationSettingsService.getNotificationSettings(1),
        () => MockClient((_) async =>
            http.Response(jsonEncode(_settingsJson()), 200)),
      );
      expect(result!.gamification, isTrue);
      expect(result.videoCall, isFalse);
      expect(result.sms, isFalse);
      expect(result.emergency, isTrue);
    });

    test('returns default settings (not null) on HTTP 404', () async {
      // A 404 means the user has no saved preferences.  Rather than returning
      // null, the service returns a default object so the UI stays functional.
      final result = await http.runWithClient(
        () => NotificationSettingsService.getNotificationSettings(5),
        () => MockClient((_) async => http.Response('Not Found', 404)),
      );
      expect(result, isA<NotificationSettings>());
    });

    test('default settings on 404 have the requesting userId', () async {
      // The default object must be seeded with the correct userId so the UI
      // can later save it without the user having to re-enter their id.
      final result = await http.runWithClient(
        () => NotificationSettingsService.getNotificationSettings(5),
        () => MockClient((_) async => http.Response('Not Found', 404)),
      );
      expect(result!.userId, 5);
    });

    test('default settings on 404 enable every notification type', () async {
      // The hardcoded defaults opt-in to all notification channels.
      final result = await http.runWithClient(
        () => NotificationSettingsService.getNotificationSettings(5),
        () => MockClient((_) async => http.Response('Not Found', 404)),
      );
      expect(result!.gamification, isTrue);
      expect(result.emergency, isTrue);
      expect(result.videoCall, isTrue);
      expect(result.audioCall, isTrue);
      expect(result.sms, isTrue);
      expect(result.significantVitals, isTrue);
    });

    test('returns null on HTTP 500', () async {
      // An unexpected server error cannot be recovered from, so null is
      // returned so the caller can decide how to handle it.
      final result = await http.runWithClient(
        () => NotificationSettingsService.getNotificationSettings(1),
        () => MockClient((_) async => http.Response('Server Error', 500)),
      );
      expect(result, isNull);
    });

    test('returns null on HTTP 400', () async {
      // Any unhandled status (not 200 or 404) results in null.
      final result = await http.runWithClient(
        () => NotificationSettingsService.getNotificationSettings(1),
        () => MockClient((_) async => http.Response('Bad Request', 400)),
      );
      expect(result, isNull);
    });

    test('returns null when response JSON is malformed (exception path)', () async {
      // A 200 with invalid JSON triggers a FormatException.  The outer
      // try/catch must swallow it and return null gracefully.
      final result = await http.runWithClient(
        () => NotificationSettingsService.getNotificationSettings(1),
        () => MockClient((_) async => http.Response('not-valid-json!!!', 200)),
      );
      expect(result, isNull);
    });

    test('sends GET request to the notification-settings/{userId} endpoint',
        () async {
      // Verifies the request method and that the userId appears in the path.
      http.Request? captured;
      await http.runWithClient(
        () => NotificationSettingsService.getNotificationSettings(42),
        () => MockClient((req) async {
          captured = req;
          return http.Response(jsonEncode(_settingsJson(userId: 42)), 200);
        }),
      );
      expect(captured, isNotNull);
      expect(captured!.method, 'GET');
      expect(captured!.url.path, contains('notification-settings'));
      expect(captured!.url.path, contains('42'));
    });
  });

  // ─── saveNotificationSettings ─────────────────────────────────────────────

  group('NotificationSettingsService.saveNotificationSettings()', () {
    test('returns a NotificationSettings on HTTP 200', () async {
      // Verifies the happy-path update: a 200 response is parsed and returned.
      final result = await http.runWithClient(
        () => NotificationSettingsService.saveNotificationSettings(_settings()),
        () => MockClient((_) async =>
            http.Response(jsonEncode(_settingsJson()), 200)),
      );
      expect(result, isA<NotificationSettings>());
    });

    test('returns a NotificationSettings on HTTP 201', () async {
      // REST convention for newly created resources; the service accepts both
      // 200 and 201 as success.
      final result = await http.runWithClient(
        () => NotificationSettingsService.saveNotificationSettings(_settings()),
        () => MockClient((_) async =>
            http.Response(jsonEncode(_settingsJson()), 201)),
      );
      expect(result, isA<NotificationSettings>());
    });

    test('returned settings reflect the server response, not the input', () async {
      // The method returns what the server persisted, allowing the caller to
      // detect any transformations the backend applied.
      final serverJson = _settingsJson();
      serverJson['gamification'] = false; // server overwrote this field

      final result = await http.runWithClient(
        () => NotificationSettingsService.saveNotificationSettings(
            _settings(userId: 1)),
        () => MockClient((_) async =>
            http.Response(jsonEncode(serverJson), 200)),
      );
      expect(result!.gamification, isFalse);
    });

    test('returns null on HTTP 400', () async {
      // A validation error from the backend must be surfaced as null.
      final result = await http.runWithClient(
        () => NotificationSettingsService.saveNotificationSettings(_settings()),
        () => MockClient((_) async => http.Response('Bad Request', 400)),
      );
      expect(result, isNull);
    });

    test('returns null on HTTP 500', () async {
      final result = await http.runWithClient(
        () => NotificationSettingsService.saveNotificationSettings(_settings()),
        () => MockClient((_) async => http.Response('Server Error', 500)),
      );
      expect(result, isNull);
    });

    test('returns null when response JSON is malformed (exception path)', () async {
      // A 200 response with non-JSON body causes a FormatException.  The catch
      // block must return null rather than letting the error propagate.
      final result = await http.runWithClient(
        () => NotificationSettingsService.saveNotificationSettings(_settings()),
        () =>
            MockClient((_) async => http.Response('not-valid-json!!!', 200)),
      );
      expect(result, isNull);
    });

    test('sends POST request to the notification-settings endpoint', () async {
      // Verifies the request uses the POST method and targets the correct path.
      http.Request? captured;
      await http.runWithClient(
        () => NotificationSettingsService.saveNotificationSettings(_settings()),
        () => MockClient((req) async {
          captured = req;
          return http.Response(jsonEncode(_settingsJson()), 200);
        }),
      );
      expect(captured, isNotNull);
      expect(captured!.method, 'POST');
      expect(captured!.url.path, contains('notification-settings'));
    });

    test('request body contains the serialised settings fields', () async {
      // Verifies that the settings.toJson() output is included in the payload.
      http.Request? captured;
      await http.runWithClient(
        () => NotificationSettingsService.saveNotificationSettings(
            _settings(userId: 9)),
        () => MockClient((req) async {
          captured = req;
          return http.Response(jsonEncode(_settingsJson(userId: 9)), 200);
        }),
      );
      final body = jsonDecode(captured!.body) as Map<String, dynamic>;
      expect(body['userId'], 9);
      expect(body['gamification'], isTrue);
      expect(body['videoCall'], isFalse);
    });
  });
}
