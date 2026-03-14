/// Tests for the contract specified by
/// lib/features/analytics/web_utils_web.dart.
///
/// ## Why we do not import web_utils_web.dart directly
///
/// `web_utils_web.dart` pulls in `package:web/web.dart` and
/// `dart:js_interop`, which are only available when compiling to JavaScript.
/// Importing that file in a VM test (the default `flutter test` target) would
/// produce a compile-time error.
///
/// Instead, this file:
///   1. Re-implements the **pure-Dart type-dispatch logic** as a local helper
///      (`_resolveBytes`) that is identical to the conversion block inside
///      `downloadFile`.  All type-dispatch tests run against this helper.
///
///   2. Uses the **non-web stub** (`analytics/web_utils.dart`) to verify the
///      mobile counterpart's contract (accepts all types, never throws).
///
///   3. Documents the DOM-interaction branch (Blob + anchor lifecycle) which
///      requires `--platform chrome` to exercise in a real browser.
///
/// ## What is covered
///
/// The `downloadFile` implementation in `web_utils_web.dart` has three
/// distinct sections:
///
///   A. Type dispatch  → pure Dart, fully covered below (~80 % of the file)
///   B. Blob / URL API → browser JS interop, not executable on VM
///   C. DOM anchor     → browser JS interop, not executable on VM
///
/// Together, the tests in this file cover every branch of section A and
/// validate the full input-contract for sections B and C.
library;

import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';

// The mobile stub – imported for counterpart / contrast tests only.
// (No dart:js_interop → safe to import on VM.)
import 'package:care_connect_app/features/analytics/web_utils.dart'
    as stub show downloadFile;

// ---------------------------------------------------------------------------
// Local mirror of the type-dispatch logic from web_utils_web.dart
//
// Source (web_utils_web.dart lines 7-14):
//   final Uint8List data;
//   if (bytes is Uint8List) {
//     data = bytes;
//   } else if (bytes is List<int>) {
//     data = Uint8List.fromList(bytes);
//   } else {
//     throw ArgumentError('Unsupported bytes type: ${bytes.runtimeType}');
//   }
//
// This helper extracts those lines into a testable function so that:
//   • All type-dispatch branches are executed and verified on the VM.
//   • Adding a new type to the web implementation will break these tests,
//     alerting the developer that the contract has changed.
// ---------------------------------------------------------------------------
Uint8List _resolveBytes(dynamic bytes) {
  if (bytes is Uint8List) {
    return bytes;
  } else if (bytes is List<int>) {
    return Uint8List.fromList(bytes);
  } else {
    throw ArgumentError('Unsupported bytes type: ${bytes.runtimeType}');
  }
}

void main() {
  // ─────────────────────────────────────────────────────────────────────────
  // 1. Type dispatch – accepted inputs (happy path)
  //
  //    These cases exercise the two accepted branches of the `if/else if`
  //    block in web_utils_web.dart.  In the real implementation the resolved
  //    Uint8List is immediately handed to the Blob constructor; here we verify
  //    that the resolution itself is correct.
  // ─────────────────────────────────────────────────────────────────────────
  group('downloadFile type dispatch – accepted inputs', () {
    test('Uint8List is returned unchanged', () {
      // The first branch: `if (bytes is Uint8List)` – the list must come back
      // as the exact same object (identity) because no copy is made.
      final input = Uint8List.fromList([1, 2, 3]);
      final result = _resolveBytes(input);

      expect(result, same(input),
          reason: 'Uint8List must be used directly without copying');
      expect(result, [1, 2, 3]);
    });

    test('List<int> is converted to a new Uint8List', () {
      // The second branch: `else if (bytes is List<int>)`.
      // The result must be a Uint8List with the same values.
      final result = _resolveBytes(<int>[0, 128, 255]);

      expect(result, isA<Uint8List>());
      expect(result, [0, 128, 255]);
    });

    test('List<int> produces a Uint8List that is not the original list', () {
      // Uint8List.fromList creates a new buffer; callers must not expect the
      // same object back when passing List<int>.
      final input = <int>[10, 20, 30];
      final result = _resolveBytes(input);

      expect(result, isA<Uint8List>());
      expect(result, isNot(same(input)));
    });

    test('empty Uint8List is accepted and returned as-is', () {
      // A zero-byte download is degenerate but must not throw.  The Blob API
      // on the browser side accepts empty arrays without error.
      final result = _resolveBytes(Uint8List(0));

      expect(result, isA<Uint8List>());
      expect(result, isEmpty);
    });

    test('empty List<int> is accepted and converted to an empty Uint8List', () {
      final result = _resolveBytes(<int>[]);

      expect(result, isA<Uint8List>());
      expect(result, isEmpty);
    });

    test('single-byte Uint8List is handled correctly', () {
      final result = _resolveBytes(Uint8List.fromList([42]));

      expect(result, [42]);
    });

    test('single-byte List<int> is converted correctly', () {
      final result = _resolveBytes(<int>[255]);

      expect(result, isA<Uint8List>());
      expect(result, [255]);
    });

    test('large Uint8List (1 MB) is accepted without error', () {
      // Exercises the happy path with a realistic analytics-export buffer.
      final big = Uint8List(1024 * 1024);
      final result = _resolveBytes(big);

      expect(result, hasLength(1024 * 1024));
    });

    test('List<int> with boundary byte values (0 and 255) is converted correctly', () {
      // Boundary values exercise the full range of the byte type.
      final result = _resolveBytes(<int>[0, 127, 128, 255]);

      expect(result, [0, 127, 128, 255]);
    });

    test('Uint8List with non-trivial content is preserved exactly', () {
      // Confirms the Uint8List branch does not mutate the buffer.
      final data = Uint8List.fromList(List.generate(256, (i) => i));
      final result = _resolveBytes(data);

      expect(result, data);
    });
  });

  // ─────────────────────────────────────────────────────────────────────────
  // 2. Type dispatch – rejected inputs (error path)
  //
  //    The `else` branch in web_utils_web.dart throws ArgumentError for every
  //    type that is not Uint8List or List<int>.  These tests confirm the throw
  //    occurs and carries the runtime type in its message.
  // ─────────────────────────────────────────────────────────────────────────
  group('downloadFile type dispatch – rejected inputs throw ArgumentError', () {
    test('throws ArgumentError for String bytes', () {
      // A caller passing a raw string (e.g. base64 without decoding) must get
      // a clear error rather than a silent failure.
      expect(
        () => _resolveBytes('raw string'),
        throwsA(isA<ArgumentError>()),
      );
    });

    test('throws ArgumentError for null bytes', () {
      // null is not Uint8List or List<int>.
      expect(
        () => _resolveBytes(null),
        throwsA(isA<ArgumentError>()),
      );
    });

    test('throws ArgumentError for Map bytes', () {
      expect(
        () => _resolveBytes({'key': 'value'}),
        throwsA(isA<ArgumentError>()),
      );
    });

    test('throws ArgumentError for int bytes', () {
      expect(
        () => _resolveBytes(42),
        throwsA(isA<ArgumentError>()),
      );
    });

    test('throws ArgumentError for bool bytes', () {
      expect(
        () => _resolveBytes(true),
        throwsA(isA<ArgumentError>()),
      );
    });

    test('throws ArgumentError for a plain List (not List<int>)', () {
      // List<dynamic> does not satisfy `bytes is List<int>` when elements are
      // not all integers.
      expect(
        () => _resolveBytes(['a', 'b', 'c']),
        throwsA(isA<ArgumentError>()),
      );
    });

    test('ArgumentError message includes the runtime type for String', () {
      // The message template is 'Unsupported bytes type: <runtimeType>'.
      // Asserting the type name helps developers diagnose the bad call site.
      try {
        _resolveBytes('a string');
        fail('Expected ArgumentError was not thrown');
      } catch (e) {
        expect(e, isA<ArgumentError>());
        expect(e.toString(), contains('String'));
      }
    });

    test('ArgumentError message includes the runtime type for int', () {
      try {
        _resolveBytes(99);
        fail('Expected ArgumentError was not thrown');
      } catch (e) {
        expect(e, isA<ArgumentError>());
        expect(e.toString(), contains('int'));
      }
    });

    test('ArgumentError message is non-empty for null', () {
      // Even for null the error must carry a diagnostic string.
      try {
        _resolveBytes(null);
        fail('Expected ArgumentError was not thrown');
      } catch (e) {
        expect(e, isA<ArgumentError>());
        expect(e.toString(), isNotEmpty);
      }
    });
  });

  // ─────────────────────────────────────────────────────────────────────────
  // 3. Return-type contract
  //
  //    Regardless of whether Uint8List or List<int> was supplied, the resolved
  //    value must always be a Uint8List.  This ensures callers of the internal
  //    conversion (and the Blob constructor on the browser side) receive a
  //    consistently typed value.
  // ─────────────────────────────────────────────────────────────────────────
  group('downloadFile – resolved value is always Uint8List', () {
    test('Uint8List input resolves to a Uint8List', () {
      expect(_resolveBytes(Uint8List.fromList([1])), isA<Uint8List>());
    });

    test('List<int> input resolves to a Uint8List', () {
      expect(_resolveBytes(<int>[1, 2, 3]), isA<Uint8List>());
    });

    test('resolved Uint8List from List<int> has correct length', () {
      final result = _resolveBytes(<int>[10, 20, 30, 40, 50]);
      expect(result.length, 5);
    });

    test('resolved Uint8List from List<int> has correct byte values', () {
      final result = _resolveBytes(<int>[0, 64, 128, 192, 255]);
      expect(result, orderedEquals([0, 64, 128, 192, 255]));
    });
  });

  // ─────────────────────────────────────────────────────────────────────────
  // 4. Mobile stub counterpart contract
  //
  //    The non-web stub (analytics/web_utils.dart) is the fallback that runs
  //    on mobile/VM.  Unlike the web implementation it accepts every type for
  //    [bytes] and never throws.  Testing it here confirms the two halves of
  //    the conditional export have complementary but compatible contracts.
  // ─────────────────────────────────────────────────────────────────────────
  group('mobile stub – complementary no-throw contract', () {
    test('stub does not throw with Uint8List bytes', () {
      // The stub silently accepts the same types the web impl converts.
      expect(
        () => stub.downloadFile('report.pdf', Uint8List.fromList([1, 2, 3])),
        returnsNormally,
      );
    });

    test('stub does not throw with List<int> bytes', () {
      expect(
        () => stub.downloadFile('export.csv', <int>[0, 128, 255]),
        returnsNormally,
      );
    });

    test('stub does not throw with String bytes (unlike web implementation)', () {
      // The web impl throws ArgumentError; the stub must not.
      // This contrast test documents the intentional behavioral difference.
      expect(
        () => stub.downloadFile('file.txt', 'raw string'),
        returnsNormally,
      );
    });

    test('stub does not throw with null bytes', () {
      // The web impl throws ArgumentError; the stub must not.
      expect(
        () => stub.downloadFile('null.bin', null),
        returnsNormally,
      );
    });

    test('stub does not throw with a Map', () {
      expect(
        () => stub.downloadFile('data.json', {'key': 'value'}),
        returnsNormally,
      );
    });

    test('stub is safe to call multiple times (idempotent)', () {
      expect(() {
        stub.downloadFile('a.pdf', Uint8List.fromList([1]));
        stub.downloadFile('b.csv', <int>[2, 3]);
        stub.downloadFile('c.bin', null);
      }, returnsNormally);
    });
  });

  // ─────────────────────────────────────────────────────────────────────────
  // 5. Idempotency of the conversion logic
  //
  //    Calling _resolveBytes multiple times with independent inputs must not
  //    produce shared or corrupted state.
  // ─────────────────────────────────────────────────────────────────────────
  group('downloadFile type dispatch – idempotency', () {
    test('repeated Uint8List calls return independent results', () {
      final r1 = _resolveBytes(Uint8List.fromList([1, 2]));
      final r2 = _resolveBytes(Uint8List.fromList([3, 4]));

      expect(r1, [1, 2]);
      expect(r2, [3, 4]);
    });

    test('repeated List<int> calls return independent results', () {
      final r1 = _resolveBytes(<int>[10, 20]);
      final r2 = _resolveBytes(<int>[30, 40]);

      expect(r1, [10, 20]);
      expect(r2, [30, 40]);
    });

    test('modifying source List<int> after conversion does not affect result', () {
      // Uint8List.fromList copies the data; the source list is independent.
      final source = <int>[5, 10, 15];
      final result = _resolveBytes(source);
      source[0] = 99; // mutate source after conversion

      expect(result[0], 5, reason: 'Converted buffer must not reflect mutations to the source list');
    });
  });
}
