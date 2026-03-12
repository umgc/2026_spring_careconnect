import 'package:flutter_test/flutter_test.dart';
import 'package:flutter/material.dart';
import 'package:care_connect_app/features/analytics/analytics_page.dart';

void main() {
  testWidgets('Analytics page shows the analytics screen shell', (
    WidgetTester tester,
  ) async {
    // Build our app and trigger a frame.
    await tester
        .pumpWidget(const MaterialApp(home: AnalyticsPage(patientId: 1)));

    expect(find.text('Patient Analytics'), findsOneWidget);
    expect(find.byType(CircularProgressIndicator), findsOneWidget);
  });

  testWidgets('Analytics page can be created without exceptions', (
    WidgetTester tester,
  ) async {
    await tester
        .pumpWidget(const MaterialApp(home: AnalyticsPage(patientId: 1)));

    expect(tester.takeException(), isNull);
  });

  testWidgets('Retry button remains available after invalid patient validation',
      (
    WidgetTester tester,
  ) async {
    await tester
        .pumpWidget(const MaterialApp(home: AnalyticsPage(patientId: 0)));

    await tester.pump();
    await tester.pump();

    expect(find.text('Retry'), findsOneWidget);

    await tester.tap(find.text('Retry'));
    await tester.pump();
    await tester.pump();

    expect(find.textContaining('Invalid patient ID: 0'), findsOneWidget);
  });
}
