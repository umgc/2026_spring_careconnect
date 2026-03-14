// Tests for offline_table_config.dart.
//
// Coverage strategy:
//   The file exposes a single compile-time constant Set<String> that lists
//   tables for which offline persistence is enabled.  Tests verify the set's
//   contents and size so that accidental regressions (e.g. a table being
//   removed) are caught immediately.

import 'package:flutter_test/flutter_test.dart';

import 'package:care_connect_app/services/local_db/offline_table_config.dart';

void main() {
  group('offlineEnabledTables', () {
    test('is not empty', () {
      // The constant must name at least one table; an empty set would mean
      // no offline data can be persisted.
      expect(offlineEnabledTables, isNotEmpty);
    });

    test('contains the moods table', () {
      // Mood data must be available offline so patients can log their mood
      // without an internet connection.
      expect(offlineEnabledTables, contains('moods'));
    });

    test('contains the tasks table', () {
      // Task data must be available offline for caregivers to view schedules
      // when the backend is unreachable.
      expect(offlineEnabledTables, contains('tasks'));
    });

    test('has exactly 2 entries', () {
      // The set must not grow or shrink silently; changes to enabled tables
      // require deliberate review of the offline storage strategy.
      expect(offlineEnabledTables, hasLength(2));
    });

    test('is a Set (no duplicates)', () {
      // A Set guarantees uniqueness; every name appears at most once.
      expect(offlineEnabledTables, isA<Set<String>>());
    });

    test('all entries are lowercase strings', () {
      // Table names are compared case-sensitively when routing CREATE TABLE
      // SQL, so lowercase is required to match backend table names.
      for (final name in offlineEnabledTables) {
        expect(name, equals(name.toLowerCase()),
            reason: '"$name" should be all lowercase');
      }
    });
  });
}
