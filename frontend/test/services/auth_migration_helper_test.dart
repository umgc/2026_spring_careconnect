import 'dart:convert';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:care_connect_app/services/auth_migration_helper.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  const MethodChannel secureStorageChannel =
      MethodChannel('plugins.it_nomads.com/flutter_secure_storage');

  // In-memory fake secure storage used by the plugin channel mock.
  final Map<String, String> secureStorage = <String, String>{};

  // Flags used to force plugin failures for negative-path tests.
  bool failReads = false;
  bool failWrites = false;
  bool failDeletes = false;

  // Keys that AuthTokenManager may plausibly use in the "new" system.
  // Preloading these makes the "already migrated" test resilient even
  // if the token manager uses a different common token key.
  const List<String> likelyNewTokenKeys = <String>[
    'jwtToken',
    'jwt_token',
    'authToken',
    'auth_token',
    'accessToken',
    'access_token',
    'token',
  ];

  setUpAll(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
      secureStorageChannel,
      (MethodCall methodCall) async {
        final Map<dynamic, dynamic> args =
            (methodCall.arguments as Map<dynamic, dynamic>?) ??
                <dynamic, dynamic>{};

        final String? key = args['key'] as String?;
        final String? value = args['value'] as String?;

        switch (methodCall.method) {
          case 'read':
            if (failReads) {
              throw PlatformException(
                code: 'read_error',
                message: 'Forced secure storage read failure',
              );
            }
            return secureStorage[key];

          case 'write':
            if (failWrites) {
              throw PlatformException(
                code: 'write_error',
                message: 'Forced secure storage write failure',
              );
            }
            if (key != null && value != null) {
              secureStorage[key] = value;
            }
            return null;

          case 'delete':
            if (failDeletes) {
              throw PlatformException(
                code: 'delete_error',
                message: 'Forced secure storage delete failure',
              );
            }
            if (key != null) {
              secureStorage.remove(key);
            }
            return null;

          case 'deleteAll':
            if (failDeletes) {
              throw PlatformException(
                code: 'delete_all_error',
                message: 'Forced secure storage deleteAll failure',
              );
            }
            secureStorage.clear();
            return null;

          case 'containsKey':
            return key != null && secureStorage.containsKey(key);

          case 'readAll':
            return Map<String, String>.from(secureStorage);

          default:
            return null;
        }
      },
    );
  });

  tearDownAll(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(secureStorageChannel, null);
  });

  setUp(() async {
    secureStorage.clear();
    failReads = false;
    failWrites = false;
    failDeletes = false;
    SharedPreferences.setMockInitialValues(<String, Object>{});
  });

  group('MigrationResult.isSuccess', () {
    test('returns true for success and alreadyMigrated', () {
      // Verifies that success-like statuses are treated as successful results.
      expect(
        const MigrationResult(MigrationStatus.success).isSuccess,
        isTrue,
      );
      expect(
        const MigrationResult(MigrationStatus.alreadyMigrated).isSuccess,
        isTrue,
      );
    });

    test('returns false for noDataFound and failed', () {
      // Verifies that non-success statuses are not treated as successful.
      expect(
        const MigrationResult(MigrationStatus.noDataFound).isSuccess,
        isFalse,
      );
      expect(
        const MigrationResult(MigrationStatus.failed).isSuccess,
        isFalse,
      );
    });
  });

  group('AuthMigrationHelper.migrateAuthData', () {
    test('returns alreadyMigrated when new system already has a token', () async {
      // Preload common token key names so AuthTokenManager.getJwtToken()
      // sees that the new unified system already contains auth data.
      for (final String key in likelyNewTokenKeys) {
        secureStorage[key] = 'existing-token';
      }
      // Provide a far-future expiry so _isTokenValid() returns true without
      // attempting a backend HTTP call (which always returns 400 in tests).
      final int farFuture =
          DateTime.now().add(const Duration(days: 365)).millisecondsSinceEpoch ~/
              1000;
      secureStorage['token_expiry'] = farFuture.toString();

      final MigrationResult result =
          await AuthMigrationHelper.migrateAuthData();

      expect(result.status, MigrationStatus.alreadyMigrated);
      expect(result.isSuccess, isTrue);
    });

    test('returns success when legacy token and valid legacy session exist',
        () async {
      // Simulates the old storage format:
      // - authCookie holds the JWT token
      // - session holds JSON session data
      secureStorage['authCookie'] = 'legacy-token';
      secureStorage['session'] = jsonEncode(<String, dynamic>{
        'userId': 42,
        'email': 'user@example.com',
        'roles': <String>['user'],
      });

      SharedPreferences.setMockInitialValues(<String, Object>{
        'session_cookie': 'old-cookie-value',
      });

      final MigrationResult result =
          await AuthMigrationHelper.migrateAuthData();

      // Verifies that migration reports success.
      expect(result.status, MigrationStatus.success);
      expect(result.isSuccess, isTrue);

      // Verifies that legacy storage is cleaned up after a successful migration.
      expect(secureStorage.containsKey('authCookie'), isFalse);
      expect(secureStorage.containsKey('session'), isFalse);

      final SharedPreferences prefs = await SharedPreferences.getInstance();
      expect(prefs.containsKey('session_cookie'), isFalse);
    });

    test(
        'returns noDataFound when no legacy token/session data exists at all',
        () async {
      // Verifies the "nothing to migrate" path.
      final MigrationResult result =
          await AuthMigrationHelper.migrateAuthData();

      expect(result.status, MigrationStatus.noDataFound);
      expect(result.isSuccess, isFalse);
    });

    test(
        'returns noDataFound when legacy token exists but session JSON is invalid',
        () async {
      // Simulates a legacy token plus corrupted session JSON.
      // The helper should ignore the bad session JSON and report no data found
      // because both token and parsed session are required for migration.
      secureStorage['authCookie'] = 'legacy-token';
      secureStorage['session'] = '{this-is-not-valid-json';

      final MigrationResult result =
          await AuthMigrationHelper.migrateAuthData();

      expect(result.status, MigrationStatus.noDataFound);
      expect(result.isSuccess, isFalse);

      // Since migration did not succeed, the old keys should still remain.
      expect(secureStorage['authCookie'], 'legacy-token');
      expect(secureStorage['session'], '{this-is-not-valid-json');
    });

    test('returns failed when secure storage throws during migration',
        () async {
      // Forces a storage read failure so the outer try/catch in migrateAuthData()
      // returns a failed MigrationResult with an error message.
      failReads = true;

      final MigrationResult result =
          await AuthMigrationHelper.migrateAuthData();

      expect(result.status, MigrationStatus.failed);
      expect(result.isSuccess, isFalse);
      expect(result.errorMessage, isNotNull);
      expect(result.errorMessage!, contains('Migration failed'));
    });

    test(
        'still returns success when migration succeeds but legacy cleanup fails',
        () async {
      // This covers the private _cleanupOldStorage() catch path.
      // Migration should still succeed because cleanup failure is explicitly
      // treated as non-critical by the implementation.
      secureStorage['authCookie'] = 'legacy-token';
      secureStorage['session'] = jsonEncode(<String, dynamic>{
        'userId': 7,
        'name': 'Cleanup Failure Case',
      });

      failDeletes = true;

      final MigrationResult result =
          await AuthMigrationHelper.migrateAuthData();

      expect(result.status, MigrationStatus.success);
      expect(result.isSuccess, isTrue);

      // Because cleanup failed, the old values may still be present.
      expect(secureStorage['authCookie'], 'legacy-token');
      expect(secureStorage['session'], isNotNull);
    });
  });

  group('AuthMigrationHelper.clearAllAuthData', () {
    test('returns true and removes legacy storage/prefs when clearing auth data',
        () async {
      // Seeds old auth data and an old shared preference entry, then verifies
      // that clearAllAuthData() completes successfully and removes the legacy
      // values via _cleanupOldStorage().
      secureStorage['authCookie'] = 'legacy-token';
      secureStorage['session'] = jsonEncode(<String, dynamic>{
        'id': 1,
      });

      SharedPreferences.setMockInitialValues(<String, Object>{
        'session_cookie': 'old-cookie-value',
      });

      final bool result = await AuthMigrationHelper.clearAllAuthData();

      expect(result, isTrue);
      expect(secureStorage.containsKey('authCookie'), isFalse);
      expect(secureStorage.containsKey('session'), isFalse);

      final SharedPreferences prefs = await SharedPreferences.getInstance();
      expect(prefs.containsKey('session_cookie'), isFalse);
    });
  });
}