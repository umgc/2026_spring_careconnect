import 'package:care_connect_app/features/social/presentation/model/chat_pending_queue_manager.dart';
import 'package:care_connect_app/features/social/presentation/model/message_dto.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _FakeTransport {
  bool online = false;
  final List<String> deliveredContents = <String>[];

  Future<bool> send(MessageDto message) async {
    if (!online) {
      return false;
    }
    deliveredContents.add(message.content);
    return true;
  }
}

Future<void> _retryAllPending({
  required List<MessageDto> pendingMessages,
  required Future<bool> Function(MessageDto message) send,
}) async {
  final snapshot = List<MessageDto>.from(pendingMessages);
  for (final pending in snapshot) {
    final success = await send(pending);
    if (success) {
      pendingMessages.removeWhere((message) => message.id == pending.id);
    }
  }
}

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  group('Chat messaging E2E-style flow', () {
    testWidgets('offline pending messages persist and deliver after reconnect',
        (
      tester,
    ) async {
      SharedPreferences.setMockInitialValues({});
      final manager = ChatPendingQueueManager();
      final transport = _FakeTransport();

      final key = manager.conversationKey(currentUserId: 7, peerUserId: 21);
      final pending = <MessageDto>[
        MessageDto(
          id: -100,
          senderId: 7,
          receiverId: 21,
          content: 'offline first',
          timestamp: DateTime.utc(2026, 3, 1, 9, 0),
        ),
        MessageDto(
          id: -101,
          senderId: 7,
          receiverId: 21,
          content: 'offline second',
          timestamp: DateTime.utc(2026, 3, 1, 9, 1),
        ),
      ];

      await manager.persistToDisk(key, pending);
      final restoredAfterRefresh = await manager.restoreFromDisk(key);
      expect(restoredAfterRefresh, hasLength(2));

      await _retryAllPending(
        pendingMessages: restoredAfterRefresh,
        send: transport.send,
      );
      expect(restoredAfterRefresh, hasLength(2));
      expect(transport.deliveredContents, isEmpty);

      transport.online = true;
      await _retryAllPending(
        pendingMessages: restoredAfterRefresh,
        send: transport.send,
      );

      expect(restoredAfterRefresh, isEmpty);
      expect(transport.deliveredContents, ['offline first', 'offline second']);

      await manager.persistToDisk(key, restoredAfterRefresh);
      final restoredAfterSend = await manager.restoreFromDisk(key);
      expect(restoredAfterSend, isEmpty);
    });
  });
}
