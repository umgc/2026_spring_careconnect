import 'package:care_connect_app/features/social/presentation/pages/chat_room_screen.dart';
import 'package:care_connect_app/providers/user_provider.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('ChatRoomScreen pending messaging', () {
    setUp(() {
      SharedPreferences.setMockInitialValues({});
    });

    testWidgets('keeps pending across refresh and sends after reconnect', (
      tester,
    ) async {
      final userProvider = UserProvider()
        ..setUser(UserSession(
          id: 7,
          email: 'patient@test.careconnect.dev',
          role: 'PATIENT',
          token: 't',
        ));

      bool online = false;
      final delivered = <String>[];
      var chatBuildCount = 0;

      Future<List<dynamic>> loadConversation({
        required int user1,
        required int user2,
      }) async {
        return <dynamic>[];
      }

      Future<void> sendMessage({
        required int senderId,
        required int receiverId,
        required String content,
      }) async {
        if (!online) {
          throw Exception('offline');
        }
        delivered.add(content);
      }

      Future<void> pumpChat() async {
        await tester.pumpWidget(
          ChangeNotifierProvider<UserProvider>.value(
            value: userProvider,
            child: MaterialApp(
              home: ChatRoomScreen(
                key: ValueKey('chat-${chatBuildCount++}'),
                peerUserId: 21,
                peerName: 'Peer User',
                enableAutoSync: false,
                conversationLoader: loadConversation,
                messageSender: sendMessage,
              ),
            ),
          ),
        );
        await tester.pumpAndSettle();
      }

      await pumpChat();
      await tester.enterText(find.byType(TextField), 'hello offline');
      await tester.tap(find.byIcon(Icons.send));
      await tester.pumpAndSettle();

      expect(find.text('hello offline'), findsOneWidget);
      expect(delivered, isEmpty);

      await pumpChat();
      expect(find.text('hello offline'), findsOneWidget);
      expect(delivered, isEmpty);

      online = true;
      await pumpChat();
      await tester.pumpAndSettle();

      expect(delivered, ['hello offline']);
      expect(find.text('hello offline'), findsNothing);
    });

    testWidgets('resends multiple pending messages in original order', (
      tester,
    ) async {
      final userProvider = UserProvider()
        ..setUser(UserSession(
          id: 7,
          email: 'patient@test.careconnect.dev',
          role: 'PATIENT',
          token: 't',
        ));

      bool online = false;
      final delivered = <String>[];
      var chatBuildCount = 0;

      Future<List<dynamic>> loadConversation({
        required int user1,
        required int user2,
      }) async {
        return <dynamic>[];
      }

      Future<void> sendMessage({
        required int senderId,
        required int receiverId,
        required String content,
      }) async {
        if (!online) {
          throw Exception('offline');
        }
        delivered.add(content);
      }

      Future<void> pumpChat() async {
        await tester.pumpWidget(
          ChangeNotifierProvider<UserProvider>.value(
            value: userProvider,
            child: MaterialApp(
              home: ChatRoomScreen(
                key: ValueKey('chat-multi-${chatBuildCount++}'),
                peerUserId: 21,
                peerName: 'Peer User',
                enableAutoSync: false,
                conversationLoader: loadConversation,
                messageSender: sendMessage,
              ),
            ),
          ),
        );
        await tester.pumpAndSettle();
      }

      await pumpChat();

      await tester.enterText(find.byType(TextField), 'first offline');
      await tester.tap(find.byIcon(Icons.send));
      await tester.pumpAndSettle();

      await tester.enterText(find.byType(TextField), 'second offline');
      await tester.tap(find.byIcon(Icons.send));
      await tester.pumpAndSettle();

      expect(find.text('first offline'), findsOneWidget);
      expect(find.text('second offline'), findsOneWidget);
      expect(delivered, isEmpty);

      online = true;
      await pumpChat();
      await tester.pumpAndSettle();

      expect(delivered, ['first offline', 'second offline']);
      expect(find.text('first offline'), findsNothing);
      expect(find.text('second offline'), findsNothing);
    });
  });
}
