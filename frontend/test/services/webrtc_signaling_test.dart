// Tests for WebRTCSignaling.
//
// Coverage strategy:
//   WebRTCSignaling.sendSignal delegates to MessagingService.sendHttpWebSocketNotification
//   which requires a live HTTP server — that path is skipped.
//   The constructor and apiBaseUrl property are pure Dart and fully testable.
//
//   Branches tested:
//     constructor — stores apiBaseUrl correctly.
//     apiBaseUrl property — returns the value passed to the constructor.

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:care_connect_app/services/webrtc_signaling.dart';

void main() {
  setUp(() {
    SharedPreferences.setMockInitialValues({});
  });

  group('WebRTCSignaling constructor', () {
    test('stores apiBaseUrl from constructor argument', () {
      const url = 'http://localhost:8080';
      final signaling = WebRTCSignaling(url);
      expect(signaling.apiBaseUrl, url);
    });

    test('different instances store independent URLs', () {
      final a = WebRTCSignaling('http://localhost:8080');
      final b = WebRTCSignaling('https://prod.example.com');
      expect(a.apiBaseUrl, 'http://localhost:8080');
      expect(b.apiBaseUrl, 'https://prod.example.com');
    });
  });

  // ─── sendSignal ───────────────────────────────────────────────────────────

  group('WebRTCSignaling.sendSignal', () {
    test('returns true when backend responds 200', () async {
      final signaling = WebRTCSignaling('http://localhost:8080');
      final result = await http.runWithClient(
        () => signaling.sendSignal(userId: 'u1', message: 'offer'),
        () => MockClient(
          (_) async => http.Response('{"message":"ok"}', 200),
        ),
      );
      expect(result, isTrue);
    });

    test('returns true when backend responds 201', () async {
      final signaling = WebRTCSignaling('http://localhost:8080');
      final result = await http.runWithClient(
        () => signaling.sendSignal(userId: 'u1', message: 'answer'),
        () => MockClient(
          (_) async => http.Response('{"message":"created"}', 201),
        ),
      );
      expect(result, isTrue);
    });

    test('returns false when backend responds 500', () async {
      final signaling = WebRTCSignaling('http://localhost:8080');
      final result = await http.runWithClient(
        () => signaling.sendSignal(userId: 'u1', message: 'candidate'),
        () => MockClient((_) async => http.Response('error', 500)),
      );
      expect(result, isFalse);
    });

    test('returns false when client throws', () async {
      final signaling = WebRTCSignaling('http://localhost:8080');
      final result = await http.runWithClient(
        () => signaling.sendSignal(userId: 'u1', message: 'ice'),
        () => MockClient((_) async => throw Exception('network error')),
      );
      expect(result, isFalse);
    });

    test('extraHeaders are forwarded through to the HTTP request', () async {
      final signaling = WebRTCSignaling('http://localhost:8080');
      http.Request? captured;
      await http.runWithClient(
        () => signaling.sendSignal(
          userId: 'u2',
          message: 'ping',
          extraHeaders: {'X-Call-Id': 'abc123'},
        ),
        () => MockClient((req) async {
          captured = req;
          return http.Response('{"message":"ok"}', 200);
        }),
      );
      expect(captured?.headers['X-Call-Id'], 'abc123');
    });
  });
}
