// Tests for Task model (lib/features/tasks/models/task_model.dart).
//
// Coverage strategy:
//   Task is a mutable data class backed by Flutter's TimeOfDay.  All logic
//   is pure (no network, no platform channels beyond the Flutter binding).
//
//   Branches tested:
//     Task.fromJson — all fields present; missing optional fields use safe
//       defaults; timeOfDay as "HH:mm" string; timeOfDay as {hour, minute}
//       map; interval from "taskInterval" fallback; count from "doCount"
//       fallback; notifications list parsed to ScheduledNotification objects;
//       daysOfWeek list parsed.
//     Task.toJson  — all set fields serialized; id omitted when null;
//       taskType defaults to "general" when null; timeOfDay formatted as
//       zero-padded HH:mm string; null timeOfDay → null in map.
//     Task.isValid — true when name and description both non-empty;
//       false when name is empty; false when description is empty (default "").
//     TaskCopyWith — replaces specified fields, keeps unspecified ones;
//       copyWith with no args preserves every field.

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:care_connect_app/features/tasks/models/task_model.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  // ─── Task.fromJson ────────────────────────────────────────────────────────────

  group('Task.fromJson', () {
    Map<String, dynamic> fullJson() => {
      'id': 99,
      'name': 'Take medication',
      'description': 'Take aspirin after meals',
      'createdAt': 1700000000,
      'date': '2025-06-15T09:00:00.000Z',
      'timeOfDay': '09:30',
      'patientId': 42,
      'isComplete': true,
      'frequency': 'daily',
      'interval': 1,
      'count': 30,
      'daysOfWeek': [false, true, false, true, false, false, false],
      'taskType': 'Lab',
      'applyToSeries': true,
      'parentTaskId': 7,
    };

    test('parses all fields from a complete JSON map', () {
      // Verifies every field is correctly extracted from a full payload.
      final task = Task.fromJson(fullJson());
      expect(task.id, 99);
      expect(task.name, 'Take medication');
      expect(task.description, 'Take aspirin after meals');
      expect(task.createdAt, 1700000000);
      expect(task.date.year, 2025);
      expect(task.timeOfDay?.hour, 9);
      expect(task.timeOfDay?.minute, 30);
      expect(task.assignedPatientId, 42);
      expect(task.isComplete, isTrue);
      expect(task.frequency, 'daily');
      expect(task.interval, 1);
      expect(task.count, 30);
      expect(task.daysOfWeek, [false, true, false, true, false, false, false]);
      expect(task.taskType, 'Lab');
      expect(task.applyToSeries, isTrue);
      expect(task.parentTaskId, 7);
    });

    test('parses timeOfDay from a {hour, minute} map', () {
      // Verifies the alternative map-based timeOfDay format from the backend.
      final task = Task.fromJson({
        'name': 'Check-up',
        'date': '2025-01-01T00:00:00.000Z',
        'timeOfDay': {'hour': 14, 'minute': 45},
      });
      expect(task.timeOfDay?.hour, 14);
      expect(task.timeOfDay?.minute, 45);
    });

    test('missing optional fields use safe defaults', () {
      // Verifies null-safety defaults when optional keys are absent.
      final task = Task.fromJson({
        'name': 'Walk',
        'date': '2025-03-10T00:00:00.000Z',
      });
      expect(task.id, isNull);
      expect(task.description, '');
      expect(task.timeOfDay, isNull);
      expect(task.isComplete, isFalse);
      expect(task.applyToSeries, isFalse);
      expect(task.notifications, isNull);
      expect(task.daysOfWeek, isNull);
      expect(task.parentTaskId, isNull);
    });

    test('interval falls back to taskInterval when interval key is absent', () {
      // Verifies the alternative field name used by older backend versions.
      final task = Task.fromJson({
        'name': 'Weekly review',
        'date': '2025-04-01T00:00:00.000Z',
        'taskInterval': 7,
      });
      expect(task.interval, 7);
    });

    test('count falls back to doCount when count key is absent', () {
      // Verifies the alternative field name used by older backend versions.
      final task = Task.fromJson({
        'name': 'Repeat',
        'date': '2025-05-01T00:00:00.000Z',
        'doCount': 5,
      });
      expect(task.count, 5);
    });

    test('notifications list is parsed into ScheduledNotification objects', () {
      // Verifies that a notifications array is mapped correctly.
      final task = Task.fromJson({
        'name': 'Reminder',
        'date': '2025-06-01T00:00:00.000Z',
        'notifications': [
          {
            'receiverId': 1,
            'title': 'Time for medication',
            'body': 'Take aspirin',
            'scheduledTime': '2025-06-01T09:00:00.000Z',
          },
        ],
      });
      expect(task.notifications, isNotNull);
      expect(task.notifications!.length, 1);
      expect(task.notifications!.first.title, 'Time for medication');
      expect(task.notifications!.first.receiverId, 1);
    });
  });

  // ─── Task.toJson ──────────────────────────────────────────────────────────────

  group('Task.toJson', () {
    test('serializes all populated fields correctly', () {
      // Verifies JSON output for a fully-populated Task.
      final task = Task(
        id: 10,
        name: 'Blood draw',
        description: 'Fasting required',
        date: DateTime(2025, 7, 4, 8, 0),
        frequency: 'weekly',
        interval: 2,
        count: 10,
        taskType: 'Lab',
        assignedPatientId: 55,
        isComplete: false,
        applyToSeries: false,
        daysOfWeek: [true, false, true, false, false, false, false],
      );
      final json = task.toJson();
      expect(json['id'], 10);
      expect(json['name'], 'Blood draw');
      expect(json['description'], 'Fasting required');
      expect(json['frequency'], 'weekly');
      expect(json['interval'], 2);
      expect(json['count'], 10);
      expect(json['taskType'], 'Lab');
      expect(json['patientId'], 55);
      expect(json['isCompleted'], isFalse);
      expect(json['date'], isA<String>());
      expect(json['daysOfWeek'], [true, false, true, false, false, false, false]);
    });

    test('id key is omitted when id is null', () {
      // Verifies that null id is not included in the serialized map.
      final task = Task(name: 'No ID', date: DateTime(2025, 1, 1));
      final json = task.toJson();
      expect(json.containsKey('id'), isFalse);
    });

    test('taskType defaults to "general" when null', () {
      // Verifies the hardcoded fallback for taskType in the JSON output.
      final task = Task(name: 'Generic', date: DateTime(2025, 1, 1));
      expect(task.toJson()['taskType'], 'general');
    });

    test('timeOfDay serialized as zero-padded HH:mm string', () {
      // Verifies that single-digit minutes are left-padded with a zero.
      final task = Task(
        name: 'Morning routine',
        date: DateTime(2025, 1, 1),
        timeOfDay: const TimeOfDay(hour: 9, minute: 5),
      );
      expect(task.toJson()['timeOfDay'], '09:05');
    });

    test('timeOfDay serialized as null in JSON when not set', () {
      // Verifies that absent TimeOfDay produces null in the output map.
      final task = Task(name: 'No time', date: DateTime(2025, 1, 1));
      expect(task.toJson()['timeOfDay'], isNull);
    });
  });

  // ─── Task.isValid ─────────────────────────────────────────────────────────────

  group('Task.isValid', () {
    test('returns true when both name and description are non-empty', () {
      // Verifies the happy path where both required text fields are populated.
      final task = Task(
        name: 'Exercise',
        description: 'Morning jog',
        date: DateTime(2025, 1, 1),
      );
      expect(task.isValid(), isTrue);
    });

    test('returns false when name is empty', () {
      // Verifies that an empty name fails validation.
      final task = Task(
        name: '',
        description: 'Has description',
        date: DateTime(2025, 1, 1),
      );
      expect(task.isValid(), isFalse);
    });

    test('returns false when description is empty (default value)', () {
      // Verifies that the default empty description fails validation.
      final task = Task(name: 'Has name', date: DateTime(2025, 1, 1));
      expect(task.isValid(), isFalse);
    });
  });

  // ─── TaskCopyWith extension ───────────────────────────────────────────────────

  group('TaskCopyWith', () {
    test('replaces specified fields while keeping unspecified ones', () {
      // Verifies that copyWith produces a new Task with targeted changes only.
      final original = Task(
        id: 1,
        name: 'Original',
        description: 'Desc',
        date: DateTime(2025, 1, 1),
        frequency: 'daily',
        interval: 1,
        isComplete: false,
      );
      final updated = original.copyWith(name: 'Updated', isComplete: true);
      expect(updated.name, 'Updated');
      expect(updated.isComplete, isTrue);
      expect(updated.id, 1);             // unchanged
      expect(updated.description, 'Desc'); // unchanged
      expect(updated.frequency, 'daily'); // unchanged
    });

    test('copyWith with no arguments produces an equivalent task', () {
      // Verifies that omitting all parameters preserves every field.
      final task = Task(
        id: 5,
        name: 'Stable',
        description: 'Stays the same',
        date: DateTime(2025, 6, 1),
        interval: 3,
        count: 10,
      );
      final copy = task.copyWith();
      expect(copy.id, task.id);
      expect(copy.name, task.name);
      expect(copy.description, task.description);
      expect(copy.interval, task.interval);
      expect(copy.count, task.count);
    });

    test('copyWith can set timeOfDay on a task that had none', () {
      // Verifies that copyWith can introduce a previously-null optional field.
      final task = Task(name: 'Task', date: DateTime(2025, 1, 1));
      expect(task.timeOfDay, isNull);
      final updated = task.copyWith(timeOfDay: const TimeOfDay(hour: 8, minute: 0));
      expect(updated.timeOfDay?.hour, 8);
    });
  });
}
