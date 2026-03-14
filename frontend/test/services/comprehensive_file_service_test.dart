// Tests for ComprehensiveFileService pure-Dart helpers and HTTP retrieval methods.
//
// Coverage strategy:
//   Upload methods (_uploadToEndpoint, uploadProfileImage, etc.) require
//   http.MultipartRequest which cannot be intercepted via http.runWithClient,
//   and file-picker/image-picker platform channels — those are excluded.
//   pickFileForCategory / pickFileForCategoryWeb use image_picker/FilePicker — excluded.
//   validateFileForCategory calls file.lengthSync() — excluded.
//
//   HTTP GET methods use top-level http.get → interceptable via http.runWithClient.
//   AuthTokenManager.getAuthHeaders() intercepted via FlutterSecureStorage stub.
//
//   Branches tested:
//     FileCategory enum — value, displayName, icon for every member.
//     FileQueryParams.toQueryString — all fields present, partial fields, empty.
//     getAllUserFiles — 200 files key → list, 200 content key → list, non-200 → [].
//     getAllUserFiles1 — 200 → UserFileDTO, non-200 → null.
//     getPatientMedicalDocuments — 200 data key → list, 200 content key → list, non-200 → [].
//     searchFiles — 200 data key → list, non-200 → [].
//     getUserFilesByCategory — delegates to getAllUserFiles (category param set).
//     getFilesByDateRange — delegates to getAllUserFiles (date params set).
//     getFilesByMultipleCategories — delegates to getAllUserFiles (categories param set).

import 'dart:convert';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:care_connect_app/services/comprehensive_file_service.dart';
import 'package:care_connect_app/services/enhanced_file_service.dart';

// ─── Secure storage stub ──────────────────────────────────────────────────────

const MethodChannel _secureStorageChannel =
    MethodChannel('plugins.it_nomads.com/flutter_secure_storage');

final Map<String, String?> _secureStore = {};

void _setupSecureStorageStub() {
  TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
      .setMockMethodCallHandler(_secureStorageChannel, (call) async {
    switch (call.method) {
      case 'write':
        _secureStore[call.arguments['key'] as String] =
            call.arguments['value'] as String?;
        return null;
      case 'read':
        return _secureStore[call.arguments['key'] as String];
      case 'delete':
        _secureStore.remove(call.arguments['key'] as String);
        return null;
      case 'deleteAll':
        _secureStore.clear();
        return null;
      default:
        return null;
    }
  });
}

void _seedAuthToken() {
  _secureStore['jwt_token'] = 'test-jwt-token';
  _secureStore['token_expiry'] = '2000000000'; // year 2033
}

/// Minimal UserFileDTO JSON that passes fromJson without errors.
Map<String, dynamic> _fileJson({int id = 1}) => {
  'id': id,
  'originalFilename': 'test_$id.pdf',
  'contentType': 'application/pdf',
  'fileSize': 1024,
  'fileCategory': 'MEDICAL_REPORT',
  'ownerId': 42,
  'ownerType': 'PATIENT',
  'fileName': 'test_$id.pdf',
};

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    _secureStore.clear();
    SharedPreferences.setMockInitialValues({});
    _setupSecureStorageStub();
    _seedAuthToken();
  });

  tearDownAll(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(_secureStorageChannel, null);
  });

  // ─── FileCategory enum ───────────────────────────────────────────────────

  group('FileCategory', () {
    test('all members have non-empty value, displayName, and icon', () {
      for (final cat in FileCategory.values) {
        expect(cat.value, isNotEmpty, reason: '${cat.name}.value empty');
        expect(cat.displayName, isNotEmpty, reason: '${cat.name}.displayName empty');
        expect(cat.icon, isNotEmpty, reason: '${cat.name}.icon empty');
      }
    });

    test('profilePicture has value PROFILE_PICTURE', () {
      expect(FileCategory.profilePicture.value, 'PROFILE_PICTURE');
    });

    test('medicalReport has display name Medical Report', () {
      expect(FileCategory.medicalReport.displayName, 'Medical Report');
    });

    test('labResult has icon 🧪', () {
      expect(FileCategory.labResult.icon, '🧪');
    });
  });

  // ─── FileQueryParams.toQueryString ──────────────────────────────────────

  group('FileQueryParams.toQueryString', () {
    test('empty params returns empty string', () {
      expect(FileQueryParams().toQueryString(), '');
    });

    test('single param page=1', () {
      final qs = FileQueryParams(page: 1).toQueryString();
      expect(qs, '?page=1');
    });

    test('multiple params joined with &', () {
      final qs = FileQueryParams(page: 0, size: 10, sort: 'createdAt').toQueryString();
      expect(qs, contains('page=0'));
      expect(qs, contains('size=10'));
      expect(qs, contains('sort=createdAt'));
    });

    test('category param included', () {
      final qs = FileQueryParams(category: 'MEDICAL_RECORD').toQueryString();
      expect(qs, contains('category=MEDICAL_RECORD'));
    });

    test('categories list joined with comma', () {
      final qs = FileQueryParams(
        categories: ['MEDICAL_RECORD', 'PRESCRIPTION'],
      ).toQueryString();
      expect(qs, contains('categories=MEDICAL_RECORD,PRESCRIPTION'));
    });

    test('empty categories list not included', () {
      final qs = FileQueryParams(categories: []).toQueryString();
      expect(qs, isNot(contains('categories')));
    });

    test('date range params included', () {
      final qs = FileQueryParams(
        startDate: '2025-01-01',
        endDate: '2025-12-31',
      ).toQueryString();
      expect(qs, contains('startDate=2025-01-01'));
      expect(qs, contains('endDate=2025-12-31'));
    });

    test('query param is URI-encoded', () {
      final qs = FileQueryParams(query: 'hello world').toQueryString();
      expect(qs, contains('query=hello%20world'));
    });

    test('starts with ? when params present', () {
      final qs = FileQueryParams(page: 0).toQueryString();
      expect(qs, startsWith('?'));
    });
  });

  // ─── getAllUserFiles ──────────────────────────────────────────────────────

  group('ComprehensiveFileService.getAllUserFiles', () {
    test('200 with files key → returns list of UserFileDTO', () async {
      final body = jsonEncode({
        'files': [_fileJson(id: 1), _fileJson(id: 2)],
      });
      final result = await http.runWithClient(
        () => ComprehensiveFileService.getAllUserFiles(10),
        () => MockClient((_) async => http.Response(body, 200)),
      );
      expect(result.length, 2);
      expect(result[0], isA<UserFileDTO>());
    });

    test('200 with content key → returns list', () async {
      final body = jsonEncode({
        'content': [_fileJson(id: 3)],
      });
      final result = await http.runWithClient(
        () => ComprehensiveFileService.getAllUserFiles(10),
        () => MockClient((_) async => http.Response(body, 200)),
      );
      expect(result.length, 1);
    });

    test('200 with empty files → returns empty list', () async {
      final body = jsonEncode({'files': []});
      final result = await http.runWithClient(
        () => ComprehensiveFileService.getAllUserFiles(10),
        () => MockClient((_) async => http.Response(body, 200)),
      );
      expect(result, isEmpty);
    });

    test('non-200 → returns empty list', () async {
      final body = jsonEncode({'error': 'Unauthorized'});
      final result = await http.runWithClient(
        () => ComprehensiveFileService.getAllUserFiles(10),
        () => MockClient((_) async => http.Response(body, 401)),
      );
      expect(result, isEmpty);
    });
  });

  // ─── getAllUserFiles1 ─────────────────────────────────────────────────────

  group('ComprehensiveFileService.getAllUserFiles1', () {
    test('200 → returns a UserFileDTO', () async {
      final body = jsonEncode(_fileJson(id: 5));
      final result = await http.runWithClient(
        () => ComprehensiveFileService.getAllUserFiles1(10),
        () => MockClient((_) async => http.Response(body, 200)),
      );
      expect(result, isNotNull);
      expect(result, isA<UserFileDTO>());
    });

    test('non-200 → returns null', () async {
      final body = jsonEncode({'error': 'Not found'});
      final result = await http.runWithClient(
        () => ComprehensiveFileService.getAllUserFiles1(10),
        () => MockClient((_) async => http.Response(body, 404)),
      );
      expect(result, isNull);
    });
  });

  // ─── getPatientMedicalDocuments ──────────────────────────────────────────

  group('ComprehensiveFileService.getPatientMedicalDocuments', () {
    test('200 with data key → returns list', () async {
      final body = jsonEncode({
        'data': [_fileJson(id: 10)],
      });
      final result = await http.runWithClient(
        () => ComprehensiveFileService.getPatientMedicalDocuments(5),
        () => MockClient((_) async => http.Response(body, 200)),
      );
      expect(result.length, 1);
    });

    test('200 with content key → returns list', () async {
      final body = jsonEncode({
        'content': [_fileJson(id: 11), _fileJson(id: 12)],
      });
      final result = await http.runWithClient(
        () => ComprehensiveFileService.getPatientMedicalDocuments(5),
        () => MockClient((_) async => http.Response(body, 200)),
      );
      expect(result.length, 2);
    });

    test('non-200 → returns empty list', () async {
      final body = jsonEncode({'error': 'Server error'});
      final result = await http.runWithClient(
        () => ComprehensiveFileService.getPatientMedicalDocuments(5),
        () => MockClient((_) async => http.Response(body, 500)),
      );
      expect(result, isEmpty);
    });
  });

  // ─── searchFiles ─────────────────────────────────────────────────────────

  group('ComprehensiveFileService.searchFiles', () {
    test('200 with data key → returns list', () async {
      final body = jsonEncode({
        'data': [_fileJson(id: 20)],
      });
      final result = await http.runWithClient(
        () => ComprehensiveFileService.searchFiles(
          searchQuery: 'blood',
          userId: 10,
        ),
        () => MockClient((_) async => http.Response(body, 200)),
      );
      expect(result.length, 1);
    });

    test('200 with content key → returns list', () async {
      final body = jsonEncode({
        'content': [_fileJson(id: 21), _fileJson(id: 22)],
      });
      final result = await http.runWithClient(
        () => ComprehensiveFileService.searchFiles(
          searchQuery: 'lab',
          userId: 10,
        ),
        () => MockClient((_) async => http.Response(body, 200)),
      );
      expect(result.length, 2);
    });

    test('non-200 → returns empty list', () async {
      final body = jsonEncode({'error': 'Bad request'});
      final result = await http.runWithClient(
        () => ComprehensiveFileService.searchFiles(
          searchQuery: 'test',
          userId: 10,
        ),
        () => MockClient((_) async => http.Response(body, 400)),
      );
      expect(result, isEmpty);
    });
  });

  // ─── getUserFilesByCategory ──────────────────────────────────────────────

  group('ComprehensiveFileService.getUserFilesByCategory', () {
    test('delegates to getAllUserFiles and returns results', () async {
      final body = jsonEncode({
        'files': [_fileJson(id: 30)],
      });
      final result = await http.runWithClient(
        () => ComprehensiveFileService.getUserFilesByCategory(
          10,
          FileCategory.prescription,
        ),
        () => MockClient((_) async => http.Response(body, 200)),
      );
      expect(result.length, 1);
      expect(result[0], isA<UserFileDTO>());
    });

    test('non-200 → returns empty list', () async {
      final result = await http.runWithClient(
        () => ComprehensiveFileService.getUserFilesByCategory(
          10,
          FileCategory.labResult,
        ),
        () => MockClient((_) async => http.Response('{"error":"err"}', 403)),
      );
      expect(result, isEmpty);
    });
  });

  // ─── getFilesByDateRange ─────────────────────────────────────────────────

  group('ComprehensiveFileService.getFilesByDateRange', () {
    test('delegates to getAllUserFiles and returns results', () async {
      final body = jsonEncode({
        'files': [_fileJson(id: 40), _fileJson(id: 41)],
      });
      final result = await http.runWithClient(
        () => ComprehensiveFileService.getFilesByDateRange(
          10,
          startDate: DateTime(2025, 1, 1),
          endDate: DateTime(2025, 12, 31),
        ),
        () => MockClient((_) async => http.Response(body, 200)),
      );
      expect(result.length, 2);
    });

    test('non-200 → returns empty list', () async {
      final result = await http.runWithClient(
        () => ComprehensiveFileService.getFilesByDateRange(
          10,
          startDate: DateTime(2025, 1, 1),
          endDate: DateTime(2025, 6, 30),
        ),
        () => MockClient((_) async => http.Response('{"error":"err"}', 500)),
      );
      expect(result, isEmpty);
    });
  });

  // ─── getFilesByMultipleCategories ────────────────────────────────────────

  group('ComprehensiveFileService.getFilesByMultipleCategories', () {
    test('delegates to getAllUserFiles and returns results', () async {
      final body = jsonEncode({
        'content': [_fileJson(id: 50)],
      });
      final result = await http.runWithClient(
        () => ComprehensiveFileService.getFilesByMultipleCategories(
          10,
          categories: [FileCategory.prescription, FileCategory.labResult],
        ),
        () => MockClient((_) async => http.Response(body, 200)),
      );
      expect(result.length, 1);
    });

    test('non-200 → returns empty list', () async {
      final result = await http.runWithClient(
        () => ComprehensiveFileService.getFilesByMultipleCategories(
          10,
          categories: [FileCategory.medicalReport],
        ),
        () => MockClient((_) async => http.Response('{"error":"err"}', 400)),
      );
      expect(result, isEmpty);
    });
  });
}
