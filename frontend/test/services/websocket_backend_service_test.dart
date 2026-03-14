// Tests for CareConnectWebSocketService.
//
// Coverage strategy:
//   CareConnectWebSocketService.connect() opens a real WebSocket — skipped.
//   Pure-logic accessors and methods that work without a live connection
//   are tested directly.
//
//   Branches tested:
//     instance — singleton pattern; multiple calls return same object.
//     isConnected — false before connect() is called.
//     isAuthenticated — false before authentication.
//     sendCallInvitation — returns false when not authenticated.
//     messageStream, callStream, connectionStream — streams are non-null broadcast streams.

import 'package:flutter_test/flutter_test.dart';

import 'package:care_connect_app/services/websocket_backend_service.dart';

void main() {
  group('CareConnectWebSocketService singleton', () {
    test('instance returns the same object on repeated calls', () {
      final a = CareConnectWebSocketService.instance;
      final b = CareConnectWebSocketService.instance;
      expect(identical(a, b), isTrue);
    });
  });

  group('CareConnectWebSocketService initial state', () {
    test('isConnected is false before connect', () {
      expect(CareConnectWebSocketService.instance.isConnected, isFalse);
    });

    test('isAuthenticated is false before connect', () {
      expect(CareConnectWebSocketService.instance.isAuthenticated, isFalse);
    });
  });

  group('CareConnectWebSocketService.sendCallInvitation', () {
    test('returns false when not authenticated', () {
      final result = CareConnectWebSocketService.instance.sendCallInvitation(
        recipientId: 'r1',
        callType: 'video',
        callId: 'call123',
      );
      expect(result, isFalse);
    });
  });

  group('CareConnectWebSocketService streams', () {
    test('messageStream is a non-null broadcast stream', () {
      expect(
        CareConnectWebSocketService.instance.messageStream,
        isA<Stream<Map<String, dynamic>>>(),
      );
      expect(
        CareConnectWebSocketService.instance.messageStream.isBroadcast,
        isTrue,
      );
    });

    test('callStream is a non-null broadcast stream', () {
      expect(
        CareConnectWebSocketService.instance.callStream,
        isA<Stream<Map<String, dynamic>>>(),
      );
      expect(
        CareConnectWebSocketService.instance.callStream.isBroadcast,
        isTrue,
      );
    });

    test('connectionStream is a non-null broadcast stream', () {
      expect(
        CareConnectWebSocketService.instance.connectionStream,
        isA<Stream<bool>>(),
      );
      expect(
        CareConnectWebSocketService.instance.connectionStream.isBroadcast,
        isTrue,
      );
    });
  });
}
