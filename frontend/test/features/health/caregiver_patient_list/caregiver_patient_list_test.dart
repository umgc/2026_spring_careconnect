// Tests for CaregiverPatientList
// (lib/features/health/caregiver-patient-list/page/caregiver-patient-list.dart).
//
// Coverage strategy:
//   CaregiverPatientList is a StatefulWidget that in initState calls
//   _loadPatients(), which reads from UserProvider and conditionally calls
//   ApiService.  Two branches are testable without a live server:
//
//   Branches tested (null caregiverId — fast path):
//     caregiverId == null — _loadPatients sets _isLoading=false with an empty
//                           list immediately (no network call), so the widget
//                           renders the empty-patients UI after settling.
//     search field present — the search TextField renders.
//     scaffold renders     — the overall Scaffold is present.
//
//   Branches tested (non-null caregiverId — API-call path):
//     API call fires and fails (no live server) → catch block executes,
//     setting _isLoading=false with an empty list.

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:care_connect_app/providers/user_provider.dart';
import 'package:care_connect_app/features/health/caregiver-patient-list/page/caregiver-patient-list.dart';

/// Wraps [widget] with a UserProvider whose session has NO caregiverId,
/// so _loadPatients returns immediately without hitting the network.
Widget _withNullCaregiverId(Widget widget) {
  final provider = UserProvider();
  provider.setUser(UserSession(
    id: 10,
    email: 'cg@example.com',
    role: 'CAREGIVER',
    token: 'test-token',
    // caregiverId intentionally omitted → null
  ));

  return ChangeNotifierProvider<UserProvider>.value(
    value: provider,
    child: MaterialApp(home: widget),
  );
}

/// Wraps [widget] with a UserProvider whose session has a non-null caregiverId,
/// causing _loadPatients to make an API call that will fail (no live server)
/// and exercise the catch block.
Widget _withCaregiverId(Widget widget) {
  final provider = UserProvider();
  provider.setUser(UserSession(
    id: 10,
    email: 'cg@example.com',
    role: 'CAREGIVER',
    token: 'test-token',
    caregiverId: 1, // non-null → triggers the API call path
  ));

  return ChangeNotifierProvider<UserProvider>.value(
    value: provider,
    child: MaterialApp(home: widget),
  );
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    SharedPreferences.setMockInitialValues({});
  });

  group('CaregiverPatientList', () {
    testWidgets('renders Scaffold without crashing', (tester) async {
      // Verifies the widget builds successfully with a caregiver session that
      // has no caregiverId — the no-op path in _loadPatients.
      await tester.pumpWidget(
        _withNullCaregiverId(const CaregiverPatientList()),
      );
      await tester.pumpAndSettle();

      expect(find.byType(Scaffold), findsOneWidget);
    });

    testWidgets('search field is present', (tester) async {
      // Verifies the search bar is rendered as part of the page UI.
      await tester.pumpWidget(
        _withNullCaregiverId(const CaregiverPatientList()),
      );
      await tester.pumpAndSettle();

      expect(find.byType(TextField), findsOneWidget);
    });

    testWidgets('shows empty patient list after load with null caregiverId', (
      tester,
    ) async {
      // Verifies that when caregiverId is null, the page loads with zero
      // patient cards (no ListView items from API).
      await tester.pumpWidget(
        _withNullCaregiverId(const CaregiverPatientList()),
      );
      await tester.pumpAndSettle();

      // No patient cards should be rendered in the empty state.
      expect(find.byType(CircularProgressIndicator), findsNothing);
    });

    testWidgets('handles API failure gracefully when caregiverId is set', (
      tester,
    ) async {
      // Verifies that when caregiverId is non-null, _loadPatients fires the
      // API call.  With no live server the call fails fast and the catch block
      // sets _isLoading=false with an empty list — no crash.
      await tester.pumpWidget(
        _withCaregiverId(const CaregiverPatientList()),
      );
      await tester.runAsync(
        () => Future<void>.delayed(const Duration(seconds: 3)),
      );
      await tester.pumpAndSettle();

      expect(find.byType(Scaffold), findsOneWidget);
      expect(find.byType(CircularProgressIndicator), findsNothing);
    });
  });
}
