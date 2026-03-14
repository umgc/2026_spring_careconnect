// Tests for BackendQuestionType enum
// (lib/features/health/virtual_check_in/models/question_type.dart).
//
// Coverage strategy:
//   BackendQuestionType is a pure Dart enum with two pure methods:
//     fromWire — converts a backend string to an enum value, normalizing
//       case and separators; handles all four values plus aliases and fallback.
//     toWire  — converts each enum value back to the backend string.
//   No platform channels or I/O required.
//
//   Branches tested:
//     fromWire — 'TEXT', 'YES_NO', 'TRUE_FALSE', 'NUMBER', 'NUMERIC' alias;
//       lowercase inputs; null and empty string fallback to text;
//       whitespace-padded and hyphen-separated inputs.
//     toWire  — all four enum values produce the correct wire string.

import 'package:flutter_test/flutter_test.dart';

import 'package:care_connect_app/features/health/virtual_check_in/models/question_type.dart';

void main() {
  // ─── BackendQuestionType.fromWire ─────────────────────────────────────────────

  group('BackendQuestionType.fromWire', () {
    test('"TEXT" → text', () {
      // Verifies the primary text type is recognized.
      expect(BackendQuestionType.fromWire('TEXT'), BackendQuestionType.text);
    });

    test('"YES_NO" → yesNo', () {
      // Verifies the yes/no type is recognized.
      expect(BackendQuestionType.fromWire('YES_NO'), BackendQuestionType.yesNo);
    });

    test('"TRUE_FALSE" → trueFalse', () {
      // Verifies the true/false type is recognized.
      expect(BackendQuestionType.fromWire('TRUE_FALSE'), BackendQuestionType.trueFalse);
    });

    test('"NUMBER" → number', () {
      // Verifies the numeric type is recognized.
      expect(BackendQuestionType.fromWire('NUMBER'), BackendQuestionType.number);
    });

    test('"NUMERIC" alias → number', () {
      // Verifies the NUMERIC alias is tolerated and mapped to number.
      expect(BackendQuestionType.fromWire('NUMERIC'), BackendQuestionType.number);
    });

    test('lowercase input is normalized correctly', () {
      // Verifies toUpperCase() normalization for all four base values.
      expect(BackendQuestionType.fromWire('text'), BackendQuestionType.text);
      expect(BackendQuestionType.fromWire('yes_no'), BackendQuestionType.yesNo);
      expect(BackendQuestionType.fromWire('true_false'), BackendQuestionType.trueFalse);
      expect(BackendQuestionType.fromWire('number'), BackendQuestionType.number);
    });

    test('hyphen separator is converted to underscore', () {
      // Verifies replaceAll('-', '_') normalization.
      expect(BackendQuestionType.fromWire('YES-NO'), BackendQuestionType.yesNo);
      expect(BackendQuestionType.fromWire('TRUE-FALSE'), BackendQuestionType.trueFalse);
    });

    test('whitespace-padded input is trimmed', () {
      // Verifies trim() normalization removes surrounding whitespace.
      expect(BackendQuestionType.fromWire('  TEXT  '), BackendQuestionType.text);
    });

    test('null input falls back to text', () {
      // Verifies that null is treated as empty string and falls through to default.
      expect(BackendQuestionType.fromWire(null), BackendQuestionType.text);
    });

    test('empty string falls back to text', () {
      // Verifies that an empty string hits the default branch.
      expect(BackendQuestionType.fromWire(''), BackendQuestionType.text);
    });

    test('unknown string falls back to text', () {
      // Verifies the wildcard default returns text without crashing.
      expect(BackendQuestionType.fromWire('UNKNOWN_TYPE'), BackendQuestionType.text);
    });
  });

  // ─── BackendQuestionType.toWire ───────────────────────────────────────────────

  group('BackendQuestionType.toWire', () {
    test('text → "TEXT"', () {
      expect(BackendQuestionType.text.toWire(), 'TEXT');
    });

    test('yesNo → "YES_NO"', () {
      expect(BackendQuestionType.yesNo.toWire(), 'YES_NO');
    });

    test('trueFalse → "TRUE_FALSE"', () {
      expect(BackendQuestionType.trueFalse.toWire(), 'TRUE_FALSE');
    });

    test('number → "NUMBER"', () {
      expect(BackendQuestionType.number.toWire(), 'NUMBER');
    });

    test('toWire round-trips through fromWire for all values', () {
      // Verifies that toWire output can always be re-parsed by fromWire.
      for (final value in BackendQuestionType.values) {
        expect(BackendQuestionType.fromWire(value.toWire()), value);
      }
    });
  });
}
