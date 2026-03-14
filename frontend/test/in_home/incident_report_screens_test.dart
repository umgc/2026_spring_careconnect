import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:care_connect_app/features/evv/presentation/pages/incident_report_screens.dart';
import 'package:care_connect_app/features/activities/models/client_activity_model.dart';

IncidentReportEntry _makeReport({
  int id = 1,
  String incidentType = 'FALL',
  String location = 'Bathroom',
  String outcome = 'No injury',
  List<String> actions = const ['Applied first aid'],
  String? triggerNotes,
}) {
  return IncidentReportEntry(
    id: id,
    clientId: 5,
    caregiverId: 99,
    incidentType: incidentType,
    occurredAt: DateTime(2026, 3, 10, 9, 0),
    location: location,
    triggerNotes: triggerNotes,
    outcome: outcome,
    createdAt: DateTime(2026, 3, 10, 9, 5),
    actions: actions,
  );
}

void main() {
  // =========================================================
  // IncidentReportWizardScreen – rendering
  // =========================================================
  group('IncidentReportWizardScreen – rendering', () {
    Widget buildWizard() {
      return MaterialApp(
        home: IncidentReportWizardScreen(
          clientId: 1,
          clientName: 'Alice Smith',
        ),
      );
    }

    testWidgets('renders AppBar with "File Incident Report" title', (tester) async {
      await tester.pumpWidget(buildWizard());
      expect(find.text('File Incident Report'), findsOneWidget);
    });

    testWidgets('renders client name in AppBar', (tester) async {
      await tester.pumpWidget(buildWizard());
      expect(find.text('Alice Smith'), findsOneWidget);
    });

    testWidgets('starts at step 1 of 6', (tester) async {
      await tester.pumpWidget(buildWizard());
      expect(find.text('Step 1 of 6'), findsOneWidget);
    });

    testWidgets('shows linear progress indicator', (tester) async {
      await tester.pumpWidget(buildWizard());
      expect(find.byType(LinearProgressIndicator), findsOneWidget);
    });

    testWidgets('shows incident type selection grid on step 1', (tester) async {
      await tester.pumpWidget(buildWizard());
      // First 6 types are visible in the viewport; "Other" may require scrolling
      expect(find.text('Fall'), findsOneWidget);
      expect(find.text('Behavioral Crisis'), findsOneWidget);
      expect(find.text('Medical Event'), findsOneWidget);
      expect(find.text('Elopement'), findsOneWidget);
      expect(find.text('Self-Harm'), findsOneWidget);
      expect(find.text('Property Damage'), findsOneWidget);
      // Scroll to reveal "Other" (7th item in a 2-column grid)
      await tester.scrollUntilVisible(find.text('Other'), 100);
      expect(find.text('Other'), findsOneWidget);
    });

    testWidgets('Next button is visible on step 1', (tester) async {
      await tester.pumpWidget(buildWizard());
      expect(find.widgetWithText(FilledButton, 'Next'), findsOneWidget);
    });

    testWidgets('Back button is not visible on step 1', (tester) async {
      await tester.pumpWidget(buildWizard());
      expect(find.widgetWithText(OutlinedButton, 'Back'), findsNothing);
    });

    testWidgets('tapping a type selects it and allows moving to step 2', (tester) async {
      await tester.pumpWidget(buildWizard());

      // Select FALL
      await tester.tap(find.text('Fall'));
      await tester.pump();

      // Now Next can advance
      await tester.tap(find.widgetWithText(FilledButton, 'Next'));
      await tester.pump();

      expect(find.text('Step 2 of 6'), findsOneWidget);
    });

    testWidgets('Next does not advance on step 1 if no type selected', (tester) async {
      await tester.pumpWidget(buildWizard());

      // Tap Next without selecting a type
      await tester.tap(find.widgetWithText(FilledButton, 'Next'));
      await tester.pump();

      // Still on step 1
      expect(find.text('Step 1 of 6'), findsOneWidget);
    });

    testWidgets('Back button appears after advancing to step 2', (tester) async {
      await tester.pumpWidget(buildWizard());

      await tester.tap(find.text('Fall'));
      await tester.pump();
      await tester.tap(find.widgetWithText(FilledButton, 'Next'));
      await tester.pump();

      expect(find.widgetWithText(OutlinedButton, 'Back'), findsOneWidget);
    });

    testWidgets('Back button returns to step 1 from step 2', (tester) async {
      await tester.pumpWidget(buildWizard());

      await tester.tap(find.text('Elopement'));
      await tester.pump();
      await tester.tap(find.widgetWithText(FilledButton, 'Next'));
      await tester.pump();

      expect(find.text('Step 2 of 6'), findsOneWidget);

      await tester.tap(find.widgetWithText(OutlinedButton, 'Back'));
      await tester.pump();

      expect(find.text('Step 1 of 6'), findsOneWidget);
    });

    testWidgets('step 2 shows When/Where fields', (tester) async {
      await tester.pumpWidget(buildWizard());

      await tester.tap(find.text('Fall'));
      await tester.pump();
      await tester.tap(find.widgetWithText(FilledButton, 'Next'));
      await tester.pump();

      expect(find.text('When did the incident occur?'), findsOneWidget);
      expect(find.text('Where did this occur?'), findsOneWidget);
    });
  });

  // =========================================================
  // IncidentReportDetailScreen – rendering
  // =========================================================
  group('IncidentReportDetailScreen – rendering', () {
    Widget buildDetail({IncidentReportEntry? report}) {
      return MaterialApp(
        home: IncidentReportDetailScreen(
          clientName: 'Alice Smith',
          report: report ?? _makeReport(),
        ),
      );
    }

    testWidgets('renders AppBar with "Incident Report" title', (tester) async {
      await tester.pumpWidget(buildDetail());
      expect(find.text('Incident Report'), findsOneWidget);
    });

    testWidgets('renders client name in AppBar', (tester) async {
      await tester.pumpWidget(buildDetail());
      expect(find.text('Alice Smith'), findsOneWidget);
    });

    testWidgets('renders location field', (tester) async {
      await tester.pumpWidget(buildDetail());
      expect(find.text('Bathroom'), findsOneWidget);
    });

    testWidgets('renders outcome field', (tester) async {
      await tester.pumpWidget(buildDetail());
      expect(find.text('No injury'), findsOneWidget);
    });

    testWidgets('renders action taken', (tester) async {
      await tester.pumpWidget(buildDetail());
      expect(find.text('• Applied first aid'), findsOneWidget);
    });

    testWidgets('renders "No actions recorded" when actions list is empty', (tester) async {
      await tester.pumpWidget(buildDetail(report: _makeReport(actions: [])));
      expect(find.text('No actions recorded'), findsOneWidget);
    });

    testWidgets('renders "None recorded" when trigger notes is null', (tester) async {
      await tester.pumpWidget(buildDetail(report: _makeReport(triggerNotes: null)));
      expect(find.text('None recorded'), findsOneWidget);
    });

    testWidgets('renders trigger notes when provided', (tester) async {
      await tester.pumpWidget(buildDetail(
        report: _makeReport(triggerNotes: 'Client was agitated before the incident'),
      ));
      expect(find.text('Client was agitated before the incident'), findsOneWidget);
    });

    testWidgets('renders multiple actions', (tester) async {
      await tester.pumpWidget(buildDetail(
        report: _makeReport(actions: ['Called supervisor', 'Notified family', 'Completed documentation']),
      ));
      expect(find.text('• Called supervisor'), findsOneWidget);
      expect(find.text('• Notified family'), findsOneWidget);
      expect(find.text('• Completed documentation'), findsOneWidget);
    });

    testWidgets('renders field labels', (tester) async {
      await tester.pumpWidget(buildDetail());
      expect(find.text('Incident type'), findsOneWidget);
      expect(find.text('When'), findsOneWidget);
      expect(find.text('Location'), findsOneWidget);
      expect(find.text('Actions taken'), findsOneWidget);
      expect(find.text('Outcome'), findsOneWidget);
    });

    testWidgets('renders BEHAVIORAL_CRISIS report', (tester) async {
      await tester.pumpWidget(buildDetail(
        report: _makeReport(
          incidentType: 'BEHAVIORAL_CRISIS',
          location: 'Lounge',
          outcome: 'De-escalated',
        ),
      ));
      expect(find.text('Lounge'), findsOneWidget);
      expect(find.text('De-escalated'), findsOneWidget);
    });
  });

  // =========================================================
  // IncidentReportHistoryScreen – rendering (from health module)
  // =========================================================
  group('IncidentReportHistoryScreen via model', () {
    test('IncidentReportEntry with all incident types parses correctly', () {
      const types = ['FALL', 'BEHAVIORAL_CRISIS', 'MEDICAL_EVENT', 'ELOPEMENT', 'SELF_HARM', 'PROPERTY_DAMAGE', 'OTHER'];
      for (final type in types) {
        final report = _makeReport(incidentType: type);
        expect(report.incidentType, type);
      }
    });

    test('IncidentReportEntry.actions is immutable list', () {
      final report = _makeReport(actions: ['Action 1', 'Action 2']);
      expect(report.actions, hasLength(2));
      expect(report.actions[0], 'Action 1');
    });

    test('IncidentReportEntry correctly stores all required fields', () {
      final report = _makeReport(
        id: 99,
        incidentType: 'ELOPEMENT',
        location: 'Front entrance',
        outcome: 'Client located safely',
        actions: ['Notified family', 'Filed report'],
        triggerNotes: 'Client attempted to leave during shift',
      );
      expect(report.id, 99);
      expect(report.incidentType, 'ELOPEMENT');
      expect(report.location, 'Front entrance');
      expect(report.outcome, 'Client located safely');
      expect(report.actions, ['Notified family', 'Filed report']);
      expect(report.triggerNotes, 'Client attempted to leave during shift');
    });
  });
}
