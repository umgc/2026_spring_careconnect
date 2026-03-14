// Tests for BackendQuestionDto
// (lib/features/health/virtual_check_in/models/virtual_check_in_backend_question_model.dart).
//
// Coverage strategy:
//   BackendQuestionDto is a pure Dart data class with fromJson / toJson /
//   copyWith methods.  No platform channels or I/O required.
//
//   Branches tested:
//     fromJson — all fields present; id as int; id as num cast to int;
//       required / active missing → defaults (false / true); ordinal
//       as int and as num; missing prompt defaults to ''.
//     toJson  — all fields serialized; id omitted when null.
//     copyWith — replaces targeted fields; no-arg call preserves all fields.

import 'package:flutter_test/flutter_test.dart';

import 'package:care_connect_app/features/health/virtual_check_in/models/virtual_check_in_backend_question_model.dart';
import 'package:care_connect_app/features/health/virtual_check_in/models/question_type.dart';

void main() {
  // ─── BackendQuestionDto.fromJson ──────────────────────────────────────────────

  group('BackendQuestionDto.fromJson', () {
    test('parses all fields from a complete JSON map', () {
      // Verifies every field is extracted correctly from a full backend response.
      final dto = BackendQuestionDto.fromJson({
        'id': 7,
        'prompt': 'How are you feeling today?',
        'type': 'YES_NO',
        'required': true,
        'active': true,
        'ordinal': 3,
      });
      expect(dto.id, 7);
      expect(dto.prompt, 'How are you feeling today?');
      expect(dto.type, BackendQuestionType.yesNo);
      expect(dto.required, isTrue);
      expect(dto.active, isTrue);
      expect(dto.ordinal, 3);
    });

    test('id as num is cast to int', () {
      // Verifies the (json['id'] as num).toInt() path for non-int numeric ids.
      final dto = BackendQuestionDto.fromJson({
        'id': 5.0, // num, not int
        'prompt': 'Q',
        'type': 'TEXT',
        'required': false,
        'active': true,
        'ordinal': 1,
      });
      expect(dto.id, 5);
    });

    test('missing required → defaults to false', () {
      // Verifies the null-safe default for the required field.
      final dto = BackendQuestionDto.fromJson({
        'prompt': 'Q',
        'type': 'NUMBER',
        'active': true,
        'ordinal': 0,
      });
      expect(dto.required, isFalse);
    });

    test('missing active → defaults to true', () {
      // Verifies the null-safe default for the active field.
      final dto = BackendQuestionDto.fromJson({
        'prompt': 'Q',
        'type': 'TEXT',
        'required': false,
        'ordinal': 0,
      });
      expect(dto.active, isTrue);
    });

    test('missing ordinal → defaults to 0', () {
      // Verifies the null-safe default for the ordinal sort field.
      final dto = BackendQuestionDto.fromJson({
        'prompt': 'Q',
        'type': 'TEXT',
        'required': false,
        'active': true,
      });
      expect(dto.ordinal, 0);
    });

    test('missing prompt → defaults to empty string', () {
      // Verifies the fallback when the prompt key is absent.
      final dto = BackendQuestionDto.fromJson({
        'type': 'TEXT',
        'required': false,
        'active': true,
        'ordinal': 1,
      });
      expect(dto.prompt, '');
    });

    test('ordinal as num is cast to int', () {
      // Verifies the (json['ordinal'] as num).toInt() path.
      final dto = BackendQuestionDto.fromJson({
        'prompt': 'Q',
        'type': 'TEXT',
        'required': false,
        'active': true,
        'ordinal': 2.0, // num, not int
      });
      expect(dto.ordinal, 2);
    });

    test('type "TRUE_FALSE" is parsed to trueFalse enum value', () {
      // Verifies that all BackendQuestionType values are parsed via fromWire.
      final dto = BackendQuestionDto.fromJson({
        'prompt': 'True/false question',
        'type': 'TRUE_FALSE',
        'required': true,
        'active': true,
        'ordinal': 1,
      });
      expect(dto.type, BackendQuestionType.trueFalse);
    });
  });

  // ─── BackendQuestionDto.toJson ────────────────────────────────────────────────

  group('BackendQuestionDto.toJson', () {
    test('serializes all fields including id when present', () {
      // Verifies the complete JSON output matches constructor values.
      const dto = BackendQuestionDto(
        id: 10,
        prompt: 'Pain level 0-10?',
        type: BackendQuestionType.number,
        required: true,
        active: true,
        ordinal: 2,
      );
      final json = dto.toJson();
      expect(json['id'], 10);
      expect(json['prompt'], 'Pain level 0-10?');
      expect(json['type'], 'NUMBER'); // toWire() output
      expect(json['required'], isTrue);
      expect(json['active'], isTrue);
      expect(json['ordinal'], 2);
    });

    test('id is omitted from JSON when null', () {
      // Verifies the conditional 'if (id != null)' prevents null id in output.
      const dto = BackendQuestionDto(
        prompt: 'New question',
        type: BackendQuestionType.text,
        required: false,
        active: true,
        ordinal: 0,
      );
      final json = dto.toJson();
      expect(json.containsKey('id'), isFalse);
    });

    test('fromJson → toJson round-trip preserves all fields', () {
      // Verifies that serializing then deserializing recovers the same data.
      final original = BackendQuestionDto.fromJson({
        'id': 3,
        'prompt': 'Round-trip test',
        'type': 'YES_NO',
        'required': true,
        'active': false,
        'ordinal': 5,
      });
      final json = original.toJson();
      final restored = BackendQuestionDto.fromJson(json);
      expect(restored.id, original.id);
      expect(restored.prompt, original.prompt);
      expect(restored.type, original.type);
      expect(restored.required, original.required);
      expect(restored.active, original.active);
      expect(restored.ordinal, original.ordinal);
    });
  });

  // ─── BackendQuestionDto.copyWith ──────────────────────────────────────────────

  group('BackendQuestionDto.copyWith', () {
    const base = BackendQuestionDto(
      id: 1,
      prompt: 'Original prompt',
      type: BackendQuestionType.text,
      required: false,
      active: true,
      ordinal: 0,
    );

    test('replaces specified fields while preserving others', () {
      // Verifies that only the supplied fields are changed.
      final updated = base.copyWith(prompt: 'New prompt', required: true);
      expect(updated.prompt, 'New prompt');
      expect(updated.required, isTrue);
      expect(updated.id, 1);           // unchanged
      expect(updated.type, BackendQuestionType.text); // unchanged
      expect(updated.active, isTrue);  // unchanged
      expect(updated.ordinal, 0);      // unchanged
    });

    test('no-arg copyWith returns an equivalent object', () {
      // Verifies that omitting all parameters preserves every field.
      final copy = base.copyWith();
      expect(copy.id, base.id);
      expect(copy.prompt, base.prompt);
      expect(copy.type, base.type);
      expect(copy.required, base.required);
      expect(copy.active, base.active);
      expect(copy.ordinal, base.ordinal);
    });

    test('can change type via copyWith', () {
      // Verifies that the enum field is replaced correctly.
      final updated = base.copyWith(type: BackendQuestionType.yesNo);
      expect(updated.type, BackendQuestionType.yesNo);
    });
  });
}
