// Tests for virtual_check_in_mapper.dart functions
// (lib/features/health/virtual_check_in/models/virtual_check_in_mapper.dart).
//
// Coverage strategy:
//   The mapper file exposes two pure top-level functions with no side effects:
//     mapTypeToUi — maps all three BackendQuestionType variants (number,
//       yesNo+trueFalse combined, text) to CheckInQuestionType values.
//     toUiQuestion — converts a BackendQuestionDto into a VirtualCheckInQuestion,
//       verifying id coercion (int→String), type mapping, required flag, and text.
//   No platform channels or I/O required.
//
//   Branches tested:
//     mapTypeToUi — BackendQuestionType.number → numerical;
//       BackendQuestionType.yesNo → yesNo; BackendQuestionType.trueFalse → yesNo;
//       BackendQuestionType.text → textInput.
//     toUiQuestion — id converted to string; prompt mapped to text field;
//       required flag passed through; all three CheckInQuestionType values
//       produced by routing through all BackendQuestionType variants.

import 'package:flutter_test/flutter_test.dart';

import 'package:care_connect_app/features/health/virtual_check_in/models/virtual_check_in_mapper.dart';
import 'package:care_connect_app/features/health/virtual_check_in/models/virtual_check_in_backend_question_model.dart';
import 'package:care_connect_app/features/health/virtual_check_in/models/question_type.dart';
import 'package:care_connect_app/features/health/virtual_check_in/models/virtual_check_in_question.dart';

// ─── Helper ────────────────────────────────────────────────────────────────────

BackendQuestionDto makeDto({
  int id = 1,
  String prompt = 'Test question',
  BackendQuestionType type = BackendQuestionType.text,
  bool required = false,
  bool active = true,
  int ordinal = 0,
}) {
  return BackendQuestionDto(
    id: id,
    prompt: prompt,
    type: type,
    required: required,
    active: active,
    ordinal: ordinal,
  );
}

void main() {
  // ─── mapTypeToUi ──────────────────────────────────────────────────────────────

  group('mapTypeToUi', () {
    test('BackendQuestionType.number → CheckInQuestionType.numerical', () {
      // Verifies the numeric type is mapped to the numerical UI type.
      expect(mapTypeToUi(BackendQuestionType.number), CheckInQuestionType.numerical);
    });

    test('BackendQuestionType.yesNo → CheckInQuestionType.yesNo', () {
      // Verifies yesNo maps to the yesNo UI type.
      expect(mapTypeToUi(BackendQuestionType.yesNo), CheckInQuestionType.yesNo);
    });

    test('BackendQuestionType.trueFalse → CheckInQuestionType.yesNo', () {
      // Verifies trueFalse shares the same UI type as yesNo (combined branch).
      expect(mapTypeToUi(BackendQuestionType.trueFalse), CheckInQuestionType.yesNo);
    });

    test('BackendQuestionType.text → CheckInQuestionType.textInput', () {
      // Verifies the text type is mapped to the textInput UI type.
      expect(mapTypeToUi(BackendQuestionType.text), CheckInQuestionType.textInput);
    });
  });

  // ─── toUiQuestion ─────────────────────────────────────────────────────────────

  group('toUiQuestion', () {
    test('maps id (int) to string in the returned VirtualCheckInQuestion', () {
      // Verifies dto.id.toString() is used for the UI question id field.
      final dto = makeDto(id: 42);
      final question = toUiQuestion(dto);
      expect(question.id, '42');
    });

    test('maps prompt to text field', () {
      // Verifies the prompt string becomes the UI question text.
      final dto = makeDto(prompt: 'How do you feel?');
      final question = toUiQuestion(dto);
      expect(question.text, 'How do you feel?');
    });

    test('maps required flag correctly', () {
      // Verifies the required flag is passed through unchanged.
      final dtoRequired = makeDto(required: true);
      final dtoOptional = makeDto(required: false);
      expect(toUiQuestion(dtoRequired).required, isTrue);
      expect(toUiQuestion(dtoOptional).required, isFalse);
    });

    test('produces CheckInQuestionType.numerical for number type', () {
      // Verifies end-to-end type mapping for numeric questions.
      final q = toUiQuestion(makeDto(type: BackendQuestionType.number));
      expect(q.type, CheckInQuestionType.numerical);
    });

    test('produces CheckInQuestionType.yesNo for yesNo type', () {
      // Verifies end-to-end type mapping for yes/no questions.
      final q = toUiQuestion(makeDto(type: BackendQuestionType.yesNo));
      expect(q.type, CheckInQuestionType.yesNo);
    });

    test('produces CheckInQuestionType.yesNo for trueFalse type', () {
      // Verifies end-to-end type mapping for true/false questions.
      final q = toUiQuestion(makeDto(type: BackendQuestionType.trueFalse));
      expect(q.type, CheckInQuestionType.yesNo);
    });

    test('produces CheckInQuestionType.textInput for text type', () {
      // Verifies end-to-end type mapping for open-text questions.
      final q = toUiQuestion(makeDto(type: BackendQuestionType.text));
      expect(q.type, CheckInQuestionType.textInput);
    });

    test('returned object is a VirtualCheckInQuestion', () {
      // Verifies the return type is correct.
      final q = toUiQuestion(makeDto());
      expect(q, isA<VirtualCheckInQuestion>());
    });
  });
}
