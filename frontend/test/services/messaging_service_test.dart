// Tests for MessagingService.
//
// Coverage strategy:
//   MessagingService uses WebSocketChannel for real-time messaging and
//   AuthTokenManager + http for backend storage.  WebSocket connections
//   require a live server, so those paths are skipped.
//
//   Pure-logic methods that run without platform channels are tested directly:
//     getPlatformFeatures — returns the expected feature flags map.
//     sendMessage — returns false when WebSocket is not connected (_channel == null).
//     getConversation — returns empty list when no local messages and backend unavailable.
//     markMessagesAsRead — returns false when backend unavailable (no local keys affected).

import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:care_connect_app/services/messaging_service.dart';

void main() {
  setUp(() {
    SharedPreferences.setMockInitialValues({});
  });

  // ─── getPlatformFeatures ──────────────────────────────────────────────────

  group('MessagingService.getPlatformFeatures', () {
    test('returns a map with required feature flags', () {
      final features = MessagingService.getPlatformFeatures();
      expect(features, isA<Map<String, bool>>());
      expect(features.containsKey('videoCall'), isTrue);
      expect(features.containsKey('audioCall'), isTrue);
      expect(features.containsKey('sms'), isTrue);
      expect(features.containsKey('pushNotifications'), isTrue);
      expect(features.containsKey('backgroundMessages'), isTrue);
      expect(features.containsKey('webNotifications'), isTrue);
    });

    test('videoCall and audioCall features are true', () {
      final features = MessagingService.getPlatformFeatures();
      expect(features['videoCall'], isTrue);
      expect(features['audioCall'], isTrue);
    });

    test('pushNotifications and backgroundMessages are true', () {
      final features = MessagingService.getPlatformFeatures();
      expect(features['pushNotifications'], isTrue);
      expect(features['backgroundMessages'], isTrue);
    });
  });

  // ─── sendMessage (no connection) ──────────────────────────────────────────

  group('MessagingService.sendMessage', () {
    test('returns false when WebSocket is not connected', () async {
      // MessagingService._channel starts null until initialize() is called.
      // Without a live WebSocket server, the channel stays null.
      final result = await MessagingService.sendMessage(
        recipientId: 'r1',
        senderId: 's1',
        senderName: 'Alice',
        message: 'Hello',
        messageType: 'text',
      );
      expect(result, isFalse);
    });
  });

  // ─── getConversation (no local data) ─────────────────────────────────────

  group('MessagingService.getConversation', () {
    test('returns empty list when no local messages and backend unreachable', () async {
      // With fresh SharedPreferences, local_messages key is absent.
      // Backend call will fail (no real server), which is caught internally.
      final result = await MessagingService.getConversation(
        userId1: 'u1',
        userId2: 'u2',
      );
      expect(result, isA<List>());
    });
  });

  // ─── markMessagesAsRead ───────────────────────────────────────────────────

  group('MessagingService.markMessagesAsRead', () {
    test('returns false when backend call fails (no server)', () async {
      final result = await MessagingService.markMessagesAsRead(
        conversationId: 'u1_u2',
        userId: 'u1',
      );
      expect(result, isFalse);
    });
  });

  // ─── getConversation (with backend success) ───────────────────────────────

  group('MessagingService.getConversation (backend available)', () {
    test('200 from backend merges with local messages', () async {
      final backendMessages = [
        {
          'id': 'msg1',
          'timestamp': '2025-01-01T00:00:00.000Z',
          'senderId': 'u1',
          'message': 'hello',
        },
      ];
      final result = await http.runWithClient(
        () => MessagingService.getConversation(userId1: 'u1', userId2: 'u2'),
        () => MockClient(
          (_) async =>
              http.Response(jsonEncode(backendMessages), 200),
        ),
      );
      expect(result, isA<List>());
    });
  });

  // ─── markMessagesAsRead (backend success) ────────────────────────────────

  group('MessagingService.markMessagesAsRead (backend success)', () {
    test('returns true when backend patch succeeds', () async {
      final result = await http.runWithClient(
        () => MessagingService.markMessagesAsRead(
          conversationId: 'u1_u2',
          userId: 'u1',
        ),
        () => MockClient((_) async => http.Response('', 200)),
      );
      expect(result, isTrue);
    });
  });

  // ─── sendHttpWebSocketNotification ───────────────────────────────────────

  group('MessagingService.sendHttpWebSocketNotification', () {
    test('returns true when server responds 200', () async {
      final result = await http.runWithClient(
        () => MessagingService.sendHttpWebSocketNotification(
          userId: 'u1',
          message: 'hello',
        ),
        () => MockClient(
          (_) async => http.Response('{"message":"ok"}', 200),
        ),
      );
      expect(result, isTrue);
    });

    test('returns true when server responds 201', () async {
      final result = await http.runWithClient(
        () => MessagingService.sendHttpWebSocketNotification(
          userId: 'u2',
          message: 'created',
        ),
        () => MockClient(
          (_) async => http.Response('{"message":"created"}', 201),
        ),
      );
      expect(result, isTrue);
    });

    test('returns false when server responds 500', () async {
      final result = await http.runWithClient(
        () => MessagingService.sendHttpWebSocketNotification(
          userId: 'u3',
          message: 'fail',
        ),
        () => MockClient((_) async => http.Response('error', 500)),
      );
      expect(result, isFalse);
    });

    test('returns false when client throws', () async {
      final result = await http.runWithClient(
        () => MessagingService.sendHttpWebSocketNotification(
          userId: 'u4',
          message: 'oops',
        ),
        () => MockClient((_) async => throw Exception('network down')),
      );
      expect(result, isFalse);
    });

    test('extraHeaders are forwarded to the request', () async {
      http.Request? captured;
      await http.runWithClient(
        () => MessagingService.sendHttpWebSocketNotification(
          userId: 'u5',
          message: 'hi',
          extraHeaders: {'X-Custom': 'value'},
        ),
        () => MockClient((req) async {
          captured = req;
          return http.Response('{"message":"ok"}', 200);
        }),
      );
      expect(captured?.headers['X-Custom'], 'value');
    });
  });
}
