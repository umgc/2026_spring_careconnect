// Tests for ProfilePage — user profile view with role-specific sections.
// With null user → "User not found" error state.
// With CAREGIVER user (caregiverId = null) → no API call, form shown directly.
// With PATIENT user (patientId = null) → no API call, medical section shown.

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:care_connect_app/pages/profile_page.dart';
import 'package:care_connect_app/providers/user_provider.dart';

Widget _wrap({UserSession? user}) {
  // ProfilePage uses Provider.of<UserProvider> for avatar and role section.
  final provider = UserProvider();
  if (user != null) provider.setUser(user);
  return ChangeNotifierProvider<UserProvider>.value(
    value: provider,
    child: const MaterialApp(home: ProfilePage()),
  );
}

final _caregiverUser = UserSession(
  id: 1,
  email: 'caregiver@test.com',
  role: 'caregiver',
  token: 'token',
  // caregiverId = null → no API call → form rendered directly
);

final _patientUser = UserSession(
  id: 2,
  email: 'patient@test.com',
  role: 'patient',
  token: 'token',
  // patientId = null → no API call → form rendered directly
);

void main() {
  setUp(() {
    SharedPreferences.setMockInitialValues({});
  });

  group('ProfilePage – null user (error state)', () {
    testWidgets('shows User not found error text', (tester) async {
      // With no user in UserProvider, _loadUserProfile sets _error = "User not found".
      await tester.pumpWidget(_wrap());
      await tester.pumpAndSettle();
      expect(find.text('User not found'), findsOneWidget);
    });

    testWidgets('shows Retry button', (tester) async {
      // An ElevatedButton labeled "Retry" is shown in the error state.
      await tester.pumpWidget(_wrap());
      await tester.pumpAndSettle();
      expect(find.text('Retry'), findsOneWidget);
    });

    testWidgets('shows error icon in error state', (tester) async {
      // The error state shows an error_outline icon.
      await tester.pumpWidget(_wrap());
      await tester.pumpAndSettle();
      expect(find.byIcon(Icons.error_outline), findsOneWidget);
    });
  });

  group('ProfilePage – CAREGIVER user (caregiverId = null)', () {
    testWidgets('shows Basic Information section after loading', (tester) async {
      // With caregiverId = null, no HTTP call is made; profile form renders.
      await tester.pumpWidget(_wrap(user: _caregiverUser));
      await tester.pumpAndSettle();
      expect(find.text('Basic Information'), findsOneWidget);
    });

    testWidgets('shows Contact Information section', (tester) async {
      // The contact section with phone number field is rendered.
      await tester.pumpWidget(_wrap(user: _caregiverUser));
      await tester.pumpAndSettle();
      expect(find.text('Contact Information'), findsOneWidget);
    });

    testWidgets('shows Address section', (tester) async {
      // The address section with street, city, state, zip, country is rendered.
      await tester.pumpWidget(_wrap(user: _caregiverUser));
      await tester.pumpAndSettle();
      expect(find.text('Address'), findsOneWidget);
    });

    testWidgets('shows Professional Information section for caregiver',
        (tester) async {
      // The caregiver-specific section with specialization/org/license is shown.
      await tester.pumpWidget(_wrap(user: _caregiverUser));
      await tester.pumpAndSettle();
      expect(find.text('Professional Information'), findsOneWidget);
    });

    testWidgets('shows Full Name field', (tester) async {
      // The Full Name TextFormField is visible in the Basic Information card.
      await tester.pumpWidget(_wrap(user: _caregiverUser));
      await tester.pumpAndSettle();
      expect(find.text('Full Name'), findsOneWidget);
    });

    testWidgets('shows Email field', (tester) async {
      // The Email TextFormField is visible in the Basic Information card.
      await tester.pumpWidget(_wrap(user: _caregiverUser));
      await tester.pumpAndSettle();
      expect(find.text('Email'), findsOneWidget);
    });

    testWidgets('shows Phone Number field', (tester) async {
      // The Phone Number TextFormField is visible in Contact Information.
      await tester.pumpWidget(_wrap(user: _caregiverUser));
      await tester.pumpAndSettle();
      expect(find.text('Phone Number'), findsOneWidget);
    });

    testWidgets('shows Specialization field for caregiver', (tester) async {
      // The Specialization field is in the Professional Information card.
      await tester.pumpWidget(_wrap(user: _caregiverUser));
      await tester.pumpAndSettle();
      expect(find.text('Specialization'), findsOneWidget);
    });

    testWidgets('shows edit icon button when not editing', (tester) async {
      // The edit (pencil) icon button is shown when not in edit mode.
      await tester.pumpWidget(_wrap(user: _caregiverUser));
      await tester.pumpAndSettle();
      expect(find.byIcon(Icons.edit), findsOneWidget);
    });

    testWidgets('tapping edit button shows cancel and save icons', (tester) async {
      // Tapping the edit icon sets _isEditing = true, showing cancel and save.
      await tester.pumpWidget(_wrap(user: _caregiverUser));
      await tester.pumpAndSettle();
      await tester.tap(find.byIcon(Icons.edit));
      await tester.pump();
      expect(find.byIcon(Icons.cancel), findsOneWidget);
      expect(find.byIcon(Icons.save), findsOneWidget);
    });

    testWidgets('tapping cancel returns to view mode', (tester) async {
      // After entering edit mode, tapping cancel sets _isEditing = false.
      await tester.pumpWidget(_wrap(user: _caregiverUser));
      await tester.pumpAndSettle();
      await tester.tap(find.byIcon(Icons.edit));
      await tester.pump();
      await tester.tap(find.byIcon(Icons.cancel));
      await tester.pump();
      expect(find.byIcon(Icons.edit), findsOneWidget);
    });

    testWidgets('shows caregiver role Chip', (tester) async {
      // A Chip displays the user's role name in the profile picture section.
      await tester.pumpWidget(_wrap(user: _caregiverUser));
      await tester.pumpAndSettle();
      expect(find.byType(Chip), findsOneWidget);
    });
  });

  group('ProfilePage – PATIENT user (patientId = null)', () {
    testWidgets('shows Medical Information section for patient', (tester) async {
      // The patient-specific section with emergency contact and notes is shown.
      await tester.pumpWidget(_wrap(user: _patientUser));
      await tester.pumpAndSettle();
      expect(find.text('Medical Information'), findsOneWidget);
    });

    testWidgets('shows Emergency Contact field for patient', (tester) async {
      // The Emergency Contact field is visible in the Medical Information card.
      await tester.pumpWidget(_wrap(user: _patientUser));
      await tester.pumpAndSettle();
      expect(find.text('Emergency Contact'), findsOneWidget);
    });

    testWidgets('shows Medical Notes field for patient', (tester) async {
      // The Medical Notes field is visible in the Medical Information card.
      await tester.pumpWidget(_wrap(user: _patientUser));
      await tester.pumpAndSettle();
      expect(find.text('Medical Notes'), findsOneWidget);
    });

    testWidgets('does not show Professional Information for patient',
        (tester) async {
      // The caregiver-only section is not rendered for a patient role.
      await tester.pumpWidget(_wrap(user: _patientUser));
      await tester.pumpAndSettle();
      expect(find.text('Professional Information'), findsNothing);
    });
  });
}
