import 'package:shared_preferences/shared_preferences.dart';

class TelemetrySettings {
  static const _optOutKey = 'telemetry_opted_out';
  static const _seenDialogKey = 'telemetry_seen_optout_dialog';

  static Future<bool> isOptedOut() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getBool(_optOutKey) ?? false;
  }

  static Future<void> setOptedOut(bool optedOut) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(_optOutKey, optedOut);
  }

  static Future<bool> hasSeenDialog() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getBool(_seenDialogKey) ?? false;
  }

  static Future<void> setHasSeenDialog(bool seen) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(_seenDialogKey, seen);
  }
}
