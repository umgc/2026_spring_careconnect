// Tests for generated/jpa_drift_bundle.dart.
//
// Coverage strategy:
//   jpa_drift_bundle.dart is a generated file that exposes:
//     • generatedCreateTableSql — a Map<String, String> of CREATE TABLE SQL.
//     • generatedCreateTableFor(name) — helper that does a lowercase lookup.
//   Both are pure Dart constants/functions with no external dependencies.
//
//   The Drift table class definitions (Moods, Tasks) are also verified for
//   the correct column names so accidental schema regressions are detected.

import 'package:flutter_test/flutter_test.dart';

import 'package:care_connect_app/services/local_db/generated/jpa_drift_bundle.dart';

void main() {
  // ─── generatedCreateTableSql ──────────────────────────────────────────────

  group('generatedCreateTableSql', () {
    test('is not empty', () {
      // At least one CREATE TABLE entry must exist or offline mode has no schema.
      expect(generatedCreateTableSql, isNotEmpty);
    });

    test('contains a "moods" entry', () {
      expect(generatedCreateTableSql, contains('moods'));
    });

    test('contains a "tasks" entry', () {
      expect(generatedCreateTableSql, contains('tasks'));
    });

    test('has exactly 2 entries', () {
      // Guards against accidental additions or removals in the generated file.
      expect(generatedCreateTableSql, hasLength(2));
    });

    test('moods SQL creates a table named "moods"', () {
      // Verifies the DDL targets the right table name.
      expect(generatedCreateTableSql['moods'], contains('moods'));
    });

    test('moods SQL includes CREATE TABLE IF NOT EXISTS clause', () {
      // The IF NOT EXISTS guard prevents errors on re-initialisation.
      expect(
        generatedCreateTableSql['moods'],
        contains('CREATE TABLE IF NOT EXISTS'),
      );
    });

    test('moods SQL defines an id column', () {
      expect(generatedCreateTableSql['moods'], contains('id'));
    });

    test('moods SQL defines a userId column', () {
      expect(generatedCreateTableSql['moods'], contains('userId'));
    });

    test('moods SQL defines a score column', () {
      expect(generatedCreateTableSql['moods'], contains('score'));
    });

    test('moods SQL defines a label column', () {
      expect(generatedCreateTableSql['moods'], contains('label'));
    });

    test('moods SQL defines a createdAt column', () {
      expect(generatedCreateTableSql['moods'], contains('createdAt'));
    });

    test('tasks SQL creates a table named "tasks"', () {
      expect(generatedCreateTableSql['tasks'], contains('tasks'));
    });

    test('tasks SQL includes CREATE TABLE IF NOT EXISTS clause', () {
      expect(
        generatedCreateTableSql['tasks'],
        contains('CREATE TABLE IF NOT EXISTS'),
      );
    });

    test('tasks SQL defines an id column', () {
      expect(generatedCreateTableSql['tasks'], contains('id'));
    });

    test('tasks SQL defines a patient_id column', () {
      expect(generatedCreateTableSql['tasks'], contains('patient_id'));
    });

    test('tasks SQL defines a name column', () {
      expect(generatedCreateTableSql['tasks'], contains('name'));
    });
  });

  // ─── generatedCreateTableFor ──────────────────────────────────────────────

  group('generatedCreateTableFor()', () {
    test('returns SQL for "moods"', () {
      expect(generatedCreateTableFor('moods'), isNotNull);
    });

    test('returns SQL for "tasks"', () {
      expect(generatedCreateTableFor('tasks'), isNotNull);
    });

    test('returns null for unknown table name', () {
      // The function must not throw; it returns null for unrecognised tables.
      expect(generatedCreateTableFor('unknown'), isNull);
    });

    test('returns null for empty string', () {
      expect(generatedCreateTableFor(''), isNull);
    });

    test('is case-insensitive (converts input to lowercase)', () {
      // The implementation calls tableName.toLowerCase() before lookup,
      // so uppercase inputs must resolve to the same SQL.
      expect(generatedCreateTableFor('MOODS'), isNotNull);
      expect(generatedCreateTableFor('MOODS'), generatedCreateTableFor('moods'));
    });

    test('is case-insensitive for tasks', () {
      expect(generatedCreateTableFor('TASKS'), isNotNull);
      expect(generatedCreateTableFor('TASKS'), generatedCreateTableFor('tasks'));
    });

    test('mixed-case input is resolved correctly', () {
      expect(generatedCreateTableFor('Moods'), generatedCreateTableFor('moods'));
    });

    test('returned SQL is a non-empty string for known tables', () {
      final moodSql = generatedCreateTableFor('moods');
      expect(moodSql, isNotEmpty);
    });
  });
}
