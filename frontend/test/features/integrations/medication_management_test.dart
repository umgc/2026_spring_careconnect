// Tests for MedicationManagementScreen
// (lib/features/integrations/presentation/pages/medication_management.dart).
//
// Coverage strategy:
//   MedicationManagementScreen loads medications from SharedPreferences in
//   initState.  CommonDrawer requires a UserProvider.
//
//   Branches tested (empty-medications state):
//     Scaffold renders                  — widget settles without crashing.
//     "No Medications Added" title      — empty-state heading is shown.
//     "Scan Medication Barcode" button  — primary scan action is present.
//     "Enter NDC Code" button           — secondary input action is present.
//     "Add Medication Manually" button  — tertiary input action is present.
//     "Medication Management" AppBar    — page title is correct.
//
//   Branches tested (pre-seeded medications state):
//     _buildMedicationList renders      — ListView replaces empty state.
//     "Current Medications" heading     — list header is shown.
//     Medication brand name shown       — brandName field rendered.
//     Medication generic name shown     — genericName field rendered.
//     Strength field shown              — strength label rendered.
//     Dosage + frequency conditional    — rendered when non-empty.
//     Time to take conditional          — rendered when non-empty.
//     Take with conditional             — rendered when non-empty.
//     Do not take with conditional      — rendered when non-empty.
//     Duration (date range) conditional — rendered when dates present.
//     PopupMenu present                 — trailing menu button is rendered.
//     PopupMenu shows Edit/Remove       — opens PopupMenuButton.
//     Remove via PopupMenu              — calls _removeMedication → saves.
//
//   Branches not tested (require native features or live network):
//     Barcode scanner                   — MobileScanner uses camera platform channel.
//     FDA API lookup                    — requires live HTTPS network call.
//     PDF generation / share            — requires path_provider + share_plus.
//     Edit dialog                       — navigates to complex edit sheet.

import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:care_connect_app/providers/user_provider.dart';
import 'package:care_connect_app/features/integrations/presentation/pages/medication_management.dart';

/// Wraps [child] with a UserProvider (needed by the embedded CommonDrawer).
Widget _wrap(Widget child) {
  final provider = UserProvider();
  provider.setUser(UserSession(
    id: 1,
    email: 'cg@example.com',
    role: 'CAREGIVER',
    token: 'tok',
  ));
  return ChangeNotifierProvider<UserProvider>.value(
    value: provider,
    child: MaterialApp(home: child),
  );
}

/// A comprehensive medication fixture that exercises every conditional branch
/// in _buildMedicationList (dosage, timeToTake, takeWith, doNotTakeWith,
/// startDate, endDate) and _formatDateRange / _formatDate.
final _medicationFull = {
  'id': 'med-001',
  'brandName': 'Aspirin',
  'genericName': 'Acetylsalicylic acid',
  'strength': '100mg',
  'dosage': '1 tablet',
  'frequency': 'Daily',
  'timeToTake': 'Morning',
  'takeWith': 'Water',
  'doNotTakeWith': 'Alcohol',
  'startDate': '2024-01-01',
  'endDate': '2024-12-31',
};

/// A minimal medication (no optional fields) to cover the null-fallback branches.
final _medicationMinimal = {
  'id': 'med-002',
  'brandName': 'Ibuprofen',
  'genericName': null,
  'strength': null,
};

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  // ── empty-medications state ───────────────────────────────────────────────

  group('MedicationManagementScreen – empty state', () {
    setUp(() {
      SharedPreferences.setMockInitialValues({});
    });

    testWidgets('renders Scaffold after medications load', (tester) async {
      // Verifies the widget settles without crashing once loading finishes.
      await tester.pumpWidget(_wrap(const MedicationManagementScreen()));
      await tester.pumpAndSettle();

      expect(find.byType(Scaffold), findsOneWidget);
    });

    testWidgets('shows "No Medications Added" when no medications are saved', (
      tester,
    ) async {
      await tester.pumpWidget(_wrap(const MedicationManagementScreen()));
      await tester.pumpAndSettle();

      expect(find.text('No Medications Added'), findsOneWidget);
    });

    testWidgets('shows "Medication Management" in the AppBar', (tester) async {
      await tester.pumpWidget(_wrap(const MedicationManagementScreen()));
      await tester.pumpAndSettle();

      expect(find.text('Medication Management'), findsOneWidget);
    });

    testWidgets('shows "Scan Medication Barcode" button', (tester) async {
      await tester.pumpWidget(_wrap(const MedicationManagementScreen()));
      await tester.pumpAndSettle();

      expect(find.text('Scan Medication Barcode'), findsOneWidget);
    });

    testWidgets('shows "Enter NDC Code" button', (tester) async {
      await tester.pumpWidget(_wrap(const MedicationManagementScreen()));
      await tester.pumpAndSettle();

      expect(find.textContaining('Enter NDC Code'), findsOneWidget);
    });

    testWidgets('shows "Add Medication Manually" button', (tester) async {
      await tester.pumpWidget(_wrap(const MedicationManagementScreen()));
      await tester.pumpAndSettle();

      expect(find.text('Add Medication Manually'), findsOneWidget);
    });
  });

  // ── pre-seeded medications state ─────────────────────────────────────────

  group('MedicationManagementScreen – with pre-seeded medications', () {
    setUp(() {
      // Pre-seed one comprehensive medication that exercises every conditional
      // field in _buildMedicationList.
      SharedPreferences.setMockInitialValues({
        'medications': jsonEncode([_medicationFull]),
      });
    });

    testWidgets('renders "Current Medications" heading', (tester) async {
      // Verifies _buildMedicationList is rendered instead of the empty state.
      await tester.pumpWidget(_wrap(const MedicationManagementScreen()));
      await tester.pumpAndSettle();

      expect(find.text('Current Medications'), findsOneWidget);
    });

    testWidgets('shows the medication brand name', (tester) async {
      // Verifies the brandName field is rendered in the ListTile title.
      await tester.pumpWidget(_wrap(const MedicationManagementScreen()));
      await tester.pumpAndSettle();

      expect(find.text('Aspirin'), findsOneWidget);
    });

    testWidgets('shows the medication generic name', (tester) async {
      // Verifies the genericName field is rendered in the ListTile subtitle.
      await tester.pumpWidget(_wrap(const MedicationManagementScreen()));
      await tester.pumpAndSettle();

      expect(find.text('Acetylsalicylic acid'), findsOneWidget);
    });

    testWidgets('shows strength label', (tester) async {
      // Verifies the strength field is rendered.
      await tester.pumpWidget(_wrap(const MedicationManagementScreen()));
      await tester.pumpAndSettle();

      expect(find.textContaining('Strength: 100mg'), findsOneWidget);
    });

    testWidgets('shows dosage and frequency conditional row', (tester) async {
      // Verifies the dosage/frequency conditional (medication['dosage'].isNotEmpty).
      await tester.pumpWidget(_wrap(const MedicationManagementScreen()));
      await tester.pumpAndSettle();

      expect(find.textContaining('Dosage:'), findsOneWidget);
      expect(find.textContaining('Daily'), findsOneWidget);
    });

    testWidgets('shows time-to-take conditional row', (tester) async {
      // Verifies the timeToTake conditional branch.
      await tester.pumpWidget(_wrap(const MedicationManagementScreen()));
      await tester.pumpAndSettle();

      expect(find.textContaining('Time to take:'), findsOneWidget);
      expect(find.textContaining('Morning'), findsOneWidget);
    });

    testWidgets('shows take-with conditional row', (tester) async {
      // Verifies the takeWith conditional branch.
      await tester.pumpWidget(_wrap(const MedicationManagementScreen()));
      await tester.pumpAndSettle();

      expect(find.textContaining('Take with:'), findsOneWidget);
    });

    testWidgets('shows do-not-take-with conditional row', (tester) async {
      // Verifies the doNotTakeWith conditional branch.
      await tester.pumpWidget(_wrap(const MedicationManagementScreen()));
      await tester.pumpAndSettle();

      expect(find.textContaining('Do not take with:'), findsOneWidget);
    });

    testWidgets('shows duration date range conditional row', (tester) async {
      // Verifies the startDate/endDate conditional calling _formatDateRange.
      await tester.pumpWidget(_wrap(const MedicationManagementScreen()));
      await tester.pumpAndSettle();

      expect(find.textContaining('Duration:'), findsOneWidget);
    });

    testWidgets('shows PopupMenuButton for each medication', (tester) async {
      // Verifies the trailing PopupMenuButton is rendered.
      await tester.pumpWidget(_wrap(const MedicationManagementScreen()));
      await tester.pumpAndSettle();

      expect(find.byType(PopupMenuButton<String>), findsOneWidget);
    });

    testWidgets('PopupMenu shows Edit and Remove items', (tester) async {
      // Verifies the PopupMenuButton itemBuilder returns Edit and Remove.
      await tester.pumpWidget(_wrap(const MedicationManagementScreen()));
      await tester.pumpAndSettle();

      // Open the PopupMenu.
      await tester.tap(find.byType(PopupMenuButton<String>));
      await tester.pumpAndSettle();

      expect(find.text('Edit'), findsOneWidget);
      expect(find.text('Remove'), findsOneWidget);
    });

    testWidgets('tapping Remove deletes the medication from the list', (
      tester,
    ) async {
      // Verifies _removeMedication removes the item and calls _saveMedications.
      // Flow: PopupMenu "Remove" → confirmation AlertDialog → "Remove" button.
      await tester.pumpWidget(_wrap(const MedicationManagementScreen()));
      await tester.pumpAndSettle();

      expect(find.text('Aspirin'), findsOneWidget);

      // Open PopupMenu.
      await tester.tap(find.byType(PopupMenuButton<String>));
      await tester.pumpAndSettle();

      // Tap "Remove" in the popup — this opens a confirmation AlertDialog.
      await tester.tap(find.text('Remove'));
      await tester.pumpAndSettle();

      // The confirmation dialog has a second "Remove" ElevatedButton — tap it.
      expect(find.text('Remove Medication'), findsOneWidget); // dialog title
      await tester.tap(find.text('Remove').last); // tap the dialog's Remove button
      await tester.pumpAndSettle();

      // After removal the empty state is shown again.
      expect(find.text('No Medications Added'), findsOneWidget);
    });

    testWidgets('shows medication count in header', (tester) async {
      // Verifies the "N medications being managed" subtitle.
      await tester.pumpWidget(_wrap(const MedicationManagementScreen()));
      await tester.pumpAndSettle();

      expect(
        find.textContaining('medications being managed'),
        findsOneWidget,
      );
    });
  });

  // ── minimal medication (null-fallback branches) ───────────────────────────

  group('MedicationManagementScreen – minimal medication (null fallbacks)', () {
    setUp(() {
      SharedPreferences.setMockInitialValues({
        'medications': jsonEncode([_medicationMinimal]),
      });
    });

    testWidgets('renders brand name with null generic fallback', (
      tester,
    ) async {
      // Verifies the "Unknown Generic" fallback when genericName is null.
      await tester.pumpWidget(_wrap(const MedicationManagementScreen()));
      await tester.pumpAndSettle();

      expect(find.text('Ibuprofen'), findsOneWidget);
      expect(find.text('Unknown Generic'), findsOneWidget);
    });

    testWidgets('renders "Not specified" when strength is null', (
      tester,
    ) async {
      // Verifies the strength null-fallback branch.
      await tester.pumpWidget(_wrap(const MedicationManagementScreen()));
      await tester.pumpAndSettle();

      expect(find.textContaining('Not specified'), findsOneWidget);
    });
  });
}
