import 'package:care_connect_app/services/local_db/db_encryption_service.dart';
import 'package:flutter_test/flutter_test.dart';

import 'test_support/local_db_test_bindings.dart';

void main() {
  group('db_encryption_service.dart', () {
    setUpAll(LocalDbTestBindings.install);
    tearDownAll(LocalDbTestBindings.uninstall);
    setUp(LocalDbTestBindings.reset);

    test('creates and persists encryption key', () async {
      final service = DbEncryptionService();

      final first = await service.getOrCreateEncryptionKey();
      final second = await service.getOrCreateEncryptionKey();

      expect(first, isNotEmpty);
      expect(first, equals(second));
    });

    test('reports key presence before and after key generation', () async {
      final service = DbEncryptionService();

      expect(await service.hasEncryptionKey(), isFalse);
      await service.getOrCreateEncryptionKey();
      expect(await service.hasEncryptionKey(), isTrue);
    });

    test('deleteKey removes the stored key', () async {
      final service = DbEncryptionService();
      await service.getOrCreateEncryptionKey();

      await service.deleteKey();

      expect(await service.hasEncryptionKey(), isFalse);
    });

    test('escapeForPragma escapes single quotes for SQL PRAGMA', () {
      final service = DbEncryptionService();
      expect(service.escapeForPragma("a'b''c"), equals("a''b''''c"));
    });
  });
}
