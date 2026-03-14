// Tests for SubscriptionService
// (lib/services/subscription_service.dart).
//
// Coverage strategy:
//   showPremiumRequiredDialog — fully testable without network I/O.
//   hasPremiumSubscription    — null-user and non-caregiver (patient) branches
//                               return immediately without a network call.
//   canUseAIAssistant         — patient branch and other-role branch also
//                               return immediately without network I/O.
//   canUseVideoCalls          — same fast branches as canUseAIAssistant.
//   checkPremiumAccessWithDialog — non-caregiver branch returns true fast.
//   _navigateToSubscriptionPage  — exercised by tapping "Upgrade Now".
//
//   Branches tested (showPremiumRequiredDialog):
//     dialog shown — 'Premium Feature' title and feature name are displayed.
//     'Maybe Later' — tapping the button closes the dialog.
//     feature name  — the supplied featureName string appears in the content.
//     'Upgrade Now' — tapping closes dialog and navigates to /subscription.
//
//   Branches tested (hasPremiumSubscription):
//     user == null       → returns false immediately.
//     user is not caregiver → returns true immediately.
//
//   Branches tested (canUseAIAssistant):
//     user is patient    → returns true immediately.
//     user is other role → returns true immediately.
//
//   Branches tested (canUseVideoCalls):
//     user is patient    → returns true immediately.
//     user is other role → returns true immediately.
//
//   Branches tested (checkPremiumAccessWithDialog):
//     user is not caregiver → returns true without network call.

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:care_connect_app/providers/user_provider.dart';
import 'package:care_connect_app/services/subscription_service.dart';

// ─── Provider helpers ─────────────────────────────────────────────────────────

/// Wraps [child] with a UserProvider that has NO user (logged-out state).
Widget _withNullUser(Widget child) {
  return ChangeNotifierProvider<UserProvider>(
    create: (_) => UserProvider(),
    child: MaterialApp(
      onUnknownRoute: (_) => MaterialPageRoute(
        builder: (_) => const Scaffold(body: Text('unknown')),
      ),
      home: child,
    ),
  );
}

/// Creates a UserProvider seeded with a PATIENT session.
Widget _withPatientUser(Widget child) {
  final provider = UserProvider();
  provider.setUser(UserSession(
    id: 5,
    email: 'patient@example.com',
    role: 'PATIENT',
    token: 'tok',
    name: 'Test Patient',
  ));
  return ChangeNotifierProvider<UserProvider>.value(
    value: provider,
    child: MaterialApp(
      onUnknownRoute: (_) => MaterialPageRoute(
        builder: (_) => const Scaffold(body: Text('unknown')),
      ),
      home: child,
    ),
  );
}

/// Creates a UserProvider seeded with an ADMIN session (not caregiver, not patient).
Widget _withAdminUser(Widget child) {
  final provider = UserProvider();
  provider.setUser(UserSession(
    id: 99,
    email: 'admin@example.com',
    role: 'ADMIN',
    token: 'tok',
    name: 'Test Admin',
  ));
  return ChangeNotifierProvider<UserProvider>.value(
    value: provider,
    child: MaterialApp(
      onUnknownRoute: (_) => MaterialPageRoute(
        builder: (_) => const Scaffold(body: Text('unknown')),
      ),
      home: child,
    ),
  );
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    SharedPreferences.setMockInitialValues({});
  });

  // ─── showPremiumRequiredDialog ────────────────────────────────────────────

  group('SubscriptionService.showPremiumRequiredDialog', () {
    testWidgets('shows an AlertDialog with "Premium Feature" title', (
      tester,
    ) async {
      // Verifies the dialog is displayed when showPremiumRequiredDialog is
      // called and that the title renders correctly.
      await tester.pumpWidget(
        MaterialApp(
          home: Builder(
            builder: (ctx) => ElevatedButton(
              onPressed: () =>
                  SubscriptionService.showPremiumRequiredDialog(ctx, 'AI Chat'),
              child: const Text('Open'),
            ),
          ),
        ),
      );

      // Tap the button to trigger the dialog.
      await tester.tap(find.text('Open'));
      await tester.pumpAndSettle();

      expect(find.text('Premium Feature'), findsOneWidget);
    });

    testWidgets('includes the supplied featureName in the dialog content', (
      tester,
    ) async {
      // Verifies that the featureName is interpolated into the body text.
      await tester.pumpWidget(
        MaterialApp(
          home: Builder(
            builder: (ctx) => ElevatedButton(
              onPressed: () => SubscriptionService.showPremiumRequiredDialog(
                ctx,
                'Voice & Video Calls',
              ),
              child: const Text('Open'),
            ),
          ),
        ),
      );

      await tester.tap(find.text('Open'));
      await tester.pumpAndSettle();

      expect(
        find.textContaining('Voice & Video Calls'),
        findsWidgets,
      );
    });

    testWidgets('"Maybe Later" button closes the dialog', (tester) async {
      // Verifies that tapping "Maybe Later" dismisses the AlertDialog.
      await tester.pumpWidget(
        MaterialApp(
          home: Builder(
            builder: (ctx) => ElevatedButton(
              onPressed: () => SubscriptionService.showPremiumRequiredDialog(
                ctx,
                'Advanced Analytics',
              ),
              child: const Text('Open'),
            ),
          ),
        ),
      );

      await tester.tap(find.text('Open'));
      await tester.pumpAndSettle();

      // Dialog should be present.
      expect(find.text('Maybe Later'), findsOneWidget);

      // Tap "Maybe Later" to close.
      await tester.tap(find.text('Maybe Later'));
      await tester.pumpAndSettle();

      // Dialog should be gone.
      expect(find.text('Premium Feature'), findsNothing);
    });

    testWidgets('dialog lists expected premium feature bullets', (
      tester,
    ) async {
      // Verifies that the dialog content includes the known feature list.
      await tester.pumpWidget(
        MaterialApp(
          home: Builder(
            builder: (ctx) => ElevatedButton(
              onPressed: () => SubscriptionService.showPremiumRequiredDialog(
                ctx,
                'Feature X',
              ),
              child: const Text('Open'),
            ),
          ),
        ),
      );

      await tester.tap(find.text('Open'));
      await tester.pumpAndSettle();

      expect(find.text('• AI Health Assistant'), findsOneWidget);
      expect(find.text('• Voice & Video Calls'), findsOneWidget);
      expect(find.text('• Advanced Analytics'), findsOneWidget);
      expect(find.text('• Priority Support'), findsOneWidget);
    });

    testWidgets('"Upgrade Now" button closes dialog and navigates', (
      tester,
    ) async {
      // Verifies that tapping "Upgrade Now" dismisses the dialog and calls
      // _navigateToSubscriptionPage (covers Navigator.pushNamed branch).
      await tester.pumpWidget(
        MaterialApp(
          onUnknownRoute: (_) => MaterialPageRoute(
            builder: (_) => const Scaffold(body: Text('subscription page')),
          ),
          home: Builder(
            builder: (ctx) => ElevatedButton(
              onPressed: () => SubscriptionService.showPremiumRequiredDialog(
                ctx,
                'Premium Feature',
              ),
              child: const Text('Open'),
            ),
          ),
        ),
      );

      await tester.tap(find.text('Open'));
      await tester.pumpAndSettle();

      // Tap "Upgrade Now" to close the dialog and navigate.
      await tester.tap(find.text('Upgrade Now'));
      await tester.pumpAndSettle();

      // Dialog should be closed.
      expect(find.text('Premium Feature'), findsNothing);
    });
  });

  // ─── hasPremiumSubscription ───────────────────────────────────────────────

  group('SubscriptionService.hasPremiumSubscription', () {
    testWidgets('returns false when user is null', (tester) async {
      // Verifies the null-user guard returns false without a network call.
      bool? result;
      await tester.pumpWidget(
        _withNullUser(
          Builder(
            builder: (ctx) => ElevatedButton(
              onPressed: () async {
                result = await SubscriptionService.hasPremiumSubscription(ctx);
              },
              child: const Text('Test'),
            ),
          ),
        ),
      );

      await tester.tap(find.text('Test'));
      await tester.pump();

      expect(result, isFalse);
    });

    testWidgets('returns true for a non-caregiver (patient) user', (
      tester,
    ) async {
      // Verifies that patients bypass the subscription check and get true.
      bool? result;
      await tester.pumpWidget(
        _withPatientUser(
          Builder(
            builder: (ctx) => ElevatedButton(
              onPressed: () async {
                result = await SubscriptionService.hasPremiumSubscription(ctx);
              },
              child: const Text('Test'),
            ),
          ),
        ),
      );

      await tester.tap(find.text('Test'));
      await tester.pump();

      expect(result, isTrue);
    });
  });

  // ─── canUseAIAssistant ────────────────────────────────────────────────────

  group('SubscriptionService.canUseAIAssistant', () {
    testWidgets('returns true for patient user', (tester) async {
      // Verifies the isPatient == true fast path returns true immediately.
      bool? result;
      await tester.pumpWidget(
        _withPatientUser(
          Builder(
            builder: (ctx) => ElevatedButton(
              onPressed: () async {
                result = await SubscriptionService.canUseAIAssistant(ctx);
              },
              child: const Text('Test'),
            ),
          ),
        ),
      );

      await tester.tap(find.text('Test'));
      await tester.pump();

      expect(result, isTrue);
    });

    testWidgets('returns true for other-role (admin) user', (tester) async {
      // Verifies the final "other roles have access" return path is reached
      // for a user that is neither a patient nor a caregiver.
      bool? result;
      await tester.pumpWidget(
        _withAdminUser(
          Builder(
            builder: (ctx) => ElevatedButton(
              onPressed: () async {
                result = await SubscriptionService.canUseAIAssistant(ctx);
              },
              child: const Text('Test'),
            ),
          ),
        ),
      );

      await tester.tap(find.text('Test'));
      await tester.pump();

      expect(result, isTrue);
    });
  });

  // ─── canUseVideoCalls ─────────────────────────────────────────────────────

  group('SubscriptionService.canUseVideoCalls', () {
    testWidgets('returns true for patient user', (tester) async {
      // Verifies the isPatient == true fast path for video calls.
      bool? result;
      await tester.pumpWidget(
        _withPatientUser(
          Builder(
            builder: (ctx) => ElevatedButton(
              onPressed: () async {
                result = await SubscriptionService.canUseVideoCalls(ctx);
              },
              child: const Text('Test'),
            ),
          ),
        ),
      );

      await tester.tap(find.text('Test'));
      await tester.pump();

      expect(result, isTrue);
    });

    testWidgets('returns true for other-role (admin) user', (tester) async {
      // Verifies the other-roles fast path for video calls.
      bool? result;
      await tester.pumpWidget(
        _withAdminUser(
          Builder(
            builder: (ctx) => ElevatedButton(
              onPressed: () async {
                result = await SubscriptionService.canUseVideoCalls(ctx);
              },
              child: const Text('Test'),
            ),
          ),
        ),
      );

      await tester.tap(find.text('Test'));
      await tester.pump();

      expect(result, isTrue);
    });
  });

  // ─── checkPremiumAccessWithDialog ─────────────────────────────────────────

  group('SubscriptionService.checkPremiumAccessWithDialog', () {
    testWidgets('returns true immediately for a non-caregiver user', (
      tester,
    ) async {
      // Verifies that non-caregivers bypass the premium check and return true.
      bool? result;
      await tester.pumpWidget(
        _withPatientUser(
          Builder(
            builder: (ctx) => ElevatedButton(
              onPressed: () async {
                result = await SubscriptionService.checkPremiumAccessWithDialog(
                  ctx,
                  'Test Feature',
                );
              },
              child: const Text('Test'),
            ),
          ),
        ),
      );

      await tester.tap(find.text('Test'));
      await tester.pump();

      expect(result, isTrue);
    });

  });
}
