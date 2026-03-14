// Tests for PatientNote, PatientNotesService (pure methods and HTTP methods).
//
// Coverage strategy:
//   PatientNote is a pure data class with fromJson / toJson / fromUserFileDTO
//   and two private static helpers.  These are exercised without HTTP.
//   PatientNotesService.getNoteCategories and getCategoryDisplayNames are pure.
//   HTTP-backed methods (getPatientNotes, deletePatientNote, downloadPatientNote)
//   delegate to EnhancedFileService which uses http.get/delete interceptable via
//   http.runWithClient + MockClient.  AuthTokenManager intercepted via
//   FlutterSecureStorage stub.
//
//   Branches tested:
//     PatientNote.fromJson — all fields present, missing fields default, category
//       display-name mapping for all six categories.
//     PatientNote.toJson — round-trip for key fields.
//     PatientNote.fromUserFileDTO — maps UserFileDTO fields correctly for all
//       MEDICAL_NOTE/LAB_RESULT/APPOINTMENT/PRESCRIPTION/GENERAL_NOTE/CARE_NOTE/default.
//     PatientNotesService.getNoteCategories — returns all six legacy categories.
//     PatientNotesService.getCategoryDisplayNames — maps all six categories.
//     getPatientNote / updatePatientNote — always returns null (stub implementation).
//     getPatientNotes — 200 → list, exercising all 6 category-mapping switch branches.
//     deletePatientNote — 200 → true, non-200 → false.
//     downloadPatientNote — success → string, null bytes → null.

import 'dart:convert';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:care_connect_app/services/medical_notes_service.dart';
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

Map<String, dynamic> _fileDtoJson({String category = 'MEDICAL_NOTE'}) => {
  'id': 1,
  'originalFilename': 'note.pdf',
  'contentType': 'application/pdf',
  'fileSize': 512,
  'fileCategory': category,
  'ownerId': 10,
  'ownerType': 'PATIENT',
  'fileName': 'note.pdf',
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

  // ─── PatientNote.fromJson ────────────────────────────────────────────────

  group('PatientNote.fromJson', () {
    Map<String, dynamic> fullJson() => {
          'id': 42,
          'title': 'Checkup',
          'content': 'All clear',
          'fileName': 'checkup.pdf',
          'fileUrl': 'https://example.com/checkup.pdf',
          'uploadedBy': 'Dr Smith',
          'uploadDate': '2025-01-15T10:00:00.000Z',
          'category': 'medicalNote',
          'patientId': 7,
        };

    test('parses all fields from full JSON', () {
      final note = PatientNote.fromJson(fullJson());
      expect(note.id, 42);
      expect(note.title, 'Checkup');
      expect(note.content, 'All clear');
      expect(note.fileName, 'checkup.pdf');
      expect(note.fileUrl, 'https://example.com/checkup.pdf');
      expect(note.uploadedBy, 'Dr Smith');
      expect(note.category, 'medicalNote');
      expect(note.patientId, 7);
    });

    test('missing fields use defaults', () {
      final note = PatientNote.fromJson({});
      expect(note.id, 0);
      expect(note.title, '');
      expect(note.content, '');
      expect(note.uploadedBy, 'Unknown');
      expect(note.category, 'generalNote');
    });

    test('uses originalFilename fallback when fileName missing', () {
      final note = PatientNote.fromJson({
        'originalFilename': 'backup.pdf',
      });
      expect(note.fileName, 'backup.pdf');
    });

    test('uses downloadUrl fallback when fileUrl missing', () {
      final note = PatientNote.fromJson({
        'downloadUrl': 'https://example.com/download',
      });
      expect(note.fileUrl, 'https://example.com/download');
    });

    test('uses createdAt fallback when uploadDate missing', () {
      final note = PatientNote.fromJson({
        'createdAt': '2025-03-01T00:00:00.000Z',
      });
      expect(note.uploadDate.year, 2025);
    });

    // Category display-name mapping
    const categoryMappings = {
      'medicalNote': 'Medical Note',
      'MEDICAL_NOTE': 'Medical Note',
      'labResult': 'Lab Result',
      'LAB_RESULT': 'Lab Result',
      'appointment': 'Appointment',
      'APPOINTMENT': 'Appointment',
      'prescription': 'Prescription',
      'PRESCRIPTION': 'Prescription',
      'generalNote': 'General Note',
      'GENERAL_NOTE': 'General Note',
      'careNote': 'Care Note',
      'CARE_NOTE': 'Care Note',
    };

    for (final entry in categoryMappings.entries) {
      test('category "${entry.key}" → noteType "${entry.value}"', () {
        final note = PatientNote.fromJson({'category': entry.key});
        expect(note.noteType, entry.value);
      });
    }

    test('unknown category → noteType "Note"', () {
      final note = PatientNote.fromJson({'category': 'unknownType'});
      expect(note.noteType, 'Note');
    });
  });

  // ─── PatientNote.toJson ──────────────────────────────────────────────────

  group('PatientNote.toJson', () {
    test('serializes key fields correctly', () {
      final note = PatientNote(
        id: 10,
        title: 'Test',
        content: 'Body',
        fileName: 'file.pdf',
        fileUrl: 'https://url',
        uploadedBy: 'User',
        uploadDate: DateTime(2025, 6, 1),
        category: 'generalNote',
        noteType: 'General Note',
        patientId: 3,
      );
      final json = note.toJson();
      expect(json['id'], 10);
      expect(json['title'], 'Test');
      expect(json['category'], 'generalNote');
      expect(json['patientId'], 3);
    });
  });

  // ─── PatientNote.fromUserFileDTO ─────────────────────────────────────────

  group('PatientNote.fromUserFileDTO', () {
    UserFileDTO makeDto({String category = 'MEDICAL_NOTE'}) => UserFileDTO(
          id: 5,
          originalFilename: 'report.pdf',
          contentType: 'application/pdf',
          fileSize: 512,
          fileCategory: category,
          ownerId: 2,
          ownerType: 'PATIENT',
          patientId: 9,
          createdAt: DateTime(2025, 1, 1),
          updatedAt: DateTime(2025, 1, 1),
          fileUrl: 'https://example.com/report.pdf',
          downloadUrl: 'https://example.com/dl/report.pdf',
          fileName: 'report.pdf',
        );

    test('maps id, title, patientId from DTO', () {
      final note = PatientNote.fromUserFileDTO(makeDto());
      expect(note.id, 5);
      expect(note.title, 'report.pdf');
      expect(note.patientId, 9);
    });

    test('MEDICAL_NOTE → category medicalNote', () {
      final note = PatientNote.fromUserFileDTO(makeDto(category: 'MEDICAL_NOTE'));
      expect(note.category, 'medicalNote');
    });

    test('LAB_RESULT → category labResult', () {
      final note = PatientNote.fromUserFileDTO(makeDto(category: 'LAB_RESULT'));
      expect(note.category, 'labResult');
    });

    test('GENERAL_NOTE → category generalNote', () {
      final note = PatientNote.fromUserFileDTO(makeDto(category: 'GENERAL_NOTE'));
      expect(note.category, 'generalNote');
    });

    test('CARE_NOTE → category careNote', () {
      final note = PatientNote.fromUserFileDTO(makeDto(category: 'CARE_NOTE'));
      expect(note.category, 'careNote');
    });

    test('APPOINTMENT → category appointment', () {
      final note = PatientNote.fromUserFileDTO(makeDto(category: 'APPOINTMENT'));
      expect(note.category, 'appointment');
    });

    test('PRESCRIPTION → category prescription', () {
      final note = PatientNote.fromUserFileDTO(makeDto(category: 'PRESCRIPTION'));
      expect(note.category, 'prescription');
    });

    test('unknown category → falls back to generalNote', () {
      final note = PatientNote.fromUserFileDTO(makeDto(category: 'OTHER'));
      expect(note.category, 'generalNote');
    });
  });

  // ─── PatientNotesService pure helpers ────────────────────────────────────

  group('PatientNotesService.getNoteCategories', () {
    test('returns all six legacy categories', () {
      final cats = PatientNotesService.getNoteCategories();
      expect(cats, containsAll([
        'generalNote',
        'medicalNote',
        'labResult',
        'appointment',
        'prescription',
        'careNote',
      ]));
      expect(cats.length, 6);
    });
  });

  group('PatientNotesService.getCategoryDisplayNames', () {
    test('maps all six categories to display names', () {
      final names = PatientNotesService.getCategoryDisplayNames();
      expect(names['generalNote'], 'General Note');
      expect(names['medicalNote'], 'Medical Note');
      expect(names['labResult'], 'Lab Result');
      expect(names['appointment'], 'Appointment');
      expect(names['prescription'], 'Prescription');
      expect(names['careNote'], 'Care Note');
    });
  });

  group('PatientNotesService stub methods', () {
    test('getPatientNote always returns null', () async {
      final result = await PatientNotesService.getPatientNote(1);
      expect(result, isNull);
    });

    test('updatePatientNote always returns null', () async {
      final result = await PatientNotesService.updatePatientNote(
        noteId: 1,
        title: 'x',
      );
      expect(result, isNull);
    });
  });

  // ─── PatientNotesService.getPatientNotes ─────────────────────────────────

  group('PatientNotesService.getPatientNotes', () {
    test('no category → returns list from backend', () async {
      final body = jsonEncode({'data': [_fileDtoJson()]});
      final result = await http.runWithClient(
        () => PatientNotesService.getPatientNotes(1),
        () => MockClient((_) async => http.Response(body, 200)),
      );
      expect(result, isA<List<PatientNote>>());
      expect(result.length, 1);
    });

    test('medicalNote category → maps to MEDICAL_NOTE', () async {
      final body = jsonEncode({'data': [_fileDtoJson(category: 'MEDICAL_NOTE')]});
      final result = await http.runWithClient(
        () => PatientNotesService.getPatientNotes(1, category: 'medicalNote'),
        () => MockClient((_) async => http.Response(body, 200)),
      );
      expect(result, isA<List>());
    });

    test('labResult category → maps to LAB_RESULT', () async {
      final body = jsonEncode({'data': []});
      final result = await http.runWithClient(
        () => PatientNotesService.getPatientNotes(1, category: 'labResult'),
        () => MockClient((_) async => http.Response(body, 200)),
      );
      expect(result, isEmpty);
    });

    test('appointment category → maps to APPOINTMENT', () async {
      final body = jsonEncode({'data': []});
      final result = await http.runWithClient(
        () => PatientNotesService.getPatientNotes(1, category: 'appointment'),
        () => MockClient((_) async => http.Response(body, 200)),
      );
      expect(result, isEmpty);
    });

    test('prescription category → maps to PRESCRIPTION', () async {
      final body = jsonEncode({'data': []});
      final result = await http.runWithClient(
        () => PatientNotesService.getPatientNotes(1, category: 'prescription'),
        () => MockClient((_) async => http.Response(body, 200)),
      );
      expect(result, isEmpty);
    });

    test('generalNote category → maps to GENERAL_NOTE', () async {
      final body = jsonEncode({'data': []});
      final result = await http.runWithClient(
        () => PatientNotesService.getPatientNotes(1, category: 'generalNote'),
        () => MockClient((_) async => http.Response(body, 200)),
      );
      expect(result, isEmpty);
    });

    test('careNote category → maps to CARE_NOTE', () async {
      final body = jsonEncode({'data': []});
      final result = await http.runWithClient(
        () => PatientNotesService.getPatientNotes(1, category: 'careNote'),
        () => MockClient((_) async => http.Response(body, 200)),
      );
      expect(result, isEmpty);
    });

    test('unknown category → uppercased passthrough', () async {
      final body = jsonEncode({'data': []});
      final result = await http.runWithClient(
        () => PatientNotesService.getPatientNotes(1, category: 'custom'),
        () => MockClient((_) async => http.Response(body, 200)),
      );
      expect(result, isEmpty);
    });

    test('backend error → returns empty list', () async {
      final result = await http.runWithClient(
        () => PatientNotesService.getPatientNotes(1),
        () => MockClient(
          (_) async => http.Response(jsonEncode({'error': 'err'}), 500),
        ),
      );
      expect(result, isEmpty);
    });
  });

  // ─── PatientNotesService.deletePatientNote ────────────────────────────────

  group('PatientNotesService.deletePatientNote', () {
    test('200 → returns true', () async {
      final result = await http.runWithClient(
        () => PatientNotesService.deletePatientNote(42),
        () => MockClient((_) async => http.Response('', 200)),
      );
      expect(result, isTrue);
    });

    test('non-200 → returns false', () async {
      final result = await http.runWithClient(
        () => PatientNotesService.deletePatientNote(99),
        () => MockClient(
          (_) async => http.Response(jsonEncode({'error': 'Not found'}), 404),
        ),
      );
      expect(result, isFalse);
    });
  });

  // ─── PatientNotesService.downloadPatientNote ──────────────────────────────

  group('PatientNotesService.downloadPatientNote', () {
    test('success → returns "downloaded" string', () async {
      final result = await http.runWithClient(
        () => PatientNotesService.downloadPatientNote(1),
        () => MockClient((_) async => http.Response('file-bytes', 200)),
      );
      expect(result, 'downloaded');
    });

    test('non-200 → returns null', () async {
      final result = await http.runWithClient(
        () => PatientNotesService.downloadPatientNote(99),
        () => MockClient(
          (_) async => http.Response(jsonEncode({'error': 'err'}), 404),
        ),
      );
      expect(result, isNull);
    });
  });
}
