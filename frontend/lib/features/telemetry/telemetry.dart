import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;

import 'telemetry_settings.dart';
import 'telemetry_guardrails.dart';

class Telemetry {
  // NOTE:
  // - Flutter Web in Chrome: localhost works because browser is on your Mac.
  // - Android emulator: use http://10.0.2.2:8080 instead of localhost.
  // - Physical phone: use your Mac LAN IP (ex: http://192.168.1.50:8080).
  static const String _devEndpoint = 'http://localhost:8080/api/dev/telemetry';

  static Future<bool> _enabled() async {
    final optedOut = await TelemetrySettings.isOptedOut();
    return !optedOut;
  }

  static Future<void> event(String name, Map<String, Object?> props) async {
    final enabled = await _enabled();
    if (!enabled) {
      if (kDebugMode) debugPrint('[telemetry] blocked (opted out): $name');
      return;
    }

    final sanitized = TelemetryGuardrails.sanitize(name, props);
    if (sanitized == null) {
      if (kDebugMode) debugPrint('[telemetry] dropped (guardrails): $name');
      return;
    }

    final payload = {
      'eventName': name,
      'details': sanitized,
      'deviceInfo': {
        // Correctly label web vs mobile
        'uiSurface': kIsWeb ? 'web' : 'mobile',

        // For web, defaultTargetPlatform often reads as macOS/windows/etc,
        // but we also include explicit isWeb so the backend can segment cleanly.
        'platform': defaultTargetPlatform.name,
        'isWeb': kIsWeb,

        // Helpful for dev filtering
        'debug': kDebugMode,
      },
    };

    try {
      final resp = await http.post(
        Uri.parse(_devEndpoint),
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode(payload),
      );

      if (kDebugMode) {
        debugPrint('[telemetry] sent: $name status=${resp.statusCode}');
        if (resp.statusCode >= 400) debugPrint('[telemetry] body=${resp.body}');
      }
    } catch (e) {
      if (kDebugMode) debugPrint('[telemetry] send failed: $name error=$e');
    }
  }
}
