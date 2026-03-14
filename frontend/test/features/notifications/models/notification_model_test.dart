// Tests for Notification_dto model
// (lib/features/notifications/models/notification_model.dart).
//
// Coverage strategy:
//   Notification_dto is a pure Dart data class (no platform channels, no
//   network I/O) with fromJson deserialization and toJson serialization.
//
//   Branches tested:
//     constructor  — all required fields stored; isRead defaults to false.
//     fromJson     — all fields present; timestamp parsed from ISO-8601 string;
//                    isRead missing → defaults to false; isRead = true.
//     toJson       — serialized output is a JSON string containing all fields;
//                    timestamp serialized as ISO-8601 string; isRead included.

import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';

import 'package:care_connect_app/features/notifications/models/notification_model.dart';

void main() {
  // ─── constructor ─────────────────────────────────────────────────────────────

  group('Notification_dto constructor', () {
    test('stores all required fields and isRead defaults to false', () {
      // Verifies that every field is accessible after construction and that
      // the default value for isRead is false.
      final ts = DateTime(2025, 6, 1, 10, 0);
      final n = Notification_dto(
        id: 1,
        title: 'Appointment Reminder',
        message: 'Your appointment is tomorrow.',
        timestamp: ts,
      );
      expect(n.id, 1);
      expect(n.title, 'Appointment Reminder');
      expect(n.message, 'Your appointment is tomorrow.');
      expect(n.timestamp, ts);
      expect(n.isRead, isFalse);
    });

    test('stores isRead = true when explicitly provided', () {
      // Verifies the non-default value of isRead is honored.
      final n = Notification_dto(
        id: 2,
        title: 'Read',
        message: 'Already read.',
        timestamp: DateTime(2025, 1, 1),
        isRead: true,
      );
      expect(n.isRead, isTrue);
    });
  });

  // ─── Notification_dto.fromJson ────────────────────────────────────────────────

  group('Notification_dto.fromJson', () {
    test('parses all fields from a complete JSON map', () {
      // Verifies every field is extracted correctly from a full backend payload.
      final json = {
        'id': 42,
        'title': 'Fall Alert',
        'message': 'A fall was detected.',
        'timestamp': '2025-03-10T14:30:00.000Z',
        'isRead': false,
      };
      final n = Notification_dto.fromJson(json);
      expect(n.id, 42);
      expect(n.title, 'Fall Alert');
      expect(n.message, 'A fall was detected.');
      expect(n.timestamp.year, 2025);
      expect(n.timestamp.month, 3);
      expect(n.isRead, isFalse);
    });

    test('missing isRead key defaults to false', () {
      // Verifies the null-safety default when isRead is absent from the payload.
      final json = {
        'id': 1,
        'title': 'T',
        'message': 'M',
        'timestamp': '2025-01-01T00:00:00.000Z',
      };
      final n = Notification_dto.fromJson(json);
      expect(n.isRead, isFalse);
    });

    test('isRead = true is parsed correctly', () {
      // Verifies that a true boolean value is preserved during deserialization.
      final json = {
        'id': 5,
        'title': 'Read notification',
        'message': 'Already seen.',
        'timestamp': '2025-06-15T09:00:00.000Z',
        'isRead': true,
      };
      final n = Notification_dto.fromJson(json);
      expect(n.isRead, isTrue);
    });

    test('timestamp is parsed from ISO-8601 string to DateTime', () {
      // Verifies DateTime.parse() is used and the result has correct fields.
      final json = {
        'id': 3,
        'title': 'T',
        'message': 'M',
        'timestamp': '2025-12-25T08:00:00.000Z',
        'isRead': false,
      };
      final n = Notification_dto.fromJson(json);
      expect(n.timestamp.year, 2025);
      expect(n.timestamp.month, 12);
      expect(n.timestamp.day, 25);
    });
  });

  // ─── Notification_dto.toJson ──────────────────────────────────────────────────

  group('Notification_dto.toJson', () {
    test('serializes to a JSON string containing all fields', () {
      // Verifies toJson returns a JSON-encoded string with every field present.
      final ts = DateTime.utc(2025, 6, 1, 12, 0);
      final n = Notification_dto(
        id: 7,
        title: 'Medication Reminder',
        message: 'Take your pill.',
        timestamp: ts,
        isRead: false,
      );
      final jsonStr = n.toJson();
      expect(jsonStr, isA<String>());

      final decoded = jsonDecode(jsonStr) as Map<String, dynamic>;
      expect(decoded['id'], 7);
      expect(decoded['title'], 'Medication Reminder');
      expect(decoded['message'], 'Take your pill.');
      expect(decoded['isRead'], isFalse);
      expect(decoded['timestamp'], isA<String>());
      // Timestamp should be parseable back to a DateTime.
      expect(DateTime.parse(decoded['timestamp'] as String).year, 2025);
    });

    test('serialized isRead = true is preserved in JSON output', () {
      // Verifies that a read notification keeps isRead = true through serialization.
      final n = Notification_dto(
        id: 8,
        title: 'T',
        message: 'M',
        timestamp: DateTime(2025, 1, 1),
        isRead: true,
      );
      final decoded = jsonDecode(n.toJson()) as Map<String, dynamic>;
      expect(decoded['isRead'], isTrue);
    });

    test('fromJson → toJson round-trip preserves all fields', () {
      // Verifies that deserializing then re-serializing recovers the same data.
      final original = {
        'id': 9,
        'title': 'Round-trip',
        'message': 'Test message.',
        'timestamp': '2025-09-01T00:00:00.000Z',
        'isRead': true,
      };
      final dto = Notification_dto.fromJson(original);
      final reserialized = jsonDecode(dto.toJson()) as Map<String, dynamic>;

      expect(reserialized['id'], original['id']);
      expect(reserialized['title'], original['title']);
      expect(reserialized['message'], original['message']);
      expect(reserialized['isRead'], original['isRead']);
    });
  });
}
