import 'dart:convert';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

import 'package:care_connect_app/features/notetaker/models/patient_note_model.dart';
import 'package:care_connect_app/services/notetaker_config_service.dart';

MockClient _mockJson(int statusCode, Object body) =>
    MockClient((_) async => http.Response(jsonEncode(body), statusCode));

MockClient _mockRaw(int statusCode, String rawBody) =>
    MockClient((_) async => http.Response(rawBody, statusCode));

MockClient _mockThrows(Object error) => MockClient((_) async => throw error);

(MockClient, List<http.Request>) _capturingClient(int statusCode, Object body) {
  final captured = <http.Request>[];
  final client = MockClient((req) async {
    captured.add(req);
    return http.Response(jsonEncode(body), statusCode);
  });
  return (client, captured);
}

String? _headerValue(http.BaseRequest request, String name) {
  final expectedName = name.toLowerCase();
  for (final entry in request.headers.entries) {
    if (entry.key.toLowerCase() == expectedName) {
      return entry.value;
    }
  }
  return null;
}

Map<String, dynamic> get _fullConfigJson => {
      'id': 42,
      'patientId': 7,
      'isEnabled': true,
      'permitCaregiverAccess': true,
      'triggerKeywords': [
        {'keyword': 'test_keyword', 'event_type': 'ALERT'},
      ],
      'updatedAt': '2024-06-15T10:30:00.000',
    };

Map<String, dynamic> get _minimalConfigJson => {
      'patientId': 1,
      'isEnabled': true,
      'permitCaregiverAccess': false,
    };

Map<String, dynamic> _noteJson({
  String id = '1',
  String patientId = '5',
  String note = 'Test note',
  String aiSummary = 'Summary',
  String createdAt = '2024-01-01T00:00:00.000',
  String updatedAt = '2024-01-01T00:00:00.000',
}) =>
    {
      'id': id,
      'patientId': patientId,
      'note': note,
      'aiSummary': aiSummary,
      'createdAt': createdAt,
      'updatedAt': updatedAt,
    };

PatientNote _buildNote({
  String id = '1',
  String patientId = '5',
  String note = 'Test note',
  String aiSummary = '',
}) =>
    PatientNote(
      id: id,
      patientId: patientId,
      note: note,
      aiSummary: aiSummary,
      createdAt: DateTime(2024, 1, 1),
      updatedAt: DateTime(2024, 1, 1),
    );

const MethodChannel _secureStorageChannel =
    MethodChannel('plugins.it_nomads.com/flutter_secure_storage');
const String _testJwtToken = 'test-jwt-token';

String _futureTokenExpiry() =>
    '${DateTime.now().add(const Duration(hours: 1)).millisecondsSinceEpoch ~/ 1000}';

String? _storageKey(dynamic arguments) {
  if (arguments is Map) {
    final dynamic key = arguments['key'];
    return key is String ? key : null;
  }
  return null;
}

void _mockSecureStorage({
  String? jwtToken,
  String? tokenExpiry,
}) {
  TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
      .setMockMethodCallHandler(_secureStorageChannel, (call) async {
    if (call.method != 'read') {
      return null;
    }

    switch (_storageKey(call.arguments)) {
      case 'jwt_token':
        return jwtToken;
      case 'token_expiry':
        return tokenExpiry;
      default:
        return null;
    }
  });
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    _mockSecureStorage();
  });

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(_secureStorageChannel, null);
  });

  // --------------- PatientNotetakerKeyword.toJson() ---------------

  test('toJson() serialises keyword and event_type', () {
    final json = PatientNotetakerKeyword(
            keyword: 'PII_Social Security', event_type: 'ALERT')
        .toJson();
    expect(json['keyword'], 'PII_Social Security');
    expect(json['event_type'], 'ALERT');
    expect(json.keys.length, 2);
  });

  // --------------- PatientNotetakerConfigDTO.fromJson() ---------------

  test('fromJson() parses all scalar fields from a full JSON object', () {
    final dto = PatientNotetakerConfigDTO.fromJson(_fullConfigJson);
    expect(dto.id, 42);
    expect(dto.patientId, 7);
    expect(dto.isEnabled, true);
    expect(dto.permitCaregiverAccess, true);
    expect(dto.updatedAt, isA<DateTime>());
    expect(dto.updatedAt!.year, 2024);
  });

  test('fromJson() parses triggerKeywords list', () {
    final dto = PatientNotetakerConfigDTO.fromJson(_fullConfigJson);
    expect(dto.triggerKeywords.length, 1);
    expect(dto.triggerKeywords.first.keyword, 'test_keyword');
    expect(dto.triggerKeywords.first.event_type, 'ALERT');
  });

  test('fromJson() handles absent optional fields as null or empty', () {
    final dto = PatientNotetakerConfigDTO.fromJson(_minimalConfigJson);
    expect(dto.id, isNull);
    expect(dto.updatedAt, isNull);
    expect(dto.triggerKeywords, isEmpty);
  });

  test('fromJson() handles explicitly null updatedAt', () {
    final dto = PatientNotetakerConfigDTO.fromJson(
        {..._minimalConfigJson, 'updatedAt': null});
    expect(dto.updatedAt, isNull);
  });

  test('fromJson() parses multiple triggerKeywords entries', () {
    final json = {
      ..._minimalConfigJson,
      'triggerKeywords': [
        {'keyword': 'kw1', 'event_type': 'ALERT'},
        {'keyword': 'kw2', 'event_type': 'INFO'},
        {'keyword': 'kw3', 'event_type': 'ALERT'},
      ],
    };
    expect(PatientNotetakerConfigDTO.fromJson(json).triggerKeywords.length, 3);
  });

  // This locks in the current buggy behavior until
  // the production parser is deliberately fixed.
  test(
    'fromJson() currently throws TypeError when isEnabled is null',
    () {
      final json = {
        'patientId': 1,
        'isEnabled': null,
        'permitCaregiverAccess': false,
      };
      expect(
        () => PatientNotetakerConfigDTO.fromJson(json),
        throwsA(isA<TypeError>()),
      );
    },
  );

  // --------------- PatientNotetakerConfigDTO.toJson() ---------------

  test('toJson() includes id when set and omits it when null', () {
    final withId = PatientNotetakerConfigDTO.fromJson(_fullConfigJson).toJson();
    expect(withId.containsKey('id'), true);
    expect(withId['id'], 42);

    final withoutId =
        PatientNotetakerConfigDTO.fromJson(_minimalConfigJson).toJson();
    expect(withoutId.containsKey('id'), false);
  });

  test('toJson() includes all required fields with correct values', () {
    final json = PatientNotetakerConfigDTO.fromJson(_fullConfigJson).toJson();
    expect(json['patientId'], 7);
    expect(json['isEnabled'], true);
    expect(json['permitCaregiverAccess'], true);
    expect(json['triggerKeywords'], isA<List>());
    final first = (json['triggerKeywords'] as List).first as Map;
    expect(first['keyword'], 'test_keyword');
    expect(first['event_type'], 'ALERT');
  });

  test('toJson() round-trip preserves patientId and isEnabled', () {
    final original = PatientNotetakerConfigDTO.fromJson(_fullConfigJson);
    final reparsed = PatientNotetakerConfigDTO.fromJson(original.toJson());
    expect(reparsed.patientId, original.patientId);
    expect(reparsed.isEnabled, original.isEnabled);
  });

  // --------------- PatientNotetakerConfigDTO.copyWith() ---------------

  test(
      'copyWith() with no arguments returns a new object preserving all fields',
      () {
    final original = PatientNotetakerConfigDTO.fromJson(_fullConfigJson);
    final copy = original.copyWith();
    expect(identical(copy, original), false);
    expect(copy.id, original.id);
    expect(copy.patientId, original.patientId);
    expect(copy.isEnabled, original.isEnabled);
    expect(copy.permitCaregiverAccess, original.permitCaregiverAccess);
  });

  test('copyWith() overrides each field independently and preserves the rest',
      () {
    final original = PatientNotetakerConfigDTO.fromJson(_fullConfigJson);
    expect(original.copyWith(id: 500).id, 500);
    expect(original.copyWith(patientId: 99).patientId, 99);
    expect(original.copyWith(isEnabled: false).isEnabled, false);
    expect(
        original.copyWith(permitCaregiverAccess: false).permitCaregiverAccess,
        false);
    expect(original.copyWith(triggerKeywords: []).triggerKeywords, isEmpty);
    expect(
        original.copyWith(updatedAt: DateTime(2025)).updatedAt, DateTime(2025));
    final copy = original.copyWith(isEnabled: false);
    expect(copy.patientId, original.patientId);
    expect(copy.permitCaregiverAccess, original.permitCaregiverAccess);
    expect(copy.id, original.id);
  });

  // --------------- PatientNote.fromJson() fallback handling ---------------

  test(
      'PatientNote.fromJson() falls back to current time for invalid timestamps',
      () {
    final before = DateTime.now();
    final note = PatientNote.fromJson(
      _noteJson(createdAt: 'not-a-date', updatedAt: 'still-not-a-date'),
    );
    final after = DateTime.now();

    expect(
      note.createdAt.millisecondsSinceEpoch,
      inInclusiveRange(
        before.millisecondsSinceEpoch,
        after.millisecondsSinceEpoch,
      ),
    );
    expect(
      note.updatedAt.millisecondsSinceEpoch,
      inInclusiveRange(
        before.millisecondsSinceEpoch,
        after.millisecondsSinceEpoch,
      ),
    );
  });

  test('PatientNote.fromJson() falls back to current time for null timestamps',
      () {
    final before = DateTime.now();
    final note = PatientNote.fromJson({
      ..._noteJson(),
      'createdAt': null,
      'updatedAt': null,
    });
    final after = DateTime.now();

    expect(
      note.createdAt.millisecondsSinceEpoch,
      inInclusiveRange(
        before.millisecondsSinceEpoch,
        after.millisecondsSinceEpoch,
      ),
    );
    expect(
      note.updatedAt.millisecondsSinceEpoch,
      inInclusiveRange(
        before.millisecondsSinceEpoch,
        after.millisecondsSinceEpoch,
      ),
    );
  });

  // --------------- getUserNotetakerConfig() — HTTP behaviour ---------------
  // context param is typed dynamic and unused in the method body; null is safe.

  test('getUserNotetakerConfig() returns a populated DTO on HTTP 200',
      () async {
    final result = await http.runWithClient(
      () => NotetakerConfigService.getUserNotetakerConfig(7, null),
      () => _mockJson(200, _fullConfigJson),
    );
    expect(result, isA<PatientNotetakerConfigDTO>());
    expect(result!.patientId, 7);
    expect(result.isEnabled, true);
  });

  test('getUserNotetakerConfig() returns the default config on HTTP 404',
      () async {
    final result = await http.runWithClient(
      () => NotetakerConfigService.getUserNotetakerConfig(42, null),
      () => _mockRaw(404, 'Not Found'),
    );
    expect(result, isA<PatientNotetakerConfigDTO>());
    expect(result!.patientId, 42);
    expect(result.isEnabled, true);
    expect(result.permitCaregiverAccess, false);
    expect(result.id, isNull);
    expect(result.triggerKeywords.length, 2);
    expect(
      result.triggerKeywords.map((k) => k.keyword),
      containsAll(['PII_Social Security', 'PII_Credit Card']),
    );
  });

  test(
      'getUserNotetakerConfig() returns null on non-2xx status or transport exception',
      () async {
    for (final (label, mock) in [
      ('HTTP 500', _mockRaw(500, 'Server Error')),
      ('HTTP 403', _mockRaw(403, 'Forbidden')),
      ('HTTP 401', _mockRaw(401, 'Unauthorized')),
      (
        'transport exception',
        _mockThrows(http.ClientException('connection refused'))
      ),
    ]) {
      final result = await http.runWithClient(
        () => NotetakerConfigService.getUserNotetakerConfig(7, null),
        () => mock,
      );
      expect(result, isNull, reason: label);
    }
  });

  // --------------- getUserNotetakerConfig() — request construction ---------------

  test('getUserNotetakerConfig() sends a GET request to the correct path',
      () async {
    _mockSecureStorage(
      jwtToken: _testJwtToken,
      tokenExpiry: _futureTokenExpiry(),
    );
    final (client, captured) = _capturingClient(200, _fullConfigJson);
    await http.runWithClient(
      () => NotetakerConfigService.getUserNotetakerConfig(7, null),
      () => client,
    );
    expect(captured.single.method, 'GET');
    expect(captured.single.url.path, '/v1/api/patient-notetaker/7/config');
    expect(_headerValue(captured.single, 'Content-Type'), 'application/json');
    expect(_headerValue(captured.single, 'Authorization'),
        'Bearer $_testJwtToken');
  });

  // --------------- saveUserNotetakerConfig() — HTTP behaviour ---------------

  test('saveUserNotetakerConfig() returns a populated DTO on HTTP 200 and 201',
      () async {
    final config = PatientNotetakerConfigDTO(
      patientId: 5,
      isEnabled: true,
      permitCaregiverAccess: false,
      triggerKeywords: [],
    );
    for (final statusCode in [200, 201]) {
      final result = await http.runWithClient(
        () =>
            NotetakerConfigService.saveUserNotetakerConfig(config, userId: 10),
        () => _mockJson(statusCode, {..._fullConfigJson, 'patientId': 5}),
      );
      expect(result, isA<PatientNotetakerConfigDTO>(),
          reason: 'status $statusCode');
      expect(result!.patientId, 5, reason: 'status $statusCode');
    }
  });

  test(
      'saveUserNotetakerConfig() returns null on non-2xx status or transport exception',
      () async {
    final config = PatientNotetakerConfigDTO(
      patientId: 5,
      isEnabled: true,
      permitCaregiverAccess: false,
      triggerKeywords: [],
    );
    for (final (label, mock) in [
      ('HTTP 400', _mockRaw(400, 'Bad Request')),
      ('HTTP 404', _mockRaw(404, 'Not Found')),
      ('HTTP 500', _mockRaw(500, 'Server Error')),
      (
        'transport exception',
        _mockThrows(http.ClientException('network error'))
      ),
    ]) {
      final result = await http.runWithClient(
        () =>
            NotetakerConfigService.saveUserNotetakerConfig(config, userId: 10),
        () => mock,
      );
      expect(result, isNull, reason: label);
    }
  });

  // --------------- saveUserNotetakerConfig() — request construction ---------------

  test('saveUserNotetakerConfig() sends a PUT request to the correct path',
      () async {
    _mockSecureStorage(
      jwtToken: _testJwtToken,
      tokenExpiry: _futureTokenExpiry(),
    );
    final config = PatientNotetakerConfigDTO(
      id: 99,
      patientId: 5,
      isEnabled: true,
      permitCaregiverAccess: false,
      triggerKeywords: [
        PatientNotetakerKeyword(keyword: 'test', event_type: 'ALERT')
      ],
    );
    final (client, captured) =
        _capturingClient(200, {..._fullConfigJson, 'patientId': 5});
    await http.runWithClient(
      () => NotetakerConfigService.saveUserNotetakerConfig(config, userId: 10),
      () => client,
    );
    expect(captured.single.method, 'PUT');
    expect(captured.single.url.path, '/v1/api/patient-notetaker/5/config');
    expect(_headerValue(captured.single, 'Content-Type'), 'application/json');
    expect(_headerValue(captured.single, 'Accept'), '*/*');
    expect(_headerValue(captured.single, 'Authorization'),
        'Bearer $_testJwtToken');
  });

  test(
      'saveUserNotetakerConfig() request body contains all required fields and excludes config id',
      () async {
    _mockSecureStorage(
      jwtToken: _testJwtToken,
      tokenExpiry: _futureTokenExpiry(),
    );
    final config = PatientNotetakerConfigDTO(
      id: 99,
      patientId: 5,
      isEnabled: true,
      permitCaregiverAccess: false,
      triggerKeywords: [
        PatientNotetakerKeyword(keyword: 'test', event_type: 'ALERT')
      ],
    );
    final (client, captured) =
        _capturingClient(200, {..._fullConfigJson, 'patientId': 5});
    await http.runWithClient(
      () => NotetakerConfigService.saveUserNotetakerConfig(config, userId: 10),
      () => client,
    );
    final body = jsonDecode(captured.single.body) as Map<String, dynamic>;
    expect(body['userId'], 10);
    expect(body['patientId'], 5);
    expect(body['isEnabled'], true);
    expect(body['permitCaregiverAccess'], false);
    expect(body['triggerKeywords'], isA<List>());
    final kw = (body['triggerKeywords'] as List).first as Map;
    expect(kw['keyword'], 'test');
    expect(kw['event_type'], 'ALERT');
    // The service builds its own body map and does NOT forward config.id.
    expect(body.containsKey('id'), false);
  });

  // --------------- getPatientNotes() — HTTP behaviour ---------------

  test(
      'getPatientNotes() returns notes for all three supported 200 response body shapes',
      () async {
    for (final (label, body) in [
      ('bare List', [_noteJson()] as Object),
      (
        '{data:[]}',
        {
          'data': [_noteJson()]
        }
      ),
      (
        '{notes:[]}',
        {
          'notes': [_noteJson()]
        }
      ),
    ]) {
      final result = await http.runWithClient(
        () => NotetakerConfigService.getPatientNotes(5),
        () => _mockJson(200, body),
      );
      expect(result.length, 1, reason: label);
      expect(result.first.note, 'Test note', reason: label);
    }
  });

  test('getPatientNotes() returns empty list on unknown map shape', () async {
    final result = await http.runWithClient(
      () => NotetakerConfigService.getPatientNotes(5),
      () => _mockJson(200, {
        'result': [_noteJson()]
      }),
    );
    expect(result, isEmpty);
  });

  test('getPatientNotes() skips malformed note entries and returns the rest',
      () async {
    final result = await http.runWithClient(
      () => NotetakerConfigService.getPatientNotes(5),
      () => _mockJson(200, [42, _noteJson(id: 'good-note')]),
    );
    expect(result.length, 1);
    expect(result.first.id, 'good-note');
  });

  test(
      'getPatientNotes() returns empty list on HTTP 404, 500, or transport exception',
      () async {
    for (final (label, mock) in [
      ('HTTP 404', _mockRaw(404, 'Not Found')),
      ('HTTP 500', _mockRaw(500, 'Server Error')),
      ('transport exception', _mockThrows(http.ClientException('timeout'))),
    ]) {
      final result = await http.runWithClient(
        () => NotetakerConfigService.getPatientNotes(5),
        () => mock,
      );
      expect(result, isEmpty, reason: label);
    }
  });

  // --------------- getPatientNotes() — request construction ---------------

  test('getPatientNotes() sends a GET request to the correct path', () async {
    _mockSecureStorage(
      jwtToken: _testJwtToken,
      tokenExpiry: _futureTokenExpiry(),
    );
    final (client, captured) = _capturingClient(200, <dynamic>[]);
    await http.runWithClient(
      () => NotetakerConfigService.getPatientNotes(33),
      () => client,
    );
    expect(captured.single.method, 'GET');
    expect(captured.single.url.path, '/v1/api/patient-notetaker/33/notes');
    expect(_headerValue(captured.single, 'Content-Type'), 'application/json');
    expect(_headerValue(captured.single, 'Authorization'),
        'Bearer $_testJwtToken');
  });

  // --------------- createPatientNote() — HTTP behaviour ---------------

  test(
      'createPatientNote() returns a PatientNote with correct id on HTTP 200 and 201',
      () async {
    for (final statusCode in [200, 201]) {
      final result = await http.runWithClient(
        () => NotetakerConfigService.createPatientNote(_buildNote()),
        () => _mockJson(statusCode, _noteJson(id: 'created-1')),
      );
      expect(result, isA<PatientNote>(), reason: 'status $statusCode');
      expect(result.id, 'created-1', reason: 'status $statusCode');
    }
  });

  test(
      'createPatientNote() throws an Exception on non-2xx status or transport exception',
      () async {
    for (final (label, mock) in [
      ('HTTP 400', _mockRaw(400, 'Bad Request')),
      ('HTTP 500', _mockRaw(500, 'Server Error')),
      (
        'transport exception',
        _mockThrows(http.ClientException('network down'))
      ),
    ]) {
      await expectLater(
        () => http.runWithClient(
          () => NotetakerConfigService.createPatientNote(_buildNote()),
          () => mock,
        ),
        throwsA(isA<Exception>()),
        reason: label,
      );
    }
  });

  // --------------- createPatientNote() — request construction ---------------

  test('createPatientNote() sends a POST request to the correct path',
      () async {
    _mockSecureStorage(
      jwtToken: _testJwtToken,
      tokenExpiry: _futureTokenExpiry(),
    );
    final (client, captured) = _capturingClient(200, _noteJson());
    await http.runWithClient(
      () =>
          NotetakerConfigService.createPatientNote(_buildNote(patientId: '5')),
      () => client,
    );
    expect(captured.single.method, 'POST');
    expect(captured.single.url.path, '/v1/api/patient-notetaker/5/notes');
    expect(_headerValue(captured.single, 'Content-Type'), 'application/json');
    expect(_headerValue(captured.single, 'Accept'), '*/*');
    expect(_headerValue(captured.single, 'Authorization'),
        'Bearer $_testJwtToken');
  });

  test('createPatientNote() request body includes note text and patientId',
      () async {
    _mockSecureStorage(
      jwtToken: _testJwtToken,
      tokenExpiry: _futureTokenExpiry(),
    );
    final (client, captured) = _capturingClient(200, _noteJson());
    await http.runWithClient(
      () => NotetakerConfigService.createPatientNote(
          _buildNote(patientId: '5', note: 'Test note')),
      () => client,
    );
    final body = jsonDecode(captured.single.body) as Map<String, dynamic>;
    expect(body['note'], 'Test note');
    expect(body['patientId'], '5');
  });

  // --------------- updatePatientNote() — HTTP behaviour ---------------

  test('updatePatientNote() returns a PatientNote with correct id on HTTP 200',
      () async {
    final result = await http.runWithClient(
      () =>
          NotetakerConfigService.updatePatientNote(_buildNote(id: 'note-abc')),
      () => _mockJson(200, _noteJson(id: 'note-abc')),
    );
    expect(result, isA<PatientNote>());
    expect(result.id, 'note-abc');
  });

  test(
      'updatePatientNote() throws an Exception on non-2xx status or transport exception',
      () async {
    for (final (label, mock) in [
      ('HTTP 400', _mockRaw(400, 'Bad Request')),
      ('HTTP 404', _mockRaw(404, 'Not Found')),
      (
        'transport exception',
        _mockThrows(http.ClientException('lost connection'))
      ),
    ]) {
      await expectLater(
        () => http.runWithClient(
          () => NotetakerConfigService.updatePatientNote(
              _buildNote(id: 'note-abc')),
          () => mock,
        ),
        throwsA(isA<Exception>()),
        reason: label,
      );
    }
  });

  // --------------- updatePatientNote() — request construction ---------------

  test('updatePatientNote() sends a PUT request to the correct path', () async {
    _mockSecureStorage(
      jwtToken: _testJwtToken,
      tokenExpiry: _futureTokenExpiry(),
    );
    final (client, captured) = _capturingClient(200, _noteJson(id: 'note-abc'));
    await http.runWithClient(
      () => NotetakerConfigService.updatePatientNote(
          _buildNote(id: 'note-abc', patientId: '5')),
      () => client,
    );
    expect(captured.single.method, 'PUT');
    expect(
        captured.single.url.path, '/v1/api/patient-notetaker/5/notes/note-abc');
    expect(_headerValue(captured.single, 'Content-Type'), 'application/json');
    expect(_headerValue(captured.single, 'Accept'), '*/*');
    expect(_headerValue(captured.single, 'Authorization'),
        'Bearer $_testJwtToken');
  });

  test('updatePatientNote() request body includes note text and note id',
      () async {
    _mockSecureStorage(
      jwtToken: _testJwtToken,
      tokenExpiry: _futureTokenExpiry(),
    );
    final (client, captured) = _capturingClient(200, _noteJson(id: 'note-abc'));
    await http.runWithClient(
      () => NotetakerConfigService.updatePatientNote(
        _buildNote(id: 'note-abc', patientId: '5', note: 'Updated note'),
      ),
      () => client,
    );
    final body = jsonDecode(captured.single.body) as Map<String, dynamic>;
    expect(body['note'], 'Updated note');
    expect(body['id'], 'note-abc');
  });

  // --------------- deletePatientNote() — HTTP behaviour ---------------
  // deletePatientNote() swallows all errors — these tests document that design.

  test('deletePatientNote() completes normally on 200 and 204', () async {
    for (final statusCode in [200, 204]) {
      await expectLater(
        http.runWithClient(
          () => NotetakerConfigService.deletePatientNote('note-abc', 5),
          () => _mockRaw(statusCode, ''),
        ),
        completes,
        reason: 'status $statusCode',
      );
    }
  });

  test(
      'deletePatientNote() swallows errors — 404, 500, 403, and transport exception',
      () async {
    for (final (label, mock) in [
      ('HTTP 404', _mockRaw(404, 'Not Found')),
      ('HTTP 500', _mockRaw(500, 'Server Error')),
      ('HTTP 403', _mockRaw(403, 'Forbidden')),
      (
        'transport exception',
        _mockThrows(http.ClientException('socket error'))
      ),
    ]) {
      await expectLater(
        http.runWithClient(
          () => NotetakerConfigService.deletePatientNote('note-abc', 5),
          () => mock,
        ),
        completes,
        reason: label,
      );
    }
  });

  // --------------- deletePatientNote() — request construction ---------------

  test('deletePatientNote() sends a DELETE request to the correct path',
      () async {
    _mockSecureStorage(
      jwtToken: _testJwtToken,
      tokenExpiry: _futureTokenExpiry(),
    );
    final (client, captured) = _capturingClient(200, <String, dynamic>{});
    await http.runWithClient(
      () => NotetakerConfigService.deletePatientNote('note-abc', 5),
      () => client,
    );
    expect(captured.single.method, 'DELETE');
    expect(
        captured.single.url.path, '/v1/api/patient-notetaker/5/notes/note-abc');
    expect(_headerValue(captured.single, 'Content-Type'), 'application/json');
    expect(_headerValue(captured.single, 'Authorization'),
        'Bearer $_testJwtToken');
  });
}
