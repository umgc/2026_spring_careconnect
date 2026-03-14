// Tests for PatientVirtualCheckIn
// (lib/features/health/virtual_check_in/presentation/pages/patient_check_in_page.dart).
//
// Coverage strategy:
//   PatientVirtualCheckIn calls availableCameras() in initState via a
//   Flutter platform channel.  In the test environment the channel is mocked
//   to return an empty list, so _checkCameraAvailability sets
//   isCameraAvailable = false and isCheckingCamera = false.
//
//   Branches tested:
//     isCheckingCamera = true  (initial frame before mock responds)
//       — Scaffold and FAB render.
//     isCheckingCamera = false, isCameraAvailable = false (after mock)
//       — FAB uses Icons.videocam_off.
//       — Body shows the camera-unavailable notice.
//       — "💙 Daily Check-In" header text is present.
//       — Mood selection emoji options are rendered.

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:care_connect_app/features/health/virtual_check_in/presentation/pages/patient_check_in_page.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  // Mock the camera platform channel to return an empty list of cameras.
  // This prevents the test from hanging on an unresponsive platform channel.
  setUp(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
      const MethodChannel('plugins.flutter.io/camera'),
      (call) async {
        if (call.method == 'availableCameras') return <dynamic>[];
        return null;
      },
    );
  });

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
      const MethodChannel('plugins.flutter.io/camera'),
      null,
    );
  });

  // ─── camera check throws → catch branch ─────────────────────────────────

  group('PatientVirtualCheckIn – camera throws', () {
    setUp(() {
      // Override: throw a PlatformException to exercise the catch block
      // in _checkCameraAvailability.
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(
        const MethodChannel('plugins.flutter.io/camera'),
        (call) async {
          if (call.method == 'availableCameras') {
            throw PlatformException(
              code: 'CAMERA_ERROR',
              message: 'No cameras available',
            );
          }
          return null;
        },
      );
    });

    testWidgets('renders Scaffold when availableCameras throws', (
      tester,
    ) async {
      // Verifies the catch block sets isCameraAvailable = false and
      // isCheckingCamera = false without crashing the widget.
      await tester.pumpWidget(
        const MaterialApp(home: PatientVirtualCheckIn()),
      );
      await tester.pumpAndSettle();

      expect(find.byType(Scaffold), findsOneWidget);
      // Camera-unavailable notice should appear (same UI as empty-list path).
      expect(find.textContaining('Camera not available'), findsWidgets);
    });
  });

  // ─── normal camera (empty list) ───────────────────────────────────────────

  group('PatientVirtualCheckIn', () {
    testWidgets('renders Scaffold on first pump', (tester) async {
      // Verifies the widget builds without crashing on the initial frame.
      await tester.pumpWidget(
        const MaterialApp(home: PatientVirtualCheckIn()),
      );
      await tester.pump();

      expect(find.byType(Scaffold), findsOneWidget);
      expect(find.byType(FloatingActionButton), findsOneWidget);
    });

    testWidgets('shows "Daily Check-In" heading after camera check', (
      tester,
    ) async {
      // Verifies the page header renders once the async camera check completes.
      await tester.pumpWidget(
        const MaterialApp(home: PatientVirtualCheckIn()),
      );
      await tester.pumpAndSettle();

      expect(find.textContaining('Daily Check-In'), findsOneWidget);
    });

    testWidgets('shows camera-unavailable notice when no camera found', (
      tester,
    ) async {
      // Verifies the "Camera not available" UI is shown when the mock
      // returns an empty camera list.
      await tester.pumpWidget(
        const MaterialApp(home: PatientVirtualCheckIn()),
      );
      await tester.pumpAndSettle();

      expect(
        find.textContaining('Camera not available'),
        findsWidgets,
      );
    });

    testWidgets('FAB uses videocam_off icon when no camera', (tester) async {
      // Verifies the FAB reflects the no-camera state.
      await tester.pumpWidget(
        const MaterialApp(home: PatientVirtualCheckIn()),
      );
      await tester.pumpAndSettle();

      expect(find.byIcon(Icons.videocam_off), findsOneWidget);
    });

    testWidgets('mood selection emoji options are rendered', (tester) async {
      // Verifies that all 5 mood emoji options defined in moodOptions appear.
      await tester.pumpWidget(
        const MaterialApp(home: PatientVirtualCheckIn()),
      );
      await tester.pumpAndSettle();

      for (final emoji in ['😢', '😞', '😐', '🙂', '😊']) {
        expect(
          find.text(emoji),
          findsOneWidget,
          reason: 'Expected mood emoji $emoji to be present',
        );
      }
    });

    testWidgets('shows "How are you feeling today?" question', (tester) async {
      // Verifies the mood card heading is rendered.
      await tester.pumpWidget(
        const MaterialApp(home: PatientVirtualCheckIn()),
      );
      await tester.pumpAndSettle();

      expect(find.text('How are you feeling today?'), findsOneWidget);
    });

    testWidgets('shows "Any symptoms or notes?" heading', (tester) async {
      // Verifies the notes card heading is rendered.
      await tester.pumpWidget(
        const MaterialApp(home: PatientVirtualCheckIn()),
      );
      await tester.pumpAndSettle();

      expect(find.text('Any symptoms or notes?'), findsOneWidget);
    });

    testWidgets('shows submit button and mood-required hint initially', (
      tester,
    ) async {
      // Verifies the Submit Check-In button and the mood-required hint text
      // appear before a mood is selected.
      await tester.pumpWidget(
        const MaterialApp(home: PatientVirtualCheckIn()),
      );
      await tester.pumpAndSettle();

      expect(find.text('Submit Check-In'), findsOneWidget);
      expect(
        find.text('Please select your mood to submit your check-in'),
        findsOneWidget,
      );
    });

    testWidgets('tapping a mood emoji selects it and hides hint', (
      tester,
    ) async {
      // Verifies that tapping the 😊 emoji triggers setState (selectedMood
      // is set), which hides the "Please select your mood" hint and enables
      // the Submit button.
      await tester.pumpWidget(
        const MaterialApp(home: PatientVirtualCheckIn()),
      );
      await tester.pumpAndSettle();

      // Tap the 😊 emoji (value = 5, "Great").
      await tester.tap(find.text('😊'));
      await tester.pump();

      // The hint should disappear once a mood is chosen.
      expect(
        find.text('Please select your mood to submit your check-in'),
        findsNothing,
      );
    });

    testWidgets('tapping Submit after mood selection shows snack bar', (
      tester,
    ) async {
      // Verifies that pressing Submit (with mood selected) shows the
      // mock confirmation SnackBar.
      await tester.pumpWidget(
        const MaterialApp(home: PatientVirtualCheckIn()),
      );
      await tester.pumpAndSettle();

      // Select a mood first to enable the button.
      await tester.tap(find.text('😐'));
      await tester.pump();

      // Tap Submit.
      await tester.tap(find.text('Submit Check-In'));
      await tester.pump();

      expect(find.text('Check-in submitted (mock)!'), findsOneWidget);
    });
  });
}
