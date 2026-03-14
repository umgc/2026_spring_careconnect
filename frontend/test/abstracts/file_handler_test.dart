// Tests for FileUploadResult data class
// (lib/abstracts/file_handler.dart).
//
// Coverage strategy:
//   FileHandler itself is an abstract class with no concrete logic.
//   FileUploadResult is a simple data class whose only testable behaviour is
//   that the constructor stores its two fields.
//
//   Branches tested:
//     constructor — filePath and isTemporary stored correctly for both bool
//                   values (true and false).

import 'package:flutter_test/flutter_test.dart';

import 'package:care_connect_app/abstracts/file_handler.dart';

void main() {
  group('FileUploadResult constructor', () {
    test('stores filePath and isTemporary = true', () {
      // Verifies that both fields are accessible after construction when the
      // file is marked as temporary.
      final result = FileUploadResult(
        filePath: '/tmp/upload_abc.pdf',
        isTemporary: true,
      );
      expect(result.filePath, '/tmp/upload_abc.pdf');
      expect(result.isTemporary, isTrue);
    });

    test('stores filePath and isTemporary = false', () {
      // Verifies the non-temporary (permanent) variant of the constructor.
      final result = FileUploadResult(
        filePath: '/storage/documents/report.pdf',
        isTemporary: false,
      );
      expect(result.filePath, '/storage/documents/report.pdf');
      expect(result.isTemporary, isFalse);
    });

    test('stores an empty filePath string', () {
      // Edge case: empty path should be stored without modification.
      final result = FileUploadResult(filePath: '', isTemporary: false);
      expect(result.filePath, '');
    });
  });
}
