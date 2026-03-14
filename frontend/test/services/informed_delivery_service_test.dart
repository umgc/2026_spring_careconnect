// Tests for InformedDeliveryService.
//
// Coverage strategy:
//   InformedDeliveryService.fetchInformedDelivery calls
//   AuthTokenManager.getAuthHeaders() (FlutterSecureStorage MethodChannel)
//   then http.get (top-level, intercepted via http.runWithClient + MockClient).
//
//   Branches tested:
//     fetchInformedDelivery — 200 with JSON body → returns decoded map.
//     fetchInformedDelivery — 200 with empty body → throws 'Failed to fetch'.
//     fetchInformedDelivery — 401 → throws 'Not authorized'.
//     fetchInformedDelivery — other status → throws 'Failed to fetch'.

import 'dart:convert';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:care_connect_app/services/informed_delivery_service.dart';

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

  group('InformedDeliveryService.fetchInformedDelivery', () {
    test('200 with JSON body → returns decoded map', () async {
      final payload = {'mailCount': 3, 'items': []};
      final result = await http.runWithClient(
        () => InformedDeliveryService.fetchInformedDelivery(),
        () => MockClient(
          (_) async => http.Response(jsonEncode(payload), 200),
        ),
      );
      expect(result['mailCount'], 3);
    });

    test('200 with empty body → throws Failed to fetch', () async {
      await expectLater(
        http.runWithClient(
          () => InformedDeliveryService.fetchInformedDelivery(),
          () => MockClient((_) async => http.Response('', 200)),
        ),
        throwsA(isA<Exception>().having(
          (e) => e.toString(),
          'message',
          contains('Failed to fetch informed delivery data'),
        )),
      );
    });

    test('401 → throws Not authorized', () async {
      await expectLater(
        http.runWithClient(
          () => InformedDeliveryService.fetchInformedDelivery(),
          () => MockClient((_) async => http.Response('', 401)),
        ),
        throwsA(isA<Exception>().having(
          (e) => e.toString(),
          'message',
          contains('Not authorized'),
        )),
      );
    });

    test('other status (500) → throws Failed to fetch', () async {
      await expectLater(
        http.runWithClient(
          () => InformedDeliveryService.fetchInformedDelivery(),
          () => MockClient((_) async => http.Response('Server Error', 500)),
        ),
        throwsA(isA<Exception>().having(
          (e) => e.toString(),
          'message',
          contains('Failed to fetch informed delivery data'),
        )),
      );
    });

    test('Authorization header is forwarded when token is stored', () async {
      // Seed a token so getAuthHeaders includes Authorization.
      _secureStore['jwt_token'] = 'my-jwt';
      // Also seed a far-future expiry so the token is considered valid.
      final futureExp =
          DateTime.now().millisecondsSinceEpoch ~/ 1000 + 3600;
      _secureStore['token_expiry'] = futureExp.toString();

      String? capturedAuth;
      await http.runWithClient(
        () => InformedDeliveryService.fetchInformedDelivery(),
        () => MockClient((req) async {
          capturedAuth = req.headers['Authorization'];
          return http.Response(jsonEncode({'ok': true}), 200);
        }),
      );
      expect(capturedAuth, startsWith('Bearer '));
    });
  });
}
