import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:care_connect_app/features/activities/presentation/pages/behavioral_incident_screens.dart';

/// Wraps the screen in a minimal MaterialApp so localizations & theme are available.
Widget buildForm({int clientId = 1, String clientName = 'Alice Smith'}) {
  return MaterialApp(
    home: BehavioralIncidentFormScreen(
      clientId: clientId,
      clientName: clientName,
    ),
  );
}

void main() {
  // =========================================================
  // BehavioralIncidentFormScreen – rendering
  // =========================================================
  group('BehavioralIncidentFormScreen – rendering', () {
    testWidgets('renders AppBar with "Log Behavior" title', (tester) async {
      await tester.pumpWidget(buildForm());
      // "Log Behavior" appears in both the AppBar title and the submit button;
      // verify it appears in the AppBar specifically
      expect(
        find.descendant(of: find.byType(AppBar), matching: find.text('Log Behavior')),
        findsOneWidget,
      );
    });

    testWidgets('renders client name in AppBar subtitle', (tester) async {
      await tester.pumpWidget(buildForm(clientName: 'Bob Jones'));
      expect(find.text('Bob Jones'), findsOneWidget);
    });

    testWidgets('renders Observed Behavior text field', (tester) async {
      await tester.pumpWidget(buildForm());
      expect(find.widgetWithText(TextFormField, 'Observed Behavior'), findsOneWidget);
    });

    testWidgets('renders trigger notes text field', (tester) async {
      await tester.pumpWidget(buildForm());
      expect(
        find.widgetWithText(TextFormField, 'Possible causes or context — optional'),
        findsOneWidget,
      );
    });

    testWidgets('renders "Log Behavior" submit button', (tester) async {
      await tester.pumpWidget(buildForm());
      // The FilledButton with text 'Log Behavior' (not in AppBar)
      final buttons = find.widgetWithText(FilledButton, 'Log Behavior');
      expect(buttons, findsOneWidget);
    });

    testWidgets('renders date/time picker button', (tester) async {
      await tester.pumpWidget(buildForm());
      expect(find.byIcon(Icons.schedule), findsOneWidget);
    });

    testWidgets('renders "When did this occur?" label', (tester) async {
      await tester.pumpWidget(buildForm());
      expect(find.text('When did this occur?'), findsOneWidget);
    });
  });

  // =========================================================
  // BehavioralIncidentFormScreen – form validation
  // =========================================================
  group('BehavioralIncidentFormScreen – form validation', () {
    testWidgets('shows validation error when submitting with empty observed behavior', (tester) async {
      await tester.pumpWidget(buildForm());

      // Tap the submit button without filling in the behavior field
      await tester.tap(find.widgetWithText(FilledButton, 'Log Behavior'));
      await tester.pump();

      expect(
        find.text('Please describe the observed behavior'),
        findsOneWidget,
      );
    });

    testWidgets('does not show validation error when observed behavior is filled', (tester) async {
      await tester.pumpWidget(buildForm());

      await tester.enterText(
        find.widgetWithText(TextFormField, 'Observed Behavior'),
        'Client was hitting the walls repeatedly',
      );
      await tester.pump();

      // Verify the error text is NOT present before submitting
      expect(find.text('Please describe the observed behavior'), findsNothing);
    });

    testWidgets('submit button is enabled by default', (tester) async {
      await tester.pumpWidget(buildForm());
      final button = tester.widget<FilledButton>(
        find.widgetWithText(FilledButton, 'Log Behavior'),
      );
      expect(button.onPressed, isNotNull);
    });

    testWidgets('behavior text field accepts multiline input', (tester) async {
      await tester.pumpWidget(buildForm());

      const text = 'Client was agitated.\nRefused to cooperate.\nHitting walls.';
      await tester.enterText(
        find.widgetWithText(TextFormField, 'Observed Behavior'),
        text,
      );
      await tester.pump();

      expect(find.text(text), findsOneWidget);
    });

    testWidgets('trigger notes field accepts optional text', (tester) async {
      await tester.pumpWidget(buildForm());

      await tester.enterText(
        find.widgetWithText(TextFormField, 'Possible causes or context — optional'),
        'Before dinner',
      );
      await tester.pump();

      expect(find.text('Before dinner'), findsOneWidget);
    });
  });

  // =========================================================
  // BehavioralIncidentHistoryScreen – rendering
  // =========================================================
  group('BehavioralIncidentHistoryScreen – rendering', () {
    testWidgets('shows loading indicator on initial load', (tester) async {
      await tester.pumpWidget(MaterialApp(
        home: BehavioralIncidentHistoryScreen(
          clientId: 1,
          clientName: 'Alice Smith',
        ),
      ));
      // Before async completes, loading indicator is shown
      expect(find.byType(CircularProgressIndicator), findsOneWidget);
    });

    testWidgets('renders AppBar with "Behavioral history" title', (tester) async {
      await tester.pumpWidget(MaterialApp(
        home: BehavioralIncidentHistoryScreen(
          clientId: 1,
          clientName: 'Alice Smith',
        ),
      ));
      expect(find.text('Behavioral history'), findsOneWidget);
    });

    testWidgets('renders client name in history AppBar', (tester) async {
      await tester.pumpWidget(MaterialApp(
        home: BehavioralIncidentHistoryScreen(
          clientId: 2,
          clientName: 'Charlie Brown',
        ),
      ));
      expect(find.text('Charlie Brown'), findsOneWidget);
    });
  });
}
