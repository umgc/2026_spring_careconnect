import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:care_connect_app/services/local_db/db_encryption_service.dart';

class FakeFlutterSecureStorage extends FlutterSecureStorage {
  FakeFlutterSecureStorage();

  final Map<String, String> _store = <String, String>{};

  @override
  Future<String?> read({
    required String key,
    AppleOptions? iOptions,
    AndroidOptions? aOptions,
    LinuxOptions? lOptions,
    WebOptions? webOptions,
    WindowsOptions? wOptions,
    MacOsOptions? mOptions,
  }) async {
    return _store[key];
  }

  @override
  Future<void> write({
    required String key,
    required String? value,
    AppleOptions? iOptions,
    AndroidOptions? aOptions,
    LinuxOptions? lOptions,
    WebOptions? webOptions,
    WindowsOptions? wOptions,
    MacOsOptions? mOptions,
  }) async {
    if (value == null) {
      _store.remove(key);
    } else {
      _store[key] = value;
    }
  }

  @override
  Future<void> delete({
    required String key,
    AppleOptions? iOptions,
    AndroidOptions? aOptions,
    LinuxOptions? lOptions,
    WebOptions? webOptions,
    WindowsOptions? wOptions,
    MacOsOptions? mOptions,
  }) async {
    _store.remove(key);
  }
}

void main() {
  group('DbEncryptionService', () {
    late FakeFlutterSecureStorage fakeStorage;
    late DbEncryptionService service;

    setUp(() {
      fakeStorage = FakeFlutterSecureStorage();
      service = DbEncryptionService(storage: fakeStorage);
    });

    test('getOrCreateEncryptionKey returns existing key when already stored',
        () async {
      await fakeStorage.write(
        key: 'careconnect_db_key_v1',
        value: 'existing-test-key',
      );

      final key = await service.getOrCreateEncryptionKey();

      expect(key, 'existing-test-key');
    });

    test('getOrCreateEncryptionKey creates and stores new key when missing',
        () async {
      final key = await service.getOrCreateEncryptionKey();

      expect(key, isNotEmpty);
      expect(key.length, greaterThan(20));

      final stored = await fakeStorage.read(key: 'careconnect_db_key_v1');
      expect(stored, key);
    });

    test('getOrCreateEncryptionKey reuses same generated key on later calls',
        () async {
      final first = await service.getOrCreateEncryptionKey();
      final second = await service.getOrCreateEncryptionKey();

      expect(second, first);
    });

    test('hasEncryptionKey returns false when key does not exist', () async {
      final result = await service.hasEncryptionKey();
      expect(result, isFalse);
    });

    test('hasEncryptionKey returns false when stored key is empty', () async {
      await fakeStorage.write(
        key: 'careconnect_db_key_v1',
        value: '',
      );

      final result = await service.hasEncryptionKey();
      expect(result, isFalse);
    });

    test('hasEncryptionKey returns true when stored key exists', () async {
      await fakeStorage.write(
        key: 'careconnect_db_key_v1',
        value: 'existing-test-key',
      );

      final result = await service.hasEncryptionKey();
      expect(result, isTrue);
    });

    test('escapeForPragma escapes single quotes correctly', () {
      final escaped = service.escapeForPragma("abc'def'ghi");
      expect(escaped, "abc''def''ghi");
    });

    test('escapeForPragma leaves string unchanged when no quotes present', () {
      final escaped = service.escapeForPragma('plain_key_value');
      expect(escaped, 'plain_key_value');
    });

    test('deleteKey removes stored encryption key', () async {
      await fakeStorage.write(
        key: 'careconnect_db_key_v1',
        value: 'existing-test-key',
      );

      expect(await service.hasEncryptionKey(), isTrue);

      await service.deleteKey();

      expect(await service.hasEncryptionKey(), isFalse);
      expect(await fakeStorage.read(key: 'careconnect_db_key_v1'), isNull);
    });
  });
}
