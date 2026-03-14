import 'dart:io';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

/// Test-only platform channel bindings for local DB tests.
///
/// This mocks:
/// - flutter_secure_storage
/// - path_provider
class LocalDbTestBindings {
  static const MethodChannel _secureStorageChannel = MethodChannel(
    'plugins.it_nomads.com/flutter_secure_storage',
  );
  static const MethodChannel _pathProviderChannel = MethodChannel(
    'plugins.flutter.io/path_provider',
  );

  static final Map<String, String> _secureStorageData = <String, String>{};
  static Directory? _documentsDirectory;

  static Future<void> install() async {
    TestWidgetsFlutterBinding.ensureInitialized();
    await _recreateDocumentsDirectory();

    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(_secureStorageChannel, (call) async {
      final args = (call.arguments as Map?)?.cast<String, dynamic>() ??
          <String, dynamic>{};
      final key = args['key'] as String?;

      switch (call.method) {
        case 'read':
          if (key == null) return null;
          return _secureStorageData[key];
        case 'write':
          if (key != null) {
            _secureStorageData[key] = (args['value'] ?? '').toString();
          }
          return null;
        case 'delete':
          if (key != null) {
            _secureStorageData.remove(key);
          }
          return null;
        case 'deleteAll':
          _secureStorageData.clear();
          return null;
        case 'containsKey':
          if (key == null) return false;
          return _secureStorageData.containsKey(key);
        case 'readAll':
          return Map<String, String>.from(_secureStorageData);
        default:
          return null;
      }
    });

    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(_pathProviderChannel, (call) async {
      final path = _documentsDirectory!.path;
      switch (call.method) {
        case 'getApplicationDocumentsDirectory':
        case 'getApplicationSupportDirectory':
        case 'getTemporaryDirectory':
        case 'getDownloadsDirectory':
        case 'getStorageDirectory':
          return path;
        case 'getExternalStorageDirectories':
          return <String>[path];
        default:
          return path;
      }
    });
  }

  static Future<void> reset() async {
    _secureStorageData.clear();
    await _recreateDocumentsDirectory();
  }

  static Future<void> uninstall() async {
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    messenger.setMockMethodCallHandler(_secureStorageChannel, null);
    messenger.setMockMethodCallHandler(_pathProviderChannel, null);

    if (_documentsDirectory != null && _documentsDirectory!.existsSync()) {
      try {
        await _documentsDirectory!.delete(recursive: true);
      } catch (_) {
        // Ignore cleanup failures from open handles during test shutdown.
      }
    }
    _documentsDirectory = null;
  }

  static Future<void> _recreateDocumentsDirectory() async {
    if (_documentsDirectory != null && _documentsDirectory!.existsSync()) {
      try {
        await _documentsDirectory!.delete(recursive: true);
      } catch (_) {
        // Ignore cleanup failures from open handles; create a fresh folder below.
      }
    }
    _documentsDirectory = await Directory.systemTemp.createTemp(
      'careconnect_localdb_test_',
    );
  }
}
