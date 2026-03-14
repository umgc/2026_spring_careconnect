import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:care_connect_app/features/telemetry/telemetry_settings.dart';

void main() {
  // This is required for any test that uses plugins like SharedPreferences
  TestWidgetsFlutterBinding.ensureInitialized();

  group('TelemetrySettings', () {
    
    setUp(() {
      // Clear the "disk" before every test to ensure a clean slate
      SharedPreferences.setMockInitialValues({});
    });

    test('isOptedOut returns false by default when no value is set', () async {
      final result = await TelemetrySettings.isOptedOut();
      expect(result, isFalse);
    });

    test('setOptedOut correctly saves and retrieves the value', () async {
      await TelemetrySettings.setOptedOut(true);
      final result = await TelemetrySettings.isOptedOut();
      expect(result, isTrue);
    });

    test('hasSeenDialog returns false by default when no value is set', () async {
      final result = await TelemetrySettings.hasSeenDialog();
      expect(result, isFalse);
    });

    test('setHasSeenDialog correctly saves and retrieves the value', () async {
      await TelemetrySettings.setHasSeenDialog(true);
      final result = await TelemetrySettings.hasSeenDialog();
      expect(result, isTrue);
    });

    test('settings are independent of each other', () async {
      // Set one value but not the other
      await TelemetrySettings.setOptedOut(true);
      
      expect(await TelemetrySettings.isOptedOut(), isTrue);
      expect(await TelemetrySettings.hasSeenDialog(), isFalse);
    });
  });
}