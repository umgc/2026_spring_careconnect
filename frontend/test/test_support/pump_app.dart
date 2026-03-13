import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';
import 'package:provider/single_child_widget.dart';

/// Shared widget bootstrap helper for frontend tests.
///
/// This helper standardizes how tests pump widgets with app-level wrappers so
/// each test does not duplicate provider/material setup.
Future<void> pumpCareConnectApp(
  WidgetTester tester,
  Widget child, {
  List<SingleChildWidget> providers = const [],
}) async {
  await tester.pumpWidget(
    MultiProvider(
      providers: providers,
      child: MaterialApp(home: Scaffold(body: child)),
    ),
  );
}
