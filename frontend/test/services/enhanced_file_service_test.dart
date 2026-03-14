// Tests for EnhancedFileService, UserFileDTO, FileUploadResponse, and the
// getCategoryDisplayName top-level helper.
//
// Coverage strategy:
//   Upload methods use http.MultipartRequest which cannot be intercepted
//   via http.runWithClient — those are excluded.
//
//   HTTP GET/DELETE methods are tested via http.runWithClient + MockClient.
//   AuthTokenManager.getAuthHeaders() intercepted via FlutterSecureStorage stub.
//
//   Branches tested:
//     UserFileDTO.fromJson — all fields, null fallbacks, icon/isImage/isPreviewable.
//     UserFileDTO.toJson   — round-trip serialization.
//     FileUploadResponse.fromJson — all fields.
//     EnhancedFileService.getValidCategories — patient / caregiver / family / default.
//     EnhancedFileService.getCategoryDisplayNames — key count and sample values.
//     getCategoryDisplayName (top-level helper) — known and unknown keys.
//     downloadFile — 200 → bytes, non-200 → null.
//     downloadFileLegacy — 200 → bytes, non-200 → null.
//     listMyFiles — 200 → list, non-200 → [].
//     listPatientFiles — 200 → list, non-200 → [].
//     deleteFile — 200 → true, non-200 → false.
//     getProfileImage — 200 → DTO, 404 → null, non-200 → null.

import 'dart:convert';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:shared_preferences/shared_preferences.dart';

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
  _secureStore['token_expiry'] = '2000000000';
}

Map<String, dynamic> _fileJson({int id = 1}) => {
  'id': id,
  'originalFilename': 'file_$id.pdf',
  'contentType': 'application/pdf',
  'fileSize': 512,
  'fileCategory': 'MEDICAL_RECORD',
  'ownerId': 10,
  'ownerType': 'PATIENT',
  'fileName': 'file_$id.pdf',
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

  // ─── UserFileDTO.fromJson ────────────────────────────────────────────────

  group('UserFileDTO.fromJson', () {
    Map<String, dynamic> base() => {
          'id': 1,
          'originalFilename': 'report.pdf',
          'contentType': 'application/pdf',
          'fileSize': 2048,
          'fileCategory': 'MEDICAL_RECORD',
          'description': 'Annual report',
          'ownerId': 10,
          'ownerType': 'PATIENT',
          'patientId': 5,
          'fileUrl': 'https://example.com/report.pdf',
          'downloadUrl': 'https://example.com/download/report.pdf',
          'files': ['a.pdf'],
          'category': 'MEDICAL_RECORD',
          's3FullKey': 'bucket/key',
          'filename': 'report.pdf',
        };

    test('parses all fields from JSON', () {
      final dto = UserFileDTO.fromJson(base());
      expect(dto.id, 1);
      expect(dto.originalFilename, 'report.pdf');
      expect(dto.contentType, 'application/pdf');
      expect(dto.fileSize, 2048);
      expect(dto.fileCategory, 'MEDICAL_RECORD');
      expect(dto.description, 'Annual report');
      expect(dto.ownerId, 10);
      expect(dto.ownerType, 'PATIENT');
      expect(dto.patientId, 5);
      expect(dto.fileUrl, 'https://example.com/report.pdf');
      expect(dto.downloadUrl, 'https://example.com/download/report.pdf');
      expect(dto.s3FullKey, 'bucket/key');
      expect(dto.fileName, 'report.pdf');
    });

    test('null/missing optional fields use defaults', () {
      final dto = UserFileDTO.fromJson({
        'ownerId': 1,
        'ownerType': 'PATIENT',
      });
      expect(dto.id, 0);
      expect(dto.originalFilename, '');
      expect(dto.fileCategory, 'documents');
      expect(dto.description, isNull);
      expect(dto.patientId, isNull);
      expect(dto.fileName, '[Unnamed File]');
    });

    test('isImage true for image/* contentType', () {
      final dto = UserFileDTO.fromJson({...base(), 'contentType': 'image/png', 'ownerId': 1, 'ownerType': 'PATIENT'});
      expect(dto.isImage, isTrue);
    });

    test('isImage false for non-image contentType', () {
      final dto = UserFileDTO.fromJson(base());
      expect(dto.isImage, isFalse);
    });

    test('isPreviewable true for PDF', () {
      final dto = UserFileDTO.fromJson(base());
      expect(dto.isPreviewable, isTrue);
    });

    test('isPreviewable true for image', () {
      final dto = UserFileDTO.fromJson({...base(), 'contentType': 'image/jpeg'});
      expect(dto.isPreviewable, isTrue);
    });

    test('isPreviewable true for word document', () {
      final dto = UserFileDTO.fromJson({...base(), 'contentType': 'application/msword'});
      expect(dto.isPreviewable, isTrue);
    });

    test('fileIcon returns 📄 for PDF', () {
      final dto = UserFileDTO.fromJson(base());
      expect(dto.fileIcon, '📄');
    });

    test('fileIcon returns 🖼️ for image', () {
      final dto = UserFileDTO.fromJson({...base(), 'contentType': 'image/png'});
      expect(dto.fileIcon, '🖼️');
    });

    test('fileIcon returns 📁 for unknown type', () {
      final dto = UserFileDTO.fromJson({...base(), 'contentType': 'application/octet-stream'});
      expect(dto.fileIcon, '📁');
    });

    test('fileIcon returns 📝 for word document', () {
      final dto = UserFileDTO.fromJson({...base(), 'contentType': 'application/msword'});
      expect(dto.fileIcon, '📝');
    });

    test('fileIcon returns 📊 for spreadsheet', () {
      final dto = UserFileDTO.fromJson({...base(), 'contentType': 'application/vnd.ms-excel'});
      expect(dto.fileIcon, '📊');
    });

    test('fileIcon returns 📈 for presentation', () {
      final dto = UserFileDTO.fromJson({...base(), 'contentType': 'application/vnd.ms-powerpoint'});
      expect(dto.fileIcon, '📈');
    });

    test('fileIcon returns 🎥 for video', () {
      final dto = UserFileDTO.fromJson({...base(), 'contentType': 'video/mp4'});
      expect(dto.fileIcon, '🎥');
    });

    test('fileIcon returns 🎵 for audio', () {
      final dto = UserFileDTO.fromJson({...base(), 'contentType': 'audio/mpeg'});
      expect(dto.fileIcon, '🎵');
    });

    test('categoryDisplayName returns human-readable string', () {
      final dto = UserFileDTO.fromJson({...base(), 'fileCategory': 'MEDICAL_RECORD'});
      expect(dto.categoryDisplayName, 'Medical Record');
    });
  });

  // ─── UserFileDTO.toJson ──────────────────────────────────────────────────

  group('UserFileDTO.toJson', () {
    test('serializes all fields to a map', () {
      final dto = UserFileDTO(
        id: 7,
        originalFilename: 'x.pdf',
        contentType: 'application/pdf',
        fileSize: 100,
        fileCategory: 'OTHER_DOCUMENT',
        ownerId: 3,
        ownerType: 'CAREGIVER',
        createdAt: null,
        updatedAt: null,
        fileName: 'x.pdf',
      );
      final json = dto.toJson();
      expect(json['id'], 7);
      expect(json['originalFilename'], 'x.pdf');
      expect(json['fileCategory'], 'OTHER_DOCUMENT');
    });
  });

  // ─── FileUploadResponse.fromJson ─────────────────────────────────────────

  group('FileUploadResponse.fromJson', () {
    test('parses all fields', () {
      final resp = FileUploadResponse.fromJson({
        'fileId': 99,
        'originalFilename': 'doc.pdf',
        'fileUrl': 'https://s3.example.com/doc.pdf',
        'downloadUrl': 'https://s3.example.com/download/doc.pdf',
        'message': 'Uploaded',
        'fileName': 'doc.pdf',
      });
      expect(resp.fileId, 99);
      expect(resp.originalFilename, 'doc.pdf');
      expect(resp.fileUrl, 'https://s3.example.com/doc.pdf');
      expect(resp.downloadUrl, 'https://s3.example.com/download/doc.pdf');
      expect(resp.message, 'Uploaded');
      expect(resp.fileName, 'doc.pdf');
    });

    test('null/missing fields default to empty strings and 0', () {
      final resp = FileUploadResponse.fromJson({});
      expect(resp.fileId, 0);
      expect(resp.originalFilename, '');
      expect(resp.fileUrl, '');
    });
  });

  // ─── EnhancedFileService.getValidCategories ──────────────────────────────

  group('EnhancedFileService.getValidCategories', () {
    test('PATIENT includes MEDICAL_RECORD', () {
      final cats = EnhancedFileService.getValidCategories('PATIENT');
      expect(cats, contains('MEDICAL_RECORD'));
      expect(cats, contains('PROFILE_PICTURE'));
    });

    test('CAREGIVER includes CERTIFICATION', () {
      final cats = EnhancedFileService.getValidCategories('CAREGIVER');
      expect(cats, contains('CERTIFICATION'));
      expect(cats, isNot(contains('MEDICAL_RECORD')));
    });

    test('FAMILY_MEMBER includes AUTHORIZATION', () {
      final cats = EnhancedFileService.getValidCategories('FAMILY_MEMBER');
      expect(cats, contains('AUTHORIZATION'));
    });

    test('unknown type returns [OTHER_DOCUMENT]', () {
      expect(EnhancedFileService.getValidCategories('ADMIN'), ['OTHER_DOCUMENT']);
    });

    test('case-insensitive: patient = PATIENT', () {
      final cats = EnhancedFileService.getValidCategories('patient');
      expect(cats, contains('MEDICAL_RECORD'));
    });
  });

  // ─── EnhancedFileService.getCategoryDisplayNames ─────────────────────────

  group('EnhancedFileService.getCategoryDisplayNames', () {
    test('contains expected display names', () {
      final names = EnhancedFileService.getCategoryDisplayNames();
      expect(names['PROFILE_PICTURE'], 'Profile Picture');
      expect(names['MEDICAL_RECORD'], 'Medical Record');
      expect(names['PRESCRIPTION'], 'Prescription');
    });

    test('returns a non-empty map', () {
      expect(EnhancedFileService.getCategoryDisplayNames(), isNotEmpty);
    });
  });

  // ─── getCategoryDisplayName (top-level helper) ───────────────────────────

  group('getCategoryDisplayName', () {
    test('known category returns display name', () {
      expect(getCategoryDisplayName('PROFILE_PICTURE'), 'Profile Picture');
    });

    test('unknown category returns humanised version of key', () {
      // replaceAll('_', ' ').toLowerCase()
      expect(getCategoryDisplayName('UNKNOWN_TYPE'), 'unknown type');
    });
  });

  // ─── EnhancedFileService.downloadFile ────────────────────────────────────

  group('EnhancedFileService.downloadFile', () {
    test('200 → returns response body bytes', () async {
      final result = await http.runWithClient(
        () => EnhancedFileService.downloadFile(1),
        () => MockClient((_) async => http.Response('file-bytes', 200)),
      );
      expect(result, isNotNull);
      expect(result!.isNotEmpty, isTrue);
    });

    test('non-200 → returns null', () async {
      final result = await http.runWithClient(
        () => EnhancedFileService.downloadFile(99),
        () => MockClient(
          (_) async => http.Response(
            jsonEncode({'error': 'Not found'}),
            404,
          ),
        ),
      );
      expect(result, isNull);
    });

    test('network error → returns null', () async {
      final result = await http.runWithClient(
        () => EnhancedFileService.downloadFile(1),
        () => MockClient((_) async => throw Exception('timeout')),
      );
      expect(result, isNull);
    });
  });

  // ─── EnhancedFileService.downloadFileLegacy ───────────────────────────────

  group('EnhancedFileService.downloadFileLegacy', () {
    test('200 → returns response body bytes', () async {
      final result = await http.runWithClient(
        () => EnhancedFileService.downloadFileLegacy(1, 'path/to/file.pdf'),
        () => MockClient((_) async => http.Response('legacy-bytes', 200)),
      );
      expect(result, isNotNull);
    });

    test('non-200 → returns null', () async {
      final result = await http.runWithClient(
        () => EnhancedFileService.downloadFileLegacy(1, 'bad/path'),
        () => MockClient(
          (_) async => http.Response(
            jsonEncode({'error': 'Forbidden'}),
            403,
          ),
        ),
      );
      expect(result, isNull);
    });
  });

  // ─── EnhancedFileService.listMyFiles ─────────────────────────────────────

  group('EnhancedFileService.listMyFiles', () {
    test('200 → returns list of UserFileDTO', () async {
      final body = jsonEncode({
        'data': [_fileJson(id: 1), _fileJson(id: 2)],
      });
      final result = await http.runWithClient(
        () => EnhancedFileService.listMyFiles(),
        () => MockClient((_) async => http.Response(body, 200)),
      );
      expect(result.length, 2);
      expect(result[0], isA<UserFileDTO>());
    });

    test('200 with category filter → returns filtered list', () async {
      final body = jsonEncode({
        'data': [_fileJson(id: 3)],
      });
      final result = await http.runWithClient(
        () => EnhancedFileService.listMyFiles(category: 'PRESCRIPTION'),
        () => MockClient((_) async => http.Response(body, 200)),
      );
      expect(result.length, 1);
    });

    test('non-200 → returns empty list', () async {
      final result = await http.runWithClient(
        () => EnhancedFileService.listMyFiles(),
        () => MockClient(
          (_) async => http.Response(
            jsonEncode({'error': 'Unauthorized'}),
            401,
          ),
        ),
      );
      expect(result, isEmpty);
    });
  });

  // ─── EnhancedFileService.listPatientFiles ────────────────────────────────

  group('EnhancedFileService.listPatientFiles', () {
    test('200 → returns list of UserFileDTO', () async {
      final body = jsonEncode({
        'data': [_fileJson(id: 10)],
      });
      final result = await http.runWithClient(
        () => EnhancedFileService.listPatientFiles(5),
        () => MockClient((_) async => http.Response(body, 200)),
      );
      expect(result.length, 1);
    });

    test('non-200 → returns empty list', () async {
      final result = await http.runWithClient(
        () => EnhancedFileService.listPatientFiles(5),
        () => MockClient(
          (_) async => http.Response(
            jsonEncode({'error': 'Server error'}),
            500,
          ),
        ),
      );
      expect(result, isEmpty);
    });
  });

  // ─── EnhancedFileService.deleteFile ──────────────────────────────────────

  group('EnhancedFileService.deleteFile', () {
    test('200 → returns true', () async {
      final result = await http.runWithClient(
        () => EnhancedFileService.deleteFile(42),
        () => MockClient((_) async => http.Response('', 200)),
      );
      expect(result, isTrue);
    });

    test('non-200 → returns false', () async {
      final result = await http.runWithClient(
        () => EnhancedFileService.deleteFile(99),
        () => MockClient(
          (_) async => http.Response(
            jsonEncode({'error': 'Not found'}),
            404,
          ),
        ),
      );
      expect(result, isFalse);
    });

    test('network error → returns false', () async {
      final result = await http.runWithClient(
        () => EnhancedFileService.deleteFile(1),
        () => MockClient((_) async => throw Exception('network error')),
      );
      expect(result, isFalse);
    });
  });

  // ─── EnhancedFileService.getProfileImage ─────────────────────────────────

  group('EnhancedFileService.getProfileImage', () {
    test('200 → returns UserFileDTO', () async {
      final body = jsonEncode({'data': _fileJson(id: 7)});
      final result = await http.runWithClient(
        () => EnhancedFileService.getProfileImage(),
        () => MockClient((_) async => http.Response(body, 200)),
      );
      expect(result, isNotNull);
      expect(result, isA<UserFileDTO>());
    });

    test('404 → returns null (no profile image is not an error)', () async {
      final result = await http.runWithClient(
        () => EnhancedFileService.getProfileImage(),
        () => MockClient((_) async => http.Response('', 404)),
      );
      expect(result, isNull);
    });

    test('non-200 non-404 → returns null', () async {
      final result = await http.runWithClient(
        () => EnhancedFileService.getProfileImage(),
        () => MockClient(
          (_) async => http.Response(
            jsonEncode({'error': 'Server error'}),
            500,
          ),
        ),
      );
      expect(result, isNull);
    });
  });
}
