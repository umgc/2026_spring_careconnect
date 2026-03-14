import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:care_connect_app/features/streaming_asr_with_diarization/utils.dart';
import 'package:flutter_test/flutter_test.dart';
// Both packages below are transitive dependencies of path_provider (already in
// pubspec.yaml). The ignore comments suppress the "not a direct dependency"
// lint hint without requiring pubspec.yaml changes.
// ignore: depend_on_referenced_packages
import 'package:path_provider_platform_interface/path_provider_platform_interface.dart';
// ignore: depend_on_referenced_packages
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

// ---------------------------------------------------------------------------
// Fake PathProviderPlatform so tests never touch real device paths.
// MockPlatformInterfaceMixin bypasses PlatformInterface's token verification.
// ---------------------------------------------------------------------------
class _FakePathProvider extends Fake
    with MockPlatformInterfaceMixin
    implements PathProviderPlatform {
  final String _path;
  _FakePathProvider(this._path);

  @override
  Future<String?> getApplicationSupportPath() async => _path;
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  // =========================================================================
  // convertBytesToFloat32
  // Pure function — no platform dependencies, no mocking required.
  // Converts raw PCM int16 bytes to a normalised Float32List in [-1.0, 1.0].
  // =========================================================================
  group('convertBytesToFloat32', () {
    // --- output sizing -------------------------------------------------------

    // Empty input must produce an empty list without throwing.
    test('returns empty Float32List for empty input', () {
      final result = convertBytesToFloat32(Uint8List(0));
      expect(result, isEmpty);
    });

    // Each pair of bytes becomes one float32, so length == bytes.length ~/ 2.
    test('output length equals input byte count divided by 2', () {
      final result = convertBytesToFloat32(Uint8List(8)); // 4 pairs
      expect(result.length, equals(4));
    });

    // --- zero / silence ------------------------------------------------------

    // All-zero bytes represent digital silence; every sample should be 0.0.
    test('all-zero bytes produce 0.0 for every sample', () {
      final result = convertBytesToFloat32(Uint8List(4));
      expect(result[0], closeTo(0.0, 1e-6));
      expect(result[1], closeTo(0.0, 1e-6));
    });

    // --- little-endian boundary values (default) -----------------------------

    // 0x7FFF (little-endian: [0xFF, 0x7F]) = 32767 → 32767 / 32768 ≈ 0.99997.
    test('max positive int16 little-endian maps to ≈ 1.0', () {
      final bytes = Uint8List.fromList([0xFF, 0x7F]);
      final result = convertBytesToFloat32(bytes);
      expect(result[0], closeTo(32767 / 32768.0, 1e-5));
    });

    // 0x8000 (little-endian: [0x00, 0x80]) = −32768 → −32768 / 32768 = −1.0.
    test('min negative int16 little-endian maps to −1.0', () {
      final bytes = Uint8List.fromList([0x00, 0x80]);
      final result = convertBytesToFloat32(bytes);
      expect(result[0], closeTo(-1.0, 1e-6));
    });

    // 0x4000 (little-endian: [0x00, 0x40]) = 16384 → 16384 / 32768 = 0.5.
    test('midpoint positive little-endian maps to 0.5', () {
      final bytes = Uint8List.fromList([0x00, 0x40]);
      final result = convertBytesToFloat32(bytes);
      expect(result[0], closeTo(0.5, 1e-6));
    });

    // 0xC000 (little-endian: [0x00, 0xC0]) = −16384 → −16384 / 32768 = −0.5.
    test('midpoint negative little-endian maps to −0.5', () {
      final bytes = Uint8List.fromList([0x00, 0xC0]);
      final result = convertBytesToFloat32(bytes);
      expect(result[0], closeTo(-0.5, 1e-6));
    });

    // --- endianness ----------------------------------------------------------

    // The default endian is Endian.little; [0x01, 0x00] = 1 → 1/32768.
    test('default is little-endian: [0x01, 0x00] → 1/32768', () {
      final bytes = Uint8List.fromList([0x01, 0x00]);
      final result = convertBytesToFloat32(bytes); // no Endian arg
      expect(result[0], closeTo(1 / 32768.0, 1e-7));
    });

    // With big-endian, [0x01, 0x00] is interpreted as 0x0100 = 256.
    // 256 / 32768 = 0.0078125, which differs from the little-endian result.
    test('big-endian gives different result for asymmetric bytes', () {
      final bytes = Uint8List.fromList([0x01, 0x00]);
      final resultBE = convertBytesToFloat32(bytes, Endian.big);
      expect(resultBE[0], closeTo(256 / 32768.0, 1e-7));
    });

    // [0x7F, 0xFF] big-endian = 0x7FFF = 32767 → same near-1.0 as little-endian
    // test above, but with bytes reversed.
    test('big-endian max positive [0x7F, 0xFF] maps to ≈ 1.0', () {
      final bytes = Uint8List.fromList([0x7F, 0xFF]);
      final result = convertBytesToFloat32(bytes, Endian.big);
      expect(result[0], closeTo(32767 / 32768.0, 1e-5));
    });

    // --- multi-sample correctness --------------------------------------------

    // Verifies that each pair of bytes is converted independently and in order.
    // Sample 0: [0x00, 0x80] = −32768 → −1.0
    // Sample 1: [0xFF, 0x7F] =  32767 → ≈ 1.0
    test('multiple samples are each converted independently in order', () {
      final bytes = Uint8List.fromList([0x00, 0x80, 0xFF, 0x7F]);
      final result = convertBytesToFloat32(bytes);
      expect(result.length, equals(2));
      expect(result[0], closeTo(-1.0, 1e-6));
      expect(result[1], closeTo(32767 / 32768.0, 1e-5));
    });

    // Single-sample input: [0x00, 0x40] = 16384 → 0.5 for a 1-element list.
    test('single sample input produces a one-element list', () {
      final bytes = Uint8List.fromList([0x00, 0x40]);
      final result = convertBytesToFloat32(bytes);
      expect(result.length, equals(1));
      expect(result[0], closeTo(0.5, 1e-6));
    });
  });

  // =========================================================================
  // copyAssetFile
  // Async function that copies a Flutter asset to the app support directory.
  // Platform dependencies are replaced with fakes so the test runs on any OS.
  // =========================================================================
  group('copyAssetFile', () {
    // A fixed asset key and its bytes used across tests.
    const assetKey = 'assets/test_asset.bin';
    final assetBytes = Uint8List.fromList([0xDE, 0xAD, 0xBE, 0xEF]);

    late Directory tempDir;

    setUp(() async {
      // Create a fresh temp directory for each test so tests are isolated.
      tempDir = await Directory.systemTemp.createTemp('cc_utils_test_');

      // Redirect path_provider to the temp dir instead of the device path.
      PathProviderPlatform.instance = _FakePathProvider(tempDir.path);

      // Mock rootBundle via the binary messenger.
      // rootBundle.load(key) sends the UTF-8 encoded key on the
      // 'flutter/assets' channel and expects raw asset bytes in response.
      TestDefaultBinaryMessengerBinding
          .instance.defaultBinaryMessenger
          .setMockMessageHandler('flutter/assets', (ByteData? message) async {
        if (message == null) return null;
        final key = utf8.decode(message.buffer.asUint8List());
        if (key == assetKey) {
          return ByteData.view(assetBytes.buffer);
        }
        return null; // unknown asset → bundle will throw FlutterError
      });
    });

    tearDown(() async {
      // Remove the mock handler so it does not affect other test groups.
      TestDefaultBinaryMessengerBinding
          .instance.defaultBinaryMessenger
          .setMockMessageHandler('flutter/assets', null);
      await tempDir.delete(recursive: true);
    });

    // --- return value / path -------------------------------------------------

    // The returned path must live inside the application support directory.
    test('returned path is inside the application support directory', () async {
      final path = await copyAssetFile(assetKey);
      expect(path, startsWith(tempDir.path));
    });

    // When dst is omitted the filename should be the basename of the src key.
    test('default dst is the basename of src', () async {
      final path = await copyAssetFile(assetKey);
      expect(path.endsWith('test_asset.bin'), isTrue);
    });

    // An explicit dst argument must override the default basename.
    test('explicit dst overrides the basename', () async {
      final path = await copyAssetFile(assetKey, 'renamed.bin');
      expect(path.endsWith('renamed.bin'), isTrue);
      expect(path, isNot(contains('test_asset.bin')));
    });

    // --- file creation -------------------------------------------------------

    // The file must exist on disk after the call.
    test('creates the file if it does not yet exist', () async {
      final path = await copyAssetFile(assetKey);
      expect(await File(path).exists(), isTrue);
    });

    // The content written to disk must match the mocked asset bytes exactly.
    test('written file content matches the asset bytes', () async {
      final path = await copyAssetFile(assetKey);
      final written = await File(path).readAsBytes();
      expect(written, orderedEquals(assetBytes));
    });

    // --- overwrite logic -----------------------------------------------------

    // When the file already exists and its size matches the asset, the function
    // must NOT overwrite it (because only size is checked, not content).
    // We verify this by first writing different-but-same-length bytes and
    // confirming they survive the second copyAssetFile call unchanged.
    test('skips overwrite when file already exists with matching size', () async {
      final path = await copyAssetFile(assetKey);

      // Corrupt one byte in-place; the file length (4) still matches the asset.
      final corruptedBytes = Uint8List.fromList([0x00, 0xAD, 0xBE, 0xEF]);
      await File(path).writeAsBytes(corruptedBytes);

      // A second call must leave the corrupted file untouched.
      await copyAssetFile(assetKey);
      final afterSecondCall = await File(path).readAsBytes();
      expect(afterSecondCall, orderedEquals(corruptedBytes));
    });

    // When the on-disk size differs from the asset size, the function must
    // overwrite the file with the correct asset content.
    test('overwrites file when on-disk size differs from asset size', () async {
      final path = await copyAssetFile(assetKey);

      // Truncate the file so its length no longer matches the asset.
      await File(path).writeAsBytes([0x00]);
      expect(File(path).lengthSync(), equals(1));

      // The next call must restore the full asset content.
      await copyAssetFile(assetKey);
      final restored = await File(path).readAsBytes();
      expect(restored, orderedEquals(assetBytes));
    });
  });
}
