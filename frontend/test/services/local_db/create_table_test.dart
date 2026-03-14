// Tests for CreateTable.
//
// Coverage strategy:
//   CreateTable is a thin, pure-Dart façade over the generated SQL map in
//   jpa_drift_bundle.dart.  All methods are deterministic and have no external
//   dependencies, so every branch is exercised directly.
//
//   Branches tested:
//     • forTable(knownName)   — returns the SQL string for that table.
//     • forTable(unknownName) — returns null.
//     • all()                 — returns a map containing all known tables.

import 'package:flutter_test/flutter_test.dart';

import 'package:care_connect_app/services/local_db/create_table.dart';

void main() {
  // ─── forTable ─────────────────────────────────────────────────────────────

  group('CreateTable.forTable()', () {
    test('returns non-null SQL for the "moods" table', () {
      // Verifies that the moods table definition is present in the generated
      // bundle and is successfully forwarded to callers.
      expect(CreateTable.forTable('moods'), isNotNull);
    });

    test('returns non-null SQL for the "tasks" table', () {
      // Verifies the same for the tasks table.
      expect(CreateTable.forTable('tasks'), isNotNull);
    });

    test('moods SQL contains CREATE TABLE statement', () {
      // Verifies the content is a valid DDL statement, not just any string.
      expect(CreateTable.forTable('moods'), contains('CREATE TABLE'));
    });

    test('tasks SQL contains CREATE TABLE statement', () {
      expect(CreateTable.forTable('tasks'), contains('CREATE TABLE'));
    });

    test('moods SQL references the moods table name', () {
      // Confirms the SQL targets the correct table rather than a copy-paste
      // error from another table definition.
      expect(CreateTable.forTable('moods'), contains('moods'));
    });

    test('tasks SQL references the tasks table name', () {
      expect(CreateTable.forTable('tasks'), contains('tasks'));
    });

    test('returns null for an unknown table name', () {
      // Verifies the null-return path for tables that have not been generated.
      expect(CreateTable.forTable('nonexistent_table'), isNull);
    });

    test('returns null for an empty string', () {
      // An empty string is a degenerate key that should not match any table.
      expect(CreateTable.forTable(''), isNull);
    });

    test('lookup is case-insensitive via toLowerCase normalisation', () {
      // The implementation lowercases the input, so "MOODS" and "moods" map
      // to the same SQL.
      expect(CreateTable.forTable('MOODS'), isNotNull);
      expect(CreateTable.forTable('MOODS'), CreateTable.forTable('moods'));
    });

    test('returns a non-empty string for known tables', () {
      // The SQL must actually contain characters, not be an empty placeholder.
      expect(CreateTable.forTable('moods'), isNotEmpty);
      expect(CreateTable.forTable('tasks'), isNotEmpty);
    });
  });

  // ─── all ──────────────────────────────────────────────────────────────────

  group('CreateTable.all()', () {
    test('returns a non-empty map', () {
      // At least one table must be present; an empty map would mean no tables
      // are managed offline.
      expect(CreateTable.all(), isNotEmpty);
    });

    test('map contains the "moods" key', () {
      expect(CreateTable.all(), containsPair('moods', isNotNull));
    });

    test('map contains the "tasks" key', () {
      expect(CreateTable.all(), containsPair('tasks', isNotNull));
    });

    test('map has exactly 2 entries', () {
      // Asserts the number of generated tables is stable; unexpected additions
      // or removals are caught here.
      expect(CreateTable.all(), hasLength(2));
    });

    test('returns a copy so mutations do not affect subsequent calls', () {
      // Verifies the defensive-copy contract: modifying the returned map must
      // not corrupt the internal state.
      final first = CreateTable.all();
      first['injected'] = 'injected SQL';
      final second = CreateTable.all();
      expect(second.containsKey('injected'), isFalse);
    });

    test('each value in all() is a non-empty SQL string', () {
      for (final entry in CreateTable.all().entries) {
        expect(entry.value, isNotEmpty,
            reason: 'SQL for "${entry.key}" should not be empty');
        expect(entry.value, contains('CREATE TABLE'),
            reason: '${entry.key} SQL should contain a CREATE TABLE statement');
      }
    });
  });
}
