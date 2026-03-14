// Tests for WearablesScreen
// (lib/features/integrations/presentation/pages/wearables_screen.dart).
//
// Coverage strategy:
//   WearablesScreen.initState calls _loadConnectedDevices() (reads the
//   'connected_devices' key from SharedPreferences) and
//   _fetchLatestHealthData() (returns immediately when connectedDevices is
//   empty).  By seeding SharedPreferences with no 'connected_devices' key the
//   device list stays empty, _buildEmptyState() is rendered, and no Fitbit /
//   Health API calls are made.
//
//   CommonDrawer is embedded in the Scaffold, so a UserProvider must be
//   provided via ChangeNotifierProvider.
//
//   A tall surface (800×1200) is set in each test to prevent layout overflow
//   in the test viewport (the empty-state Column is taller than the default
//   test viewport height).
//
//   Branches tested (empty-devices state):
//     Scaffold renders                — widget settles without crashing.
//     "Wearables" AppBar             — page title is correct.
//     "No Wearables Connected"       — empty-state heading is shown.
//     Description text               — helper text is rendered.
//     "Add Your First Device" button — primary CTA is present.
//     "Supported Devices" heading    — card title is shown.
//     Fitbit in supported devices    — Fitbit is always listed (non-iOS/Android).
//     Refresh icon button            — AppBar action is rendered.
//     Add icon button                — AppBar add-device action is rendered.
//
//   Branches NOT tested (require native platform channels or live API):
//     _fetchFitbitData               — needs FlutterSecureStorage + Fitbit OAuth.
//     _fetchGoogleAppleHealthData    — needs Health package platform channel.
//     _buildConnectedDevicesView     — requires pre-seeded connected devices
//                                      AND successful Fitbit/Health data fetch.
//     _removeDevice                  — requires connected devices present.
//     _navigateToAddDevice           — navigates to AddDeviceScreen which has
//                                      its own platform dependencies.

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:care_connect_app/providers/user_provider.dart';
import 'package:care_connect_app/features/integrations/presentation/pages/wearables_screen.dart';

/// Wraps [child] with a UserProvider (needed by CommonDrawer) and a
/// MaterialApp.
Widget _wrap(Widget child) {
  final provider = UserProvider();
  provider.setUser(UserSession(
    id: 2,
    email: 'cg2@example.com',
    role: 'CAREGIVER',
    token: 'tok2',
  ));
  return ChangeNotifierProvider<UserProvider>.value(
    value: provider,
    child: MaterialApp(home: child),
  );
}

/// Sets a tall test viewport and registers the teardown to reset it.
/// The empty-state Column is taller than the default test viewport (800×600),
/// causing a RenderFlex overflow that the test framework treats as a failure.
Future<void> _setTallViewport(WidgetTester tester) async {
  await tester.binding.setSurfaceSize(const Size(800, 1200));
  addTearDown(() => tester.binding.setSurfaceSize(null));
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    // No 'connected_devices' key → connectedDevices list stays empty →
    // _fetchLatestHealthData returns immediately → _buildEmptyState shown.
    SharedPreferences.setMockInitialValues({});
  });

  group('WearablesScreen – empty-devices state', () {
    testWidgets('renders Scaffold without crashing', (tester) async {
      // Verifies the widget settles after initState completes with no devices.
      await _setTallViewport(tester);
      await tester.pumpWidget(_wrap(const WearablesScreen()));
      await tester.pumpAndSettle();

      expect(find.byType(Scaffold), findsOneWidget);
    });

    testWidgets('shows "Wearables" in the AppBar', (tester) async {
      // Verifies the AppBar title is "Wearables".
      await _setTallViewport(tester);
      await tester.pumpWidget(_wrap(const WearablesScreen()));
      await tester.pumpAndSettle();

      expect(find.text('Wearables'), findsOneWidget);
    });

    testWidgets('shows "No Wearables Connected" heading', (tester) async {
      // Verifies the empty-state title is rendered by _buildEmptyState.
      await _setTallViewport(tester);
      await tester.pumpWidget(_wrap(const WearablesScreen()));
      await tester.pumpAndSettle();

      expect(find.text('No Wearables Connected'), findsOneWidget);
    });

    testWidgets('shows descriptive text in empty state', (tester) async {
      // Verifies the helper / description text is rendered below the heading.
      await _setTallViewport(tester);
      await tester.pumpWidget(_wrap(const WearablesScreen()));
      await tester.pumpAndSettle();

      expect(find.textContaining('Connect wearable devices'), findsOneWidget);
    });

    testWidgets('shows "Add Your First Device" button', (tester) async {
      // Verifies the primary CTA button is rendered in the empty state.
      await _setTallViewport(tester);
      await tester.pumpWidget(_wrap(const WearablesScreen()));
      await tester.pumpAndSettle();

      expect(find.text('Add Your First Device'), findsOneWidget);
    });

    testWidgets('shows "Supported Devices" card heading', (tester) async {
      // Verifies the supported-devices card title is rendered below the CTA.
      await _setTallViewport(tester);
      await tester.pumpWidget(_wrap(const WearablesScreen()));
      await tester.pumpAndSettle();

      expect(find.text('Supported Devices'), findsOneWidget);
    });

    testWidgets('shows Fitbit in the supported devices card', (tester) async {
      // Verifies Fitbit is listed (always present regardless of platform).
      await _setTallViewport(tester);
      await tester.pumpWidget(_wrap(const WearablesScreen()));
      await tester.pumpAndSettle();

      expect(find.text('Fitbit'), findsOneWidget);
    });

    testWidgets('shows refresh icon button in AppBar', (tester) async {
      // Verifies the refresh IconButton action is rendered in the AppBar.
      await _setTallViewport(tester);
      await tester.pumpWidget(_wrap(const WearablesScreen()));
      await tester.pumpAndSettle();

      expect(find.byIcon(Icons.refresh), findsOneWidget);
    });

    testWidgets('shows add icon button in AppBar', (tester) async {
      // Verifies the add-device IconButton action is rendered in the AppBar.
      await _setTallViewport(tester);
      await tester.pumpWidget(_wrap(const WearablesScreen()));
      await tester.pumpAndSettle();

      expect(find.byIcon(Icons.add), findsAtLeastNWidgets(1));
    });
  });
}
