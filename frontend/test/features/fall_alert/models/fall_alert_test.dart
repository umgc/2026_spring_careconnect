// Tests for FallAlert model (lib/features/fall_alert/models/fall_alert.dart).
//
// Coverage strategy:
//   FallAlert is a pure Dart data class with toPayload / fromPayload methods
//   that serialize to/from Map<String, String>, using JSON encoding for the
//   nested playbackData map.  No platform channels or network I/O required.
//
//   Branches tested:
//     toPayload  — all optional fields present (phone, emergency contact,
//                  liveVideoUrl, playbackData JSON-encoded); all optional
//                  fields absent (null → empty string in map);
//                  hasLiveVideo false → "false" string; detectedAtUtc as ISO-8601.
//     fromPayload — full round-trip restores every field; empty liveVideoUrl
//                   string → null Uri; empty patientPhone → null; empty
//                   emergencyContactName/Phone → null; empty playbackData → null;
//                   non-empty playbackData JSON string → decoded map;
//                   detectedAtUtc parsed correctly.

import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';

import 'package:care_connect_app/features/fall_alert/models/fall_alert.dart';

// ─── Helper ────────────────────────────────────────────────────────────────────

FallAlert makeAlert({
  bool hasLiveVideo = true,
  String? phone,
  String? emergencyName,
  String? emergencyPhone,
  Map<String, dynamic>? playbackData,
}) {
  return FallAlert(
    id: 'alert-1',
    patientId: 'patient-99',
    patientName: 'Alice Smith',
    detectedAtUtc: DateTime.utc(2025, 6, 15, 14, 30),
    source: 'watch',
    hasLiveVideo: hasLiveVideo,
    liveVideoUrl: hasLiveVideo ? Uri.parse('https://video.example.com/live') : null,
    patientPhone: phone,
    emergencyContactName: emergencyName,
    emergencyContactPhone: emergencyPhone,
    playbackData: playbackData,
  );
}

void main() {
  // ─── FallAlert.toPayload ──────────────────────────────────────────────────────

  group('FallAlert.toPayload', () {
    test('serializes all fields when fully populated', () {
      // Verifies that toPayload produces a string-only map with every field present.
      final alert = makeAlert(
        phone: '+15550001234',
        emergencyName: 'Bob Smith',
        emergencyPhone: '+15550009876',
        playbackData: {'url': 'https://play.example.com/abc'},
      );
      final payload = alert.toPayload();

      expect(payload['id'], 'alert-1');
      expect(payload['patientId'], 'patient-99');
      expect(payload['patientName'], 'Alice Smith');
      expect(payload['source'], 'watch');
      expect(payload['hasLiveVideo'], 'true');
      expect(payload['liveVideoUrl'], 'https://video.example.com/live');
      expect(payload['patientPhone'], '+15550001234');
      expect(payload['emergencyContactName'], 'Bob Smith');
      expect(payload['emergencyContactPhone'], '+15550009876');
      // playbackData must be JSON-encoded.
      final decoded = jsonDecode(payload['playbackData']!) as Map;
      expect(decoded['url'], 'https://play.example.com/abc');
    });

    test('null optional fields serialize to empty strings', () {
      // Verifies that absent optional fields produce empty strings in the payload.
      final alert = makeAlert(hasLiveVideo: false);
      final payload = alert.toPayload();

      expect(payload['hasLiveVideo'], 'false');
      expect(payload['liveVideoUrl'], '');
      expect(payload['patientPhone'], '');
      expect(payload['emergencyContactName'], '');
      expect(payload['emergencyContactPhone'], '');
      expect(payload['playbackData'], '');
    });

    test('detectedAtUtc is stored as a parseable ISO-8601 string', () {
      // Verifies the timestamp survives string serialization.
      final alert = makeAlert();
      final ts = alert.toPayload()['detectedAtUtc']!;
      expect(DateTime.parse(ts).year, 2025);
    });
  });

  // ─── FallAlert.fromPayload ────────────────────────────────────────────────────

  group('FallAlert.fromPayload', () {
    test('round-trips a fully populated alert', () {
      // Verifies fromPayload restores every field that toPayload serialized.
      final original = makeAlert(
        phone: '+15550001234',
        emergencyName: 'Bob',
        emergencyPhone: '+15550009876',
        playbackData: {'clip': 'abc123'},
      );
      final restored = FallAlert.fromPayload(original.toPayload());

      expect(restored.id, original.id);
      expect(restored.patientId, original.patientId);
      expect(restored.patientName, original.patientName);
      expect(restored.source, original.source);
      expect(restored.hasLiveVideo, original.hasLiveVideo);
      expect(restored.liveVideoUrl.toString(), original.liveVideoUrl.toString());
      expect(restored.patientPhone, original.patientPhone);
      expect(restored.emergencyContactName, original.emergencyContactName);
      expect(restored.emergencyContactPhone, original.emergencyContactPhone);
      expect(restored.playbackData?['clip'], 'abc123');
    });

    test('empty liveVideoUrl string deserializes to null Uri', () {
      // Verifies that an empty string for liveVideoUrl becomes null.
      final alert = makeAlert(hasLiveVideo: false);
      final restored = FallAlert.fromPayload(alert.toPayload());
      expect(restored.liveVideoUrl, isNull);
    });

    test('empty patientPhone string deserializes to null', () {
      // Verifies null → empty string → null round-trip for patientPhone.
      final alert = makeAlert(phone: null);
      final restored = FallAlert.fromPayload(alert.toPayload());
      expect(restored.patientPhone, isNull);
    });

    test('empty emergencyContactName string deserializes to null', () {
      // Verifies null → empty string → null round-trip for emergency contact name.
      final restored = FallAlert.fromPayload(makeAlert().toPayload());
      expect(restored.emergencyContactName, isNull);
    });

    test('empty playbackData string deserializes to null map', () {
      // Verifies that absent playbackData round-trips as null.
      final alert = makeAlert(playbackData: null);
      final restored = FallAlert.fromPayload(alert.toPayload());
      expect(restored.playbackData, isNull);
    });

    test('detectedAtUtc round-trips with correct year, month, day', () {
      // Verifies the date/time survives the string serialization round-trip.
      final alert = makeAlert();
      final restored = FallAlert.fromPayload(alert.toPayload());
      expect(restored.detectedAtUtc.year, 2025);
      expect(restored.detectedAtUtc.month, 6);
      expect(restored.detectedAtUtc.day, 15);
    });

    test('hasLiveVideo true round-trips correctly', () {
      // Verifies that the "true" string is parsed back to boolean true.
      final alert = makeAlert(hasLiveVideo: true);
      final restored = FallAlert.fromPayload(alert.toPayload());
      expect(restored.hasLiveVideo, isTrue);
    });

    test('hasLiveVideo false round-trips correctly', () {
      // Verifies that the "false" string is parsed back to boolean false.
      final alert = makeAlert(hasLiveVideo: false);
      final restored = FallAlert.fromPayload(alert.toPayload());
      expect(restored.hasLiveVideo, isFalse);
    });
  });
}
