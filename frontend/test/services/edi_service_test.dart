// Tests for EdiService static utility methods.
//
// Coverage strategy:
//   EdiService contains many pure static helpers that perform date/time
//   formatting, code mapping, unit/charge calculation, and EDI content
//   generation.  All are exercised without HTTP or platform channels.
//
//   exportVisitData uses universal_html (web-only) and is skipped.
//   generateEDIContent requires a Patient model and is tested via
//   generateMockEdi837 / generateMockEdiWithDetails which exercise the same
//   formatting paths with default/custom parameters.
//
//   Branches tested:
//     validateEDIContent — empty string, missing segment, all segments present.
//     parseServiceTypeToCode — known services and unknown fallback.
//     calculateBillableUnits — exact multiple of 900, remainder, zero.
//     calculateTotalCharge — default rate and custom rate.
//     formatMANumber — existing MA number, null/empty generates from patientId.
//     generateControlNumber — returns 9-char numeric string.
//     formatEDIDate — YYYYMMDD zero-padded.
//     formatEDITime — HHMM zero-padded.
//     formatISADate — YYMMDD zero-padded.
//     sanitizeNotes — removes ~, *, : characters.
//     generateMockEdi837 — default params, custom params.
//     generateMockEdiWithDetails — uses custom patient/service/duration.

import 'package:flutter_test/flutter_test.dart';

import 'package:care_connect_app/services/edi_service.dart';

void main() {
  // ─── validateEDIContent ──────────────────────────────────────────────────

  group('EdiService.validateEDIContent', () {
    test('empty string → false', () {
      expect(EdiService.validateEDIContent(''), isFalse);
    });

    test('missing ISA segment → false', () {
      const content = 'GS*HC~\nST*837~\nBHT~\nSE~\nGE~\nIEA~';
      expect(EdiService.validateEDIContent(content), isFalse);
    });

    test('all required segments present → true', () {
      const content = 'ISA~\nGS~\nST~\nBHT~\nSE~\nGE~\nIEA~';
      expect(EdiService.validateEDIContent(content), isTrue);
    });
  });

  // ─── parseServiceTypeToCode ──────────────────────────────────────────────

  group('EdiService.parseServiceTypeToCode', () {
    test('Personal Care → T1019', () {
      expect(EdiService.parseServiceTypeToCode('Personal Care'), 'T1019');
    });

    test('Companion Care → S5125', () {
      expect(EdiService.parseServiceTypeToCode('Companion Care'), 'S5125');
    });

    test('Respite Care → T1005', () {
      expect(EdiService.parseServiceTypeToCode('Respite Care'), 'T1005');
    });

    test('Homemaker Services → S5130', () {
      expect(EdiService.parseServiceTypeToCode('Homemaker Services'), 'S5130');
    });

    test('Skilled Nursing → 99601', () {
      expect(EdiService.parseServiceTypeToCode('Skilled Nursing'), '99601');
    });

    test('Physical Therapy → 97110', () {
      expect(EdiService.parseServiceTypeToCode('Physical Therapy'), '97110');
    });

    test('Occupational Therapy → 97530', () {
      expect(EdiService.parseServiceTypeToCode('Occupational Therapy'), '97530');
    });

    test('Speech Therapy → 92507', () {
      expect(EdiService.parseServiceTypeToCode('Speech Therapy'), '92507');
    });

    test('unknown service type → T1019 default', () {
      expect(EdiService.parseServiceTypeToCode('Unknown'), 'T1019');
    });
  });

  // ─── calculateBillableUnits ──────────────────────────────────────────────

  group('EdiService.calculateBillableUnits', () {
    test('900 s (exactly 1 unit) → 1', () {
      expect(EdiService.calculateBillableUnits(900), 1);
    });

    test('1800 s (exactly 2 units) → 2', () {
      expect(EdiService.calculateBillableUnits(1800), 2);
    });

    test('901 s (just over 1 unit) → 2 (ceiling)', () {
      expect(EdiService.calculateBillableUnits(901), 2);
    });

    test('3600 s (4 units) → 4', () {
      expect(EdiService.calculateBillableUnits(3600), 4);
    });

    test('1 s → 1 (ceiling of tiny value)', () {
      expect(EdiService.calculateBillableUnits(1), 1);
    });
  });

  // ─── calculateTotalCharge ────────────────────────────────────────────────

  group('EdiService.calculateTotalCharge', () {
    test('900 s with default rate (30.0) → 30.0', () {
      expect(EdiService.calculateTotalCharge(900), 30.0);
    });

    test('1800 s with default rate → 60.0', () {
      expect(EdiService.calculateTotalCharge(1800), 60.0);
    });

    test('1800 s with custom rate 50.0 → 100.0', () {
      expect(EdiService.calculateTotalCharge(1800, ratePerUnit: 50.0), 100.0);
    });

    test('901 s with default rate → 60.0 (ceiling 2 units)', () {
      expect(EdiService.calculateTotalCharge(901), 60.0);
    });
  });

  // ─── formatMANumber ──────────────────────────────────────────────────────

  group('EdiService.formatMANumber', () {
    test('existing MA number is returned unchanged', () {
      expect(EdiService.formatMANumber(42, 'MA123'), 'MA123');
    });

    test('null MA number → generated from patientId', () {
      final result = EdiService.formatMANumber(7, null);
      expect(result, startsWith('MA'));
      expect(result, contains('7'));
    });

    test('empty MA number → generated from patientId', () {
      final result = EdiService.formatMANumber(99, '');
      expect(result, startsWith('MA'));
    });
  });

  // ─── generateControlNumber ───────────────────────────────────────────────

  group('EdiService.generateControlNumber', () {
    test('returns a 9-character numeric string', () {
      final cn = EdiService.generateControlNumber();
      expect(cn.length, 9);
      expect(int.tryParse(cn), isNotNull);
    });
  });

  // ─── formatEDIDate ───────────────────────────────────────────────────────

  group('EdiService.formatEDIDate', () {
    test('2025-01-05 → 20250105 (zero-padded month and day)', () {
      final d = DateTime(2025, 1, 5);
      expect(EdiService.formatEDIDate(d), '20250105');
    });

    test('2024-12-31 → 20241231', () {
      final d = DateTime(2024, 12, 31);
      expect(EdiService.formatEDIDate(d), '20241231');
    });
  });

  // ─── formatEDITime ───────────────────────────────────────────────────────

  group('EdiService.formatEDITime', () {
    test('09:05 → 0905 (zero-padded)', () {
      final t = DateTime(2024, 1, 1, 9, 5);
      expect(EdiService.formatEDITime(t), '0905');
    });

    test('14:30 → 1430', () {
      final t = DateTime(2024, 1, 1, 14, 30);
      expect(EdiService.formatEDITime(t), '1430');
    });
  });

  // ─── formatISADate ───────────────────────────────────────────────────────

  group('EdiService.formatISADate', () {
    test('2025-01-05 → 250105 (YYMMDD)', () {
      final d = DateTime(2025, 1, 5);
      expect(EdiService.formatISADate(d), '250105');
    });

    test('2024-12-31 → 241231', () {
      final d = DateTime(2024, 12, 31);
      expect(EdiService.formatISADate(d), '241231');
    });
  });

  // ─── sanitizeNotes ───────────────────────────────────────────────────────

  group('EdiService.sanitizeNotes', () {
    test('removes ~ characters', () {
      expect(EdiService.sanitizeNotes('hello~world'), 'helloworld');
    });

    test('removes * characters', () {
      expect(EdiService.sanitizeNotes('a*b'), 'ab');
    });

    test('removes : characters', () {
      expect(EdiService.sanitizeNotes('x:y'), 'xy');
    });

    test('removes all special characters', () {
      expect(EdiService.sanitizeNotes('a~b*c:d'), 'abcd');
    });

    test('plain string is unchanged', () {
      expect(EdiService.sanitizeNotes('plain text'), 'plain text');
    });
  });

  // ─── generateMockEdi837 ──────────────────────────────────────────────────

  group('EdiService.generateMockEdi837', () {
    test('default params produces valid EDI content', () {
      final edi = EdiService.generateMockEdi837();
      expect(EdiService.validateEDIContent(edi), isTrue);
    });

    test('custom patient name appears in output', () {
      final edi = EdiService.generateMockEdi837(
        patientFirstName: 'Alice',
        patientLastName: 'Smith',
      );
      expect(edi, contains('Alice'));
      expect(edi, contains('Smith'));
    });

    test('custom MA number appears in output', () {
      final edi = EdiService.generateMockEdi837(maNumber: 'MATEST99');
      expect(edi, contains('MATEST99'));
    });

    test('custom service type code appears in output', () {
      final edi = EdiService.generateMockEdi837(serviceType: 'Skilled Nursing');
      // 99601 is the EDI code for Skilled Nursing
      expect(edi, contains('99601'));
    });
  });

  // ─── generateMockEdiWithDetails ──────────────────────────────────────────

  group('EdiService.generateMockEdiWithDetails', () {
    test('produces valid EDI content with custom parameters', () {
      final edi = EdiService.generateMockEdiWithDetails(
        patientId: '42',
        patientFirstName: 'Bob',
        patientLastName: 'Jones',
        serviceType: 'Personal Care',
        serviceDate: DateTime(2025, 6, 15),
        durationMinutes: 60,
      );
      expect(EdiService.validateEDIContent(edi), isTrue);
      expect(edi, contains('Bob'));
      expect(edi, contains('Jones'));
    });

    test('provided MA number is used instead of generated one', () {
      final edi = EdiService.generateMockEdiWithDetails(
        patientId: '1',
        patientFirstName: 'X',
        patientLastName: 'Y',
        serviceType: 'Personal Care',
        serviceDate: DateTime(2025, 1, 1),
        durationMinutes: 30,
        maNumber: 'CUSTOM-MA',
      );
      expect(edi, contains('CUSTOM-MA'));
    });
  });
}
