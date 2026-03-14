// Tests for SmartDevicesPage
// (lib/features/integrations/presentation/pages/smart_devices.dart).
//
// Coverage strategy:
//   SmartDevicesPage calls ProfileService.getCurrentUserProfile() in initState.
//   In the test environment the HTTP call hangs in fake async; pumpAndSettle
//   would time out waiting for it to complete.  Only the loading state —
//   captured with pump() before the API resolves — is reliably testable.
//
//   Loading state (before API resolves):
//     pump() (no settle) leaves the widget in the loading state (isLoading ==
//     true).  The build method returns a plain Scaffold with a centred
//     CircularProgressIndicator.
//
//   Branches NOT tested (require live API or native platform channels):
//     Error state            — pumpAndSettle times out because the HTTP call
//       hangs in fake async; runAsync would trigger connectivity_plus native
//       plugin (MissingPluginException).
//     Normal UI              — Alexa / Google Nest cards rendered only when
//       ProfileService succeeds; unavailable in the test environment.
//     _linkAlexaAccount /
//     _unlinkAlexaAccount    — require canLaunchUrl + native URL launcher.

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';

import 'package:care_connect_app/providers/user_provider.dart';
import 'package:care_connect_app/features/integrations/presentation/pages/smart_devices.dart';

/// Wraps [child] with a UserProvider and a MaterialApp.
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

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('SmartDevicesPage – loading state', () {
    testWidgets('renders Scaffold without crashing', (tester) async {
      // Verifies the loading-state Scaffold is present immediately after
      // pumpWidget (before the async API call has a chance to resolve).
      await tester.pumpWidget(_wrap(const SmartDevicesPage()));
      await tester.pump();

      expect(find.byType(Scaffold), findsOneWidget);
    });

    testWidgets('shows CircularProgressIndicator while API is in flight', (
      tester,
    ) async {
      // pump() without settle leaves isLoading == true; the build method
      // returns the loading state with a CircularProgressIndicator.
      await tester.pumpWidget(_wrap(const SmartDevicesPage()));
      await tester.pump();

      expect(find.byType(CircularProgressIndicator), findsOneWidget);
    });

    testWidgets('loading state has no AppBar title text', (tester) async {
      // Verifies the loading Scaffold is a plain body-only Scaffold (no AppBar
      // title) — the title "Smart Devices" only appears in the error/normal
      // state builds.
      await tester.pumpWidget(_wrap(const SmartDevicesPage()));
      await tester.pump();

      expect(find.text('Smart Devices'), findsNothing);
    });
  });
}
