// Tests for NotificationService.
//
// Coverage strategy:
//   NotificationService uses WebSocketChannel which requires a live server.
//   The initialize() method is not called in these tests to avoid real connections.
//
//   Branches tested:
//     isConnected getter — false before initialize() is called.
//     channel getter — null before initialize() is called.
//     dispose() — sets isConnected to false and does not throw when channel is null.

import 'package:flutter_test/flutter_test.dart';

import 'package:care_connect_app/services/notification_service.dart';

void main() {
  // Ensure dispose is called to reset state before each test.
  setUp(() {
    NotificationService.dispose();
  });

  group('NotificationService initial state', () {
    test('isConnected is false before initialize', () {
      expect(NotificationService.isConnected, isFalse);
    });

    test('channel is null before initialize', () {
      expect(NotificationService.channel, isNull);
    });
  });

  group('NotificationService.dispose', () {
    test('dispose when not connected does not throw', () {
      expect(() => NotificationService.dispose(), returnsNormally);
    });

    test('isConnected is false after dispose', () {
      NotificationService.dispose();
      expect(NotificationService.isConnected, isFalse);
    });

    test('channel is null after dispose', () {
      NotificationService.dispose();
      expect(NotificationService.channel, isNull);
    });
  });
}
