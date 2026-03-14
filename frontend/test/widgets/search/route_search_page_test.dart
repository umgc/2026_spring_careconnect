// Tests for RouteSearchPage — a search interface that lists pages by user role.
// With no user in UserProvider, the page shows "You are not logged in".
// With a user, routes filtered by role are listed in the ListView.

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';
import 'package:care_connect_app/widgets/search/route_search_page.dart';
import 'package:care_connect_app/providers/user_provider.dart';

Widget _wrapNullUser() {
  final provider = UserProvider();
  // No user set: user == null → role == null → "You are not logged in" shown.
  return ChangeNotifierProvider<UserProvider>.value(
    value: provider,
    child: const MaterialApp(home: RouteSearchPage()),
  );
}

Widget _wrapWithUser(UserSession user) {
  final provider = UserProvider()..setUser(user);
  return ChangeNotifierProvider<UserProvider>.value(
    value: provider,
    child: const MaterialApp(home: RouteSearchPage()),
  );
}

UserSession _makeCaregiver() => UserSession(
      id: 1,
      email: 'cg@test.com',
      role: 'caregiver',
      token: 'tok',
      caregiverId: 1,
    );

void main() {
  group('RouteSearchPage – UI structure', () {
    testWidgets('renders Scaffold with Search pages AppBar', (tester) async {
      // Verifies the AppBar title is "Search pages".
      await tester.pumpWidget(_wrapNullUser());
      await tester.pump();
      expect(find.byType(Scaffold), findsOneWidget);
      expect(find.text('Search pages'), findsOneWidget);
    });

    testWidgets('shows search TextField with correct hint', (tester) async {
      // A TextField for searching is shown with the appropriate hint text.
      await tester.pumpWidget(_wrapNullUser());
      await tester.pump();
      expect(find.byType(TextField), findsOneWidget);
      expect(
        find.text('Search by page name or keyword'),
        findsOneWidget,
      );
    });

    testWidgets('shows search icon in TextField prefix', (tester) async {
      // The search icon is used as a prefix icon in the search field.
      await tester.pumpWidget(_wrapNullUser());
      await tester.pump();
      expect(find.byIcon(Icons.search), findsOneWidget);
    });
  });

  group('RouteSearchPage – no user state', () {
    testWidgets('shows You are not logged in when no user', (tester) async {
      // With role == null, _filterByRole returns empty and the not-logged-in
      // message is displayed below the search bar.
      await tester.pumpWidget(_wrapNullUser());
      await tester.pump();
      expect(find.text('You are not logged in'), findsOneWidget);
    });

    testWidgets('shows empty ListView when no user', (tester) async {
      // Without a role, no routes are shown (empty results list).
      await tester.pumpWidget(_wrapNullUser());
      await tester.pump();
      expect(find.byType(ListTile), findsNothing);
    });
  });

  group('RouteSearchPage – with caregiver user', () {
    testWidgets('does not show not-logged-in message with user', (tester) async {
      // When a user is present, the not-logged-in text is hidden.
      await tester.pumpWidget(_wrapWithUser(_makeCaregiver()));
      await tester.pump();
      expect(find.text('You are not logged in'), findsNothing);
    });

    testWidgets('shows at least one ListTile result for caregiver', (tester) async {
      // The route catalog contains CAREGIVER routes; at least one is shown.
      await tester.pumpWidget(_wrapWithUser(_makeCaregiver()));
      await tester.pump();
      expect(find.byType(ListTile), findsAtLeastNWidgets(1));
    });

    testWidgets('typing in search field triggers rebuild', (tester) async {
      // Entering text in the search field updates _query and triggers rebuild.
      await tester.pumpWidget(_wrapWithUser(_makeCaregiver()));
      await tester.pump();
      await tester.enterText(find.byType(TextField), 'dashboard');
      await tester.pump();
      // The TextField still exists after query update.
      expect(find.byType(TextField), findsOneWidget);
    });

    testWidgets('search with no match shows empty ListView', (tester) async {
      // A search query that matches nothing results in no ListTiles shown.
      await tester.pumpWidget(_wrapWithUser(_makeCaregiver()));
      await tester.pump();
      await tester.enterText(
        find.byType(TextField),
        'zzz_no_match_xyzzy_12345',
      );
      await tester.pump();
      expect(find.byType(ListTile), findsNothing);
    });

    testWidgets('shows chevron_right icon in each result', (tester) async {
      // Each route result row ends with a chevron icon.
      await tester.pumpWidget(_wrapWithUser(_makeCaregiver()));
      await tester.pump();
      expect(find.byIcon(Icons.chevron_right), findsAtLeastNWidgets(1));
    });
  });
}
