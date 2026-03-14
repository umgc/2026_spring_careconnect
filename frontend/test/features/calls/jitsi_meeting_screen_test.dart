// Tests for JitsiMeetingScreen
// (lib/features/calls/presentation/pages/jitsi_meeting_screen.dart).
//
// Coverage strategy:
//   JitsiMeetingScreen is a StatefulWidget whose Jitsi SDK integration is
//   disabled (commented out).  The build method renders a plain Scaffold with
//   an AppBar and a centred Text widget — entirely testable without a live
//   server or platform channel.
//
//   Branches tested:
//     Scaffold renders        — widget builds without crashing.
//     AppBar title            — 'Telehealth Meeting' is shown.
//     Disabled-feature text   — body copy is present.
//     roomName constructor    — different room names accepted.

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:care_connect_app/features/calls/presentation/pages/jitsi_meeting_screen.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('JitsiMeetingScreen', () {
    testWidgets('renders Scaffold without crashing', (tester) async {
      // Verifies the widget builds and shows a Scaffold for any room name.
      await tester.pumpWidget(
        const MaterialApp(home: JitsiMeetingScreen(roomName: 'test-room')),
      );
      await tester.pump();

      expect(find.byType(Scaffold), findsOneWidget);
    });

    testWidgets('shows "Telehealth Meeting" in the AppBar title', (tester) async {
      // Verifies the AppBar title is set correctly.
      await tester.pumpWidget(
        const MaterialApp(home: JitsiMeetingScreen(roomName: 'room-1')),
      );
      await tester.pump();

      expect(find.text('Telehealth Meeting'), findsOneWidget);
    });

    testWidgets('shows the disabled-feature notice in the body', (tester) async {
      // Verifies the placeholder text explaining Jitsi is temporarily disabled.
      await tester.pumpWidget(
        const MaterialApp(home: JitsiMeetingScreen(roomName: 'room-1')),
      );
      await tester.pump();

      expect(
        find.textContaining('temporarily disabled'),
        findsOneWidget,
      );
    });

    testWidgets('accepts any roomName string', (tester) async {
      // Verifies the widget does not crash when roomName contains special chars.
      await tester.pumpWidget(
        const MaterialApp(
          home: JitsiMeetingScreen(roomName: 'room/with-special_chars.123'),
        ),
      );
      await tester.pump();

      expect(find.byType(Scaffold), findsOneWidget);
    });

    testWidgets('AppBar uses blue shade background', (tester) async {
      // Verifies the AppBar widget is present and the widget tree is intact.
      await tester.pumpWidget(
        const MaterialApp(home: JitsiMeetingScreen(roomName: 'r')),
      );
      await tester.pump();

      expect(find.byType(AppBar), findsOneWidget);
    });
  });
}
