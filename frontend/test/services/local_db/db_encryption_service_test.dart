// Tests for DbEncryptionService.
//
// Coverage strategy:
//   DbEncryptionService reads and writes to FlutterSecureStorage to persist
//   an encryption key across app launches.  The platform channel is stubbed
//   with an in-memory map so no real keychain/keystore access occurs.
//
//   Branches tested:
//     • getOrCreateEncryptionKey — existing key returned as-is.
//     • getOrCreateEncryptionKey — no key → generate, store, return.
//     • getOrCreateEncryptionKey — empty string treated as missing → regenerate.
//     • hasEncryptionKey         — true when key exists.
//     • hasEncryptionKey         — false when absent.
//     • hasEncryptionKey         — false when value is empty string.
//     • escapeForPragma          — no single-quotes → unchanged.
//     • escapeForPragma          — single-quotes are doubled.
//     • deleteKey                — removes the stored key.

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:care_connect_app/services/local_db/db_encryption_service.dart';

// flutter_secure_storage communicates via this MethodChannel.
const MethodChannel _channel =
    MethodChannel('plugins.it_nomads.com/flutter_secure_storage');

// Per-test in-memory storage that the channel mock reads/writes.
final Map<String, String> _storage = {};

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    _storage.clear();

    // Intercept all flutter_secure_storage method calls and redirect them to
    // the in-memory map so no platform plugin or keychain access is required.
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(_channel, (call) async {
      final args = (call.arguments as Map<dynamic, dynamic>?) ?? {};
      final key = args['key'] as String?;
      final value = args['value'] as String?;

      switch (call.method) {
        case 'read':
          return key != null ? _storage[key] : null;
        case 'write':
          if (key != null && value != null) _storage[key] = value;
          return null;
        case 'delete':
          if (key != null) _storage.remove(key);
          return null;
        case 'containsKey':
          return key != null && _storage.containsKey(key);
        case 'readAll':
          return Map<String, String>.from(_storage);
        default:
          return null;
      }
    });
  });

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(_channel, null);
  });

  // ─── getOrCreateEncryptionKey ─────────────────────────────────────────────

  group('DbEncryptionService.getOrCreateEncryptionKey()', () {
    test('returns the existing key without overwriting it', () async {
      // Verifies the read-first path: when a key is already stored, it is
      // returned without generating a new one (which would corrupt the DB).
      _storage['careconnect_db_key_v1'] = 'pre-stored-key';
      final service = DbEncryptionService();
      final key = await service.getOrCreateEncryptionKey();
      expect(key, 'pre-stored-key');
    });

    test('generates a new key when none is stored', () async {
      // Verifies the generation path: an absent key must trigger generation
      // of a cryptographically random key.
      final service = DbEncryptionService();
      final key = await service.getOrCreateEncryptionKey();
      expect(key, isNotEmpty);
    });

    test('generated key is a non-empty base64url string', () async {
      // The key must be base64url-encoded for safe use in PRAGMA statements
      // and reliable round-trip through storage.
      final service = DbEncryptionService();
      final key = await service.getOrCreateEncryptionKey();
      // Base64url uses A-Z, a-z, 0-9, -, _  and may have = padding.
      expect(RegExp(r'^[A-Za-z0-9\-_=]+$').hasMatch(key), isTrue);
    });

    test('generated key is stored so subsequent calls return the same value',
        () async {
      // Verifies persistence: the first call generates and stores the key;
      // a second call must return the same value (same DbEncryptionService
      // instance re-reads from storage).
      final service = DbEncryptionService();
      final first = await service.getOrCreateEncryptionKey();
      final second = await service.getOrCreateEncryptionKey();
      expect(first, second);
    });

    test('generated key has sufficient length for cryptographic strength',
        () async {
      // 32 random bytes encoded as base64url produce a 43-character string
      // (without padding) or 44 (with).  Require at least 43 characters.
      final service = DbEncryptionService();
      final key = await service.getOrCreateEncryptionKey();
      expect(key.length, greaterThanOrEqualTo(43));
    });

    test('treats an empty stored value as missing and generates a new key',
        () async {
      // An empty string must not be returned as a valid key; the service
      // must generate a fresh one to avoid opening the database with an
      // empty PRAGMA key.
      _storage['careconnect_db_key_v1'] = '';
      final service = DbEncryptionService();
      final key = await service.getOrCreateEncryptionKey();
      expect(key, isNotEmpty);
    });
  });

  // ─── hasEncryptionKey ─────────────────────────────────────────────────────

  group('DbEncryptionService.hasEncryptionKey()', () {
    test('returns true when a non-empty key exists in storage', () async {
      // Verifies the positive branch used to detect that the database has
      // already been encrypted on a previous launch.
      _storage['careconnect_db_key_v1'] = 'some-key';
      final service = DbEncryptionService();
      expect(await service.hasEncryptionKey(), isTrue);
    });

    test('returns false when no key has been stored', () async {
      // Verifies the negative branch: a fresh install has no key.
      final service = DbEncryptionService();
      expect(await service.hasEncryptionKey(), isFalse);
    });

    test('returns false when the stored value is an empty string', () async {
      // An empty string is treated as absent so the caller can safely
      // distinguish "key exists" from "key is usable".
      _storage['careconnect_db_key_v1'] = '';
      final service = DbEncryptionService();
      expect(await service.hasEncryptionKey(), isFalse);
    });
  });

  // ─── escapeForPragma ──────────────────────────────────────────────────────

  group('DbEncryptionService.escapeForPragma()', () {
    test('returns the key unchanged when it contains no single quotes', () {
      // A base64url key never contains single quotes; the common path should
      // be a no-op identity transformation.
      final service = DbEncryptionService();
      const input = 'abc123DEF-_=';
      expect(service.escapeForPragma(input), input);
    });

    test('doubles every single quote in the key', () {
      // SQLite PRAGMA key values are embedded in a single-quoted string;
      // a literal single-quote must be escaped by doubling it.
      final service = DbEncryptionService();
      expect(service.escapeForPragma("it's a key"), "it''s a key");
    });

    test('doubles multiple single quotes independently', () {
      // Verifies correct handling when multiple quotes appear in the input.
      final service = DbEncryptionService();
      expect(service.escapeForPragma("a'b'c"), "a''b''c");
    });

    test('returns empty string unchanged', () {
      final service = DbEncryptionService();
      expect(service.escapeForPragma(''), '');
    });

    test('does not modify other special characters', () {
      // Double quotes, backticks, and semicolons are not touched.
      final service = DbEncryptionService();
      const input = r'key"`;semicolon;';
      expect(service.escapeForPragma(input), input);
    });
  });

  // ─── deleteKey ────────────────────────────────────────────────────────────

  group('DbEncryptionService.deleteKey()', () {
    test('removes the stored key from secure storage', () async {
      // Verifies that deleteKey() clears the persisted entry so a subsequent
      // hasEncryptionKey() returns false (factory-reset flow).
      _storage['careconnect_db_key_v1'] = 'key-to-delete';
      final service = DbEncryptionService();
      await service.deleteKey();
      expect(await service.hasEncryptionKey(), isFalse);
    });

    test('is safe to call when no key exists (no-throw)', () async {
      // Deleting a missing key must not throw; the post-condition is the
      // same: no key is stored.
      final service = DbEncryptionService();
      await expectLater(service.deleteKey(), completes);
    });

    test('after deleteKey, getOrCreateEncryptionKey generates a new key',
        () async {
      // Verifies the full reset → recreate lifecycle used in factory-reset flows.
      _storage['careconnect_db_key_v1'] = 'original-key';
      final service = DbEncryptionService();
      await service.deleteKey();
      final newKey = await service.getOrCreateEncryptionKey();
      expect(newKey, isNotEmpty);
      expect(newKey, isNot('original-key'));
    });
  });
}
