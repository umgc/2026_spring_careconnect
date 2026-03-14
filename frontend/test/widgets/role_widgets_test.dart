import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';
import 'package:care_connect_app/widgets/role_widgets.dart';
import 'package:care_connect_app/providers/user_provider.dart';

// Mock UserSession for testing
class MockUserSession {
  final int id;
  final String name;
  final String email;
  final String role;
  final String token;

  MockUserSession({
    required this.id,
    required this.name,
    required this.email,
    required this.role,
    required this.token,
  });
}

// Mock UserProvider for testing
class MockUserProvider extends UserProvider {
  MockUserSession? mockSession;

  MockUserProvider({this.mockSession});

  @override
  UserSession? get userSession {
    if (mockSession == null) return null;
    return UserSession(
      id: mockSession!.id,
      name: mockSession!.name,
      email: mockSession!.email,
      role: mockSession!.role,
      token: mockSession!.token,
      patientId: null,
      caregiverId: null,
    );
  }
}

void main() {
  group('Role Widget Tests', () {
    testWidgets('AdminOnly shows content for admin', (tester) async {
      final mockProvider = MockUserProvider(
        mockSession: MockUserSession(
          id: 1,
          name: 'Admin User',
          email: 'admin@test.com',
          role: 'ADMIN',
          token: 'token',
        ),
      );

      await tester.pumpWidget(
        ChangeNotifierProvider<UserProvider>.value(
          value: mockProvider,
          child: MaterialApp(
            home: Scaffold(
              body: AdminOnly(
                child: Text('Admin Content'),
              ),
            ),
          ),
        ),
      );

      expect(find.text('Admin Content'), findsOneWidget);
    });

    testWidgets('AdminOnly hides content for non-admin', (tester) async {
      final mockProvider = MockUserProvider(
        mockSession: MockUserSession(
          id: 1,
          name: 'Patient User',
          email: 'patient@test.com',
          role: 'PATIENT',
          token: 'token',
        ),
      );

      await tester.pumpWidget(
        ChangeNotifierProvider<UserProvider>.value(
          value: mockProvider,
          child: MaterialApp(
            home: Scaffold(
              body: AdminOnly(
                child: Text('Admin Content'),
              ),
            ),
          ),
        ),
      );

      expect(find.text('Admin Content'), findsNothing);
    });

    testWidgets('CaregiverOrAdmin shows for both roles', (tester) async {
      // Test with Caregiver
      final caregiverProvider = MockUserProvider(
        mockSession: MockUserSession(
          id: 1,
          name: 'Caregiver',
          email: 'caregiver@test.com',
          role: 'CAREGIVER',
          token: 'token',
        ),
      );

      await tester.pumpWidget(
        ChangeNotifierProvider<UserProvider>.value(
          value: caregiverProvider,
          child: MaterialApp(
            home: Scaffold(
              body: CaregiverOrAdmin(
                child: Text('Caregiver or Admin Content'),
              ),
            ),
          ),
        ),
      );

      expect(find.text('Caregiver or Admin Content'), findsOneWidget);

      // Test with Admin
      final adminProvider = MockUserProvider(
        mockSession: MockUserSession(
          id: 1,
          name: 'Admin',
          email: 'admin@test.com',
          role: 'ADMIN',
          token: 'token',
        ),
      );

      await tester.pumpWidget(
        ChangeNotifierProvider<UserProvider>.value(
          value: adminProvider,
          child: MaterialApp(
            home: Scaffold(
              body: CaregiverOrAdmin(
                child: Text('Caregiver or Admin Content'),
              ),
            ),
          ),
        ),
      );

      expect(find.text('Caregiver or Admin Content'), findsOneWidget);
    });

    testWidgets('NotFamilyMember hides for family members', (tester) async {
      final mockProvider = MockUserProvider(
        mockSession: MockUserSession(
          id: 1,
          name: 'Family',
          email: 'family@test.com',
          role: 'FAMILY_MEMBER',
          token: 'token',
        ),
      );

      await tester.pumpWidget(
        ChangeNotifierProvider<UserProvider>.value(
          value: mockProvider,
          child: MaterialApp(
            home: Scaffold(
              body: NotFamilyMember(
                child: Text('Not Family Content'),
              ),
            ),
          ),
        ),
      );

      expect(find.text('Not Family Content'), findsNothing);
    });

    testWidgets('PermissionButton shows for users with permission',
        (tester) async {
      final mockProvider = MockUserProvider(
        mockSession: MockUserSession(
          id: 1,
          name: 'Caregiver',
          email: 'caregiver@test.com',
          role: 'CAREGIVER',
          token: 'token',
        ),
      );

      await tester.pumpWidget(
        ChangeNotifierProvider<UserProvider>.value(
          value: mockProvider,
          child: MaterialApp(
            home: Scaffold(
              body: PermissionButton(
                permission: 'CREATE_TASKS',
                onPressed: () {},
                child: Text('Create Task'),
              ),
            ),
          ),
        ),
      );

      expect(find.text('Create Task'), findsOneWidget);
    });

    testWidgets('PermissionButton hides for users without permission',
        (tester) async {
      final mockProvider = MockUserProvider(
        mockSession: MockUserSession(
          id: 1,
          name: 'Patient',
          email: 'patient@test.com',
          role: 'PATIENT',
          token: 'token',
        ),
      );

      await tester.pumpWidget(
        ChangeNotifierProvider<UserProvider>.value(
          value: mockProvider,
          child: MaterialApp(
            home: Scaffold(
              body: PermissionButton(
                permission: 'DELETE_PATIENTS',
                onPressed: () {},
                child: Text('Delete Patient'),
              ),
            ),
          ),
        ),
      );

      expect(find.text('Delete Patient'), findsNothing);
    });
  });
}