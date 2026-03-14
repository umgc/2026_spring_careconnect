import 'package:flutter_test/flutter_test.dart';
import 'package:flutter/material.dart';
import 'package:care_connect_app/features/analytics/analytics_page.dart';

void main() {
  testWidgets('Analytics page renders correctly', (WidgetTester tester) async {
    // Build our app and trigger a frame.
    await tester
        .pumpWidget(const MaterialApp(home: AnalyticsPage(patientId: 1)));

    // Verify that the analytics page shows loading initially
    expect(find.text('Patient Analytics'), findsOneWidget);
    expect(find.byType(CircularProgressIndicator), findsOneWidget);
  });

  testWidgets('Analytics page shows validation error for invalid patient id', (
    WidgetTester tester,
  ) async {
    await tester.pumpWidget(
      const MaterialApp(home: AnalyticsPage(patientId: 0)),
    );

    await tester.pump();
    await tester.pump();

    expect(find.textContaining('Invalid patient ID: 0'), findsOneWidget);
    expect(find.text('Retry'), findsOneWidget);
    expect(find.byIcon(Icons.error_outline), findsOneWidget);
  });
}
