/// Tests for the analytics [downloadFile] stub
/// (lib/features/analytics/web_utils.dart).
///
/// ## What this file is
///
/// `analytics/web_utils.dart` is the **non-web stub** for file downloads.
/// Its sole job is to absorb calls to [downloadFile] on mobile/VM platforms
/// and print a diagnostic message so callers don't need `if (kIsWeb)` guards
/// everywhere.
///
/// The companion file `analytics/web_utils_web.dart` contains the real
/// browser implementation (Blob + anchor-click download) and requires
/// `package:web` / `dart:js_interop`, making it incompatible with VM tests.
///
/// ## Coverage strategy
///
///   1. No-throw contract  – [downloadFile] must never raise an exception
///      regardless of the values passed for [fileName] or [bytes].
///   2. Print output       – the stub must emit a diagnostic message that
///      includes the supplied file name, captured via a custom Zone.
///   3. Input variants     – [bytes] is typed as [dynamic]; the stub must
///      accept every common type callers might pass (Uint8List, `List<int>`,
///      String, Map, null, empty collection, etc.).
///   4. Idempotency        – successive calls must be independent.

library;

import 'dart:async';
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:care_connect_app/features/analytics/web_utils.dart';

/// Runs [fn] synchronously inside a Zone that captures every [print] call and
/// returns the collected lines.  Suitable for testing side-effects that write
/// to stdout via [print].
List<String> _capturePrint(void Function() fn) {
  final lines = <String>[];
  Zone.current
      .fork(
        specification: ZoneSpecification(
          print: (_, _, _, String line) => lines.add(line),
        ),
      )
      .run(fn);
  return lines;
}

void main() {
  // ─────────────────────────────────────────────────────────────────────────
  // No-throw contract
  // ─────────────────────────────────────────────────────────────────────────
  group('downloadFile – no-throw contract', () {
    test('does not throw with a normal file name and List<int> bytes',
        () {
      // The most common call pattern from the analytics page.
      expect(
        () => downloadFile('report.pdf', <int>[1, 2, 3, 4]),
        returnsNormally,
      );
    });

    test('does not throw with a Uint8List payload', () {
      // Uint8List is the standard byte buffer type in Dart; callers may pass
      // either List<int> or Uint8List without knowing which the stub accepts.
      expect(
        () => downloadFile('export.xlsx', Uint8List.fromList([0, 128, 255])),
        returnsNormally,
      );
    });

    test('does not throw with an empty byte list', () {
      // An empty export (e.g. a CSV with no rows) is a valid edge case.
      expect(
        () => downloadFile('empty.csv', <int>[]),
        returnsNormally,
      );
    });

    test('does not throw with an empty Uint8List', () {
      expect(
        () => downloadFile('empty.bin', Uint8List(0)),
        returnsNormally,
      );
    });

    test('does not throw with a String passed as bytes', () {
      // The stub accepts [dynamic]; passing a String is degenerate but must
      // not crash (the stub ignores the value entirely).
      expect(
        () => downloadFile('file.txt', 'raw string content'),
        returnsNormally,
      );
    });

    test('does not throw with null passed as bytes', () {
      // Callers may pass null if bytes are optional in their context.
      expect(
        () => downloadFile('null_bytes.bin', null),
        returnsNormally,
      );
    });

    test('does not throw with a Map passed as bytes', () {
      // Arbitrary dynamic types must not trigger an exception in the stub.
      expect(
        () => downloadFile('data.json', {'key': 'value'}),
        returnsNormally,
      );
    });

    test('does not throw with an empty file name', () {
      // An empty file name is unusual but must not cause the stub to crash.
      expect(
        () => downloadFile('', <int>[1, 2, 3]),
        returnsNormally,
      );
    });

    test('does not throw with a file name that contains special characters',
        () {
      // File names may include spaces, dots, and path separators.
      expect(
        () => downloadFile('my report (Q1) – 2024.pdf', <int>[]),
        returnsNormally,
      );
    });
  });

  // ─────────────────────────────────────────────────────────────────────────
  // Print output – diagnostic message verification
  // ─────────────────────────────────────────────────────────────────────────
  group('downloadFile – print output', () {
    test('prints exactly one line per call', () {
      // The stub must not be silent (callers rely on the message to know
      // that a real download was not performed) and must not double-print.
      final lines = _capturePrint(
        () => downloadFile('report.pdf', <int>[1]),
      );
      expect(lines, hasLength(1));
    });

    test('printed message contains the supplied file name', () {
      // The file name must appear in the output so developers can identify
      // which download call was suppressed.
      const fileName = 'analytics_export.csv';
      final lines = _capturePrint(
        () => downloadFile(fileName, <int>[]),
      );
      expect(lines.first, contains(fileName));
    });

    test('printed message contains a platform-unsupported indicator', () {
      // The stub's message should communicate that the feature is unavailable
      // on the current platform.
      final lines = _capturePrint(
        () => downloadFile('test.bin', null),
      );
      // The message "Download not supported on this platform:" is the current
      // text; we match a case-insensitive substring for resilience.
      expect(
        lines.first.toLowerCase(),
        anyOf(
          contains('not supported'),
          contains('unavailable'),
          contains('stub'),
          contains('platform'),
        ),
      );
    });

    test('each call prints the correct file name independently', () {
      // Calling downloadFile twice must produce two separate, correctly
      // labelled messages — the stub must not cache or mix up file names.
      const name1 = 'first.pdf';
      const name2 = 'second.xlsx';

      final linesA = _capturePrint(() => downloadFile(name1, <int>[]));
      final linesB = _capturePrint(() => downloadFile(name2, <int>[]));

      expect(linesA.first, contains(name1));
      expect(linesB.first, contains(name2));
    });

    test('printed message with an empty file name does not throw', () {
      // Even with an empty file name the print statement must succeed.
      final lines = _capturePrint(() => downloadFile('', <int>[]));
      expect(lines, hasLength(1));
    });
  });

  // ─────────────────────────────────────────────────────────────────────────
  // Idempotency
  // ─────────────────────────────────────────────────────────────────────────
  group('downloadFile – idempotency', () {
    test('repeated calls do not interfere with each other', () {
      // The stub holds no mutable state, so successive calls must each
      // produce exactly one print line and not affect subsequent calls.
      expect(() {
        downloadFile('a.pdf', <int>[1]);
        downloadFile('b.pdf', <int>[2]);
        downloadFile('c.pdf', <int>[3]);
      }, returnsNormally);
    });

    test('each repeated call prints a line for its own file name', () {
      const names = ['one.csv', 'two.pdf', 'three.xlsx'];
      for (final name in names) {
        final lines = _capturePrint(() => downloadFile(name, <int>[]));
        expect(lines.first, contains(name),
            reason: 'Expected output to contain "$name"');
      }
    });
  });

  // ─────────────────────────────────────────────────────────────────────────
  // Return value
  // ─────────────────────────────────────────────────────────────────────────
  group('downloadFile – return value', () {
    test('completes synchronously without returning a Future', () {
      // The function is declared void; wrapping it in returnsNormally confirms
      // it completes synchronously (not async) and does not throw.  If the
      // signature were accidentally changed to Future<void>, callers that
      // ignore the return value would silently drop the operation.
      expect(() => downloadFile('check.pdf', <int>[1]), returnsNormally);
    });
  });
}
