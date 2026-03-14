// Tests for ScheduledNotification model
// (lib/features/notifications/models/scheduled_notification_model.dart).
//
// Coverage strategy:
//   ScheduledNotification is a pure Dart data class (no platform channels, no
//   network I/O) with fromJson deserialization and toJson serialization.
//
//   Branches tested:
//     constructor  — required fields stored; optional fields default correctly
//                    (status = "PENDING", id = null, sentTime = null, etc.).
//     fromJson     — all fields present; missing id → defaults to -1;
//                    missing status → defaults to "PENDING"; missing sentTime → null;
//                    scheduledTime and sentTime parsed from ISO-8601 strings;
//                    optional notificationType, messageId, errorMessage present.
//     toJson       — only the fields needed for creation requests are included
//                    (taskId, receiverId, title, body, notificationType,
//                    scheduledTime as ISO-8601); backend-managed fields excluded.

import 'package:flutter_test/flutter_test.dart';

import 'package:care_connect_app/features/notifications/models/scheduled_notification_model.dart';

void main() {
  // ─── Constructor ─────────────────────────────────────────────────────────────

  group('ScheduledNotification constructor', () {
    test('stores all required fields and applies default status "PENDING"', () {
      // Verifies that every required field is stored and the status default works.
      final scheduledTime = DateTime(2025, 7, 1, 9, 0);
      final n = ScheduledNotification(
        receiverId: 10,
        title: 'Appointment Reminder',
        body: 'Your appointment is at 9 AM.',
        scheduledTime: scheduledTime,
      );
      expect(n.receiverId, 10);
      expect(n.title, 'Appointment Reminder');
      expect(n.body, 'Your appointment is at 9 AM.');
      expect(n.scheduledTime, scheduledTime);
      expect(n.status, 'PENDING');
      expect(n.id, isNull);
      expect(n.taskId, isNull);
      expect(n.sentTime, isNull);
      expect(n.notificationType, isNull);
      expect(n.messageId, isNull);
      expect(n.errorMessage, isNull);
    });

    test('stores explicitly provided optional fields', () {
      // Verifies that optional fields are honored when supplied.
      final sent = DateTime(2025, 7, 1, 9, 0, 5);
      final n = ScheduledNotification(
        id: 55,
        taskId: 99,
        receiverId: 1,
        title: 'Emergency',
        body: 'Fall detected.',
        notificationType: 'EMERGENCY',
        scheduledTime: DateTime(2025, 7, 1, 9, 0),
        sentTime: sent,
        status: 'SENT',
        messageId: 'msg-abc',
        errorMessage: null,
      );
      expect(n.id, 55);
      expect(n.taskId, 99);
      expect(n.notificationType, 'EMERGENCY');
      expect(n.sentTime, sent);
      expect(n.status, 'SENT');
      expect(n.messageId, 'msg-abc');
    });
  });

  // ─── ScheduledNotification.fromJson ──────────────────────────────────────────

  group('ScheduledNotification.fromJson', () {
    test('parses all fields from a complete JSON map', () {
      // Verifies every field is correctly extracted from a full backend payload.
      final json = {
        'id': 3,
        'taskId': 7,
        'receiverId': 42,
        'title': 'Medication Reminder',
        'body': 'Take aspirin.',
        'notificationType': 'REMINDER',
        'scheduledTime': '2025-06-01T09:00:00.000Z',
        'sentTime': '2025-06-01T09:00:02.000Z',
        'status': 'SENT',
        'messageId': 'msg-xyz',
        'errorMessage': null,
      };
      final n = ScheduledNotification.fromJson(json);
      expect(n.id, 3);
      expect(n.taskId, 7);
      expect(n.receiverId, 42);
      expect(n.title, 'Medication Reminder');
      expect(n.body, 'Take aspirin.');
      expect(n.notificationType, 'REMINDER');
      expect(n.scheduledTime.year, 2025);
      expect(n.sentTime, isNotNull);
      expect(n.status, 'SENT');
      expect(n.messageId, 'msg-xyz');
      expect(n.errorMessage, isNull);
    });

    test('missing id defaults to -1', () {
      // Verifies the fallback for the optional id field.
      final n = ScheduledNotification.fromJson({
        'receiverId': 1,
        'title': 'T',
        'body': 'B',
        'scheduledTime': '2025-01-01T00:00:00.000Z',
      });
      expect(n.id, -1);
    });

    test('missing status defaults to "PENDING"', () {
      // Verifies the fallback status when the field is absent from the payload.
      final n = ScheduledNotification.fromJson({
        'receiverId': 1,
        'title': 'T',
        'body': 'B',
        'scheduledTime': '2025-01-01T00:00:00.000Z',
      });
      expect(n.status, 'PENDING');
    });

    test('missing sentTime → null', () {
      // Verifies that absent sentTime stays null (notification not yet sent).
      final n = ScheduledNotification.fromJson({
        'receiverId': 1,
        'title': 'T',
        'body': 'B',
        'scheduledTime': '2025-01-01T00:00:00.000Z',
      });
      expect(n.sentTime, isNull);
    });

    test('present sentTime is parsed from ISO-8601 string', () {
      // Verifies DateTime.parse is used for the sentTime field.
      final n = ScheduledNotification.fromJson({
        'receiverId': 1,
        'title': 'T',
        'body': 'B',
        'scheduledTime': '2025-03-10T08:00:00.000Z',
        'sentTime': '2025-03-10T08:00:01.000Z',
      });
      expect(n.sentTime?.year, 2025);
      expect(n.sentTime?.month, 3);
    });
  });

  // ─── ScheduledNotification.toJson ────────────────────────────────────────────

  group('ScheduledNotification.toJson', () {
    test('includes only the creation-request fields', () {
      // Verifies that only the fields needed for creating a new notification
      // on the backend are included (server sets id, sentTime, status, etc.).
      final n = ScheduledNotification(
        id: 99,
        taskId: 5,
        receiverId: 10,
        title: 'Reminder',
        body: 'Check in now.',
        notificationType: 'ALERT',
        scheduledTime: DateTime.utc(2025, 8, 15, 14, 0),
        sentTime: DateTime.utc(2025, 8, 15, 14, 0, 1),
        status: 'SENT',
        messageId: 'msg-1',
      );
      final json = n.toJson();

      // Fields that belong in a creation request.
      expect(json['taskId'], 5);
      expect(json['receiverId'], 10);
      expect(json['title'], 'Reminder');
      expect(json['body'], 'Check in now.');
      expect(json['notificationType'], 'ALERT');
      expect(json['scheduledTime'], isA<String>());
      expect(DateTime.parse(json['scheduledTime'] as String).year, 2025);

      // Backend-managed fields must not be included.
      expect(json.containsKey('id'), isFalse);
      expect(json.containsKey('sentTime'), isFalse);
      expect(json.containsKey('status'), isFalse);
      expect(json.containsKey('messageId'), isFalse);
      expect(json.containsKey('errorMessage'), isFalse);
    });

    test('fromJson → toJson round-trip preserves creation fields', () {
      // Verifies that deserializing then serializing recovers the same data.
      final original = ScheduledNotification.fromJson({
        'id': 1,
        'taskId': 3,
        'receiverId': 20,
        'title': 'Round-trip',
        'body': 'Test body.',
        'notificationType': 'REMINDER',
        'scheduledTime': '2025-11-01T07:30:00.000Z',
        'status': 'PENDING',
      });
      final json = original.toJson();
      expect(json['taskId'], 3);
      expect(json['receiverId'], 20);
      expect(json['title'], 'Round-trip');
      expect(json['body'], 'Test body.');
      expect(json['notificationType'], 'REMINDER');
    });
  });
}
