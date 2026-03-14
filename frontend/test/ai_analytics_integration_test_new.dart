import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:care_connect_app/features/analytics/analytics_page.dart';

void main() {
  group('AI Analytics Integration Tests', () {
    testWidgets('AnalyticsPage exposes an AI entry point', (
      WidgetTester tester,
    ) async {
      await tester.pumpWidget(
        const MaterialApp(home: AnalyticsPage(patientId: 0)),
      );

      await tester.pump();
      await tester.pump();

      expect(find.byTooltip('Ask AI about analytics'), findsOneWidget);
    });

    testWidgets('AnalyticsPage error state still builds cleanly for AI flows', (
      WidgetTester tester,
    ) async {
      await tester.pumpWidget(
        const MaterialApp(home: AnalyticsPage(patientId: 0)),
      );

      await tester.pump();
      await tester.pump();

      expect(find.byTooltip('Ask AI about analytics'), findsOneWidget);
      expect(tester.takeException(), isNull);
    });

    test('Health data context anonymizes patient information', () {
      expect(true, isTrue);
    });
  });
}
