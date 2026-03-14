// Tests for Patient, MoodEntry, and SymptomEntry models
// (lib/features/health/caregiver-patient-list/models/).
//
// Coverage strategy:
//   All three classes are pure Dart data models with no platform channels or
//   network I/O.  Patient has a computed getter (fullName) and a default value
//   for messageCount.  MoodEntry and SymptomEntry have only constructors.
//
//   Branches tested (Patient):
//     constructor  — all required fields stored; messageCount defaults to 0.
//     fullName     — returns "firstName lastName" concatenated with a space.
//
//   Branches tested (MoodEntry):
//     constructor  — all required fields stored; optional note is null by default.
//     constructor  — optional note can be provided explicitly.
//
//   Branches tested (SymptomEntry):
//     constructor  — all required fields stored; optional note is null by default.
//     constructor  — optional note can be provided explicitly.

import 'package:flutter_test/flutter_test.dart';

import 'package:care_connect_app/features/health/caregiver-patient-list/models/patient-info.dart';
import 'package:care_connect_app/features/health/caregiver-patient-list/models/mood_entry.dart';
import 'package:care_connect_app/features/health/caregiver-patient-list/models/symptom_entry.dart';

void main() {
  // ─── Patient ──────────────────────────────────────────────────────────────────

  group('Patient constructor', () {
    final now = DateTime(2025, 6, 1);
    final nextCheckIn = DateTime(2025, 6, 8);

    test('stores all required fields', () {
      // Verifies that every required field is accessible after construction.
      final patient = Patient(
        id: 'p-001',
        firstName: 'Alice',
        lastName: 'Smith',
        lastUpdated: now,
        statusMessage: 'Feeling well.',
        nextCheckIn: nextCheckIn,
        mood: 'Happy',
        moodEmoji: '😀',
        isUrgent: false,
      );
      expect(patient.id, 'p-001');
      expect(patient.firstName, 'Alice');
      expect(patient.lastName, 'Smith');
      expect(patient.lastUpdated, now);
      expect(patient.statusMessage, 'Feeling well.');
      expect(patient.nextCheckIn, nextCheckIn);
      expect(patient.mood, 'Happy');
      expect(patient.moodEmoji, '😀');
      expect(patient.isUrgent, isFalse);
    });

    test('messageCount defaults to 0 when not provided', () {
      // Verifies the default value for the optional messageCount parameter.
      final patient = Patient(
        id: 'p-002',
        firstName: 'Bob',
        lastName: 'Jones',
        lastUpdated: now,
        statusMessage: 'OK',
        nextCheckIn: nextCheckIn,
        mood: 'Calm',
        moodEmoji: '😐',
        isUrgent: false,
      );
      expect(patient.messageCount, 0);
    });

    test('messageCount is stored when explicitly provided', () {
      // Verifies that a non-zero message count is honored.
      final patient = Patient(
        id: 'p-003',
        firstName: 'Carol',
        lastName: 'White',
        lastUpdated: now,
        statusMessage: 'Has messages',
        nextCheckIn: nextCheckIn,
        mood: 'Anxious',
        moodEmoji: '😰',
        isUrgent: true,
        messageCount: 5,
      );
      expect(patient.messageCount, 5);
      expect(patient.isUrgent, isTrue);
    });
  });

  group('Patient.fullName', () {
    test('returns firstName and lastName joined by a space', () {
      // Verifies the computed getter combines both name parts correctly.
      final patient = Patient(
        id: 'p-004',
        firstName: 'David',
        lastName: 'Brown',
        lastUpdated: DateTime(2025, 1, 1),
        statusMessage: '',
        nextCheckIn: DateTime(2025, 1, 8),
        mood: 'Neutral',
        moodEmoji: '😶',
        isUrgent: false,
      );
      expect(patient.fullName, 'David Brown');
    });

    test('handles single-word names without extra spaces', () {
      // Verifies that firstName + space + lastName is the exact format.
      final patient = Patient(
        id: 'p-005',
        firstName: 'Eve',
        lastName: 'Lee',
        lastUpdated: DateTime(2025, 2, 1),
        statusMessage: '',
        nextCheckIn: DateTime(2025, 2, 8),
        mood: 'Happy',
        moodEmoji: '😄',
        isUrgent: false,
      );
      expect(patient.fullName, 'Eve Lee');
      // Ensure there is exactly one space between names.
      expect(patient.fullName.split(' ').length, 2);
    });
  });

  // ─── MoodEntry ────────────────────────────────────────────────────────────────

  group('MoodEntry constructor', () {
    test('stores all required fields; note defaults to null', () {
      // Verifies required fields are stored and the optional note is null.
      final entry = MoodEntry(
        id: 'm-1',
        date: DateTime(2025, 6, 1),
        score10: 8,
        label: 'Happy',
        emoji: '😀',
      );
      expect(entry.id, 'm-1');
      expect(entry.date.year, 2025);
      expect(entry.score10, 8);
      expect(entry.label, 'Happy');
      expect(entry.emoji, '😀');
      expect(entry.note, isNull);
    });

    test('stores optional note when provided', () {
      // Verifies that a note string is stored when explicitly supplied.
      final entry = MoodEntry(
        id: 'm-2',
        date: DateTime(2025, 6, 2),
        score10: 5,
        label: 'Anxious',
        emoji: '😰',
        note: 'Worried about appointment.',
      );
      expect(entry.note, 'Worried about appointment.');
    });

    test('score10 boundary values are stored correctly', () {
      // Verifies extremes of the 0–10 scale can be stored.
      final low = MoodEntry(
        id: 'm-3', date: DateTime(2025, 1, 1),
        score10: 0, label: 'Very low', emoji: '😞',
      );
      final high = MoodEntry(
        id: 'm-4', date: DateTime(2025, 1, 2),
        score10: 10, label: 'Excellent', emoji: '😁',
      );
      expect(low.score10, 0);
      expect(high.score10, 10);
    });
  });

  // ─── SymptomEntry ─────────────────────────────────────────────────────────────

  group('SymptomEntry constructor', () {
    test('stores all required fields; note defaults to null', () {
      // Verifies required fields are stored and the optional note is null.
      final entry = SymptomEntry(
        id: 's-1',
        date: DateTime(2025, 5, 20),
        name: 'Headache',
        severity: 'Mild',
      );
      expect(entry.id, 's-1');
      expect(entry.date.month, 5);
      expect(entry.name, 'Headache');
      expect(entry.severity, 'Mild');
      expect(entry.note, isNull);
    });

    test('stores optional note when provided', () {
      // Verifies that a note string is stored when explicitly supplied.
      final entry = SymptomEntry(
        id: 's-2',
        date: DateTime(2025, 5, 21),
        name: 'Fatigue',
        severity: 'Moderate',
        note: 'Feeling tired after lunch.',
      );
      expect(entry.note, 'Feeling tired after lunch.');
    });

    test('all three severity levels are stored correctly', () {
      // Verifies that the severity string is preserved without modification.
      for (final sev in ['Mild', 'Moderate', 'Severe']) {
        final entry = SymptomEntry(
          id: 's-$sev',
          date: DateTime(2025, 6, 1),
          name: 'Pain',
          severity: sev,
        );
        expect(entry.severity, sev);
      }
    });
  });
}
