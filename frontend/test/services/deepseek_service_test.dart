// Tests for DeepseekService.
//
// Coverage strategy:
//   DeepseekService calls ApiService.getJwtToken() which reads from
//   AuthTokenManager (FlutterSecureStorage via MethodChannel).
//   The MethodChannel is stubbed with an in-memory map.
//   http.post (top-level) is intercepted via http.runWithClient + MockClient.
//
//   Branches tested:
//     extractAllergy — no JWT → throws 'No JWT available'.
//     extractAllergy — JWT + 200 response with nested 'data' map → returns parsed fields.
//     extractAllergy — JWT + 200 response flat map → returns parsed fields.
//     extractAllergy — JWT + 200 with non-map response → returns fallback map.
//     extractAllergy — JWT + non-2xx → throws 'AI analyze failed'.
//     extractAllergy — optional fields (allergen, severity, reaction) go into context.
//     extractSymptom — no JWT → throws 'No JWT available'.
//     extractSymptom — JWT + 200 with 'data' map → returns data map.
//     extractSymptom — JWT + 200 without 'data' → returns fallback map.
//     extractSymptom — JWT + non-2xx → throws 'AI symptom analyze failed'.

import 'dart:convert';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:care_connect_app/services/deepseek_service.dart';

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

// ─── JWT helper ───────────────────────────────────────────────────────────────

String _makeJwt({required int expSeconds}) {
  final header = base64Url.encode(utf8.encode('{"alg":"HS256","typ":"JWT"}'));
  final payload = base64Url.encode(
    utf8.encode(jsonEncode({'sub': '1', 'exp': expSeconds})),
  );
  return '$header.$payload.fakesig';
}

/// Pre-seed a valid (non-expired) JWT into the secure storage stub so that
/// ApiService.getJwtToken() returns a non-empty token string.
void _seedValidJwt() {
  final futureExp = DateTime.now().millisecondsSinceEpoch ~/ 1000 + 3600;
  final jwt = _makeJwt(expSeconds: futureExp);
  _secureStore['jwt_token'] = jwt;
  _secureStore['token_expiry'] = futureExp.toString();
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    _secureStore.clear();
    SharedPreferences.setMockInitialValues({});
    _setupSecureStorageStub();
  });

  tearDownAll(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(_secureStorageChannel, null);
  });

  // ─── extractAllergy ───────────────────────────────────────────────────────

  group('DeepseekService.extractAllergy', () {
    test('no JWT → throws No JWT available', () async {
      // Secure store is empty → ApiService.getJwtToken() returns ''.
      await expectLater(
        http.runWithClient(
          () => DeepseekService.extractAllergy(
            patientId: 1,
            transcript: 'penicillin allergy',
          ),
          () => MockClient((_) async => http.Response('{}', 200)),
        ),
        throwsA(isA<Exception>().having(
          (e) => e.toString(),
          'message',
          contains('No JWT available'),
        )),
      );
    });

    test('JWT + 200 with nested data map → returns allergen/reaction/severity', () async {
      _seedValidJwt();
      final responseBody = jsonEncode({
        'data': {'allergen': 'Penicillin', 'reaction': 'rash', 'severity': 'mild'},
      });
      final result = await http.runWithClient(
        () => DeepseekService.extractAllergy(
          patientId: 1,
          transcript: 'penicillin allergy rash',
        ),
        () => MockClient((_) async => http.Response(responseBody, 200)),
      );
      expect(result['allergen'], 'Penicillin');
      expect(result['reaction'], 'rash');
      expect(result['severity'], 'MILD');
    });

    test('JWT + 200 with flat map → returns parsed fields', () async {
      _seedValidJwt();
      final responseBody = jsonEncode({
        'allergen': 'Latex',
        'reaction': 'hives',
        'severity': 'moderate',
      });
      final result = await http.runWithClient(
        () => DeepseekService.extractAllergy(
          patientId: 1,
          transcript: 'latex hives',
        ),
        () => MockClient((_) async => http.Response(responseBody, 200)),
      );
      expect(result['allergen'], 'Latex');
      expect(result['severity'], 'MODERATE');
    });

    test('JWT + 200 with non-map body → returns fallback map', () async {
      _seedValidJwt();
      // When decoded body is not a Map, falls back to transcript in reaction.
      final responseBody = jsonEncode(['not', 'a', 'map']);
      final result = await http.runWithClient(
        () => DeepseekService.extractAllergy(
          patientId: 1,
          transcript: 'unknown allergy',
        ),
        () => MockClient((_) async => http.Response(responseBody, 200)),
      );
      expect(result['allergen'], '');
      expect(result['reaction'], 'unknown allergy');
      expect(result['severity'], '');
    });

    test('JWT + non-2xx → throws AI analyze failed', () async {
      _seedValidJwt();
      await expectLater(
        http.runWithClient(
          () => DeepseekService.extractAllergy(
            patientId: 1,
            transcript: 'test',
          ),
          () => MockClient((_) async => http.Response('bad request', 400)),
        ),
        throwsA(isA<Exception>().having(
          (e) => e.toString(),
          'message',
          contains('AI analyze failed'),
        )),
      );
    });

    test('optional context fields are sent when provided', () async {
      _seedValidJwt();
      Map<String, dynamic>? capturedBody;
      final responseBody = jsonEncode({'allergen': 'Pollen', 'reaction': 'sneezing', 'severity': 'mild'});
      await http.runWithClient(
        () => DeepseekService.extractAllergy(
          patientId: 1,
          transcript: 'pollen',
          allergen: 'Pollen',
          severity: 'MILD',
          reaction: 'sneezing',
        ),
        () => MockClient((req) async {
          capturedBody = jsonDecode(req.body) as Map<String, dynamic>;
          return http.Response(responseBody, 200);
        }),
      );
      final context = capturedBody?['context'] as Map<String, dynamic>?;
      expect(context?['allergen'], 'Pollen');
      expect(context?['severity'], 'MILD');
      expect(context?['reaction'], 'sneezing');
    });

    test('empty optional context fields are omitted', () async {
      _seedValidJwt();
      Map<String, dynamic>? capturedBody;
      final responseBody = jsonEncode({'allergen': '', 'reaction': '', 'severity': ''});
      await http.runWithClient(
        () => DeepseekService.extractAllergy(
          patientId: 1,
          transcript: 'test',
          allergen: '',
          severity: '  ',
        ),
        () => MockClient((req) async {
          capturedBody = jsonDecode(req.body) as Map<String, dynamic>;
          return http.Response(responseBody, 200);
        }),
      );
      final context = capturedBody?['context'] as Map<String, dynamic>?;
      // Empty/whitespace fields should not be included in context.
      expect(context?.containsKey('allergen'), isFalse);
      expect(context?.containsKey('severity'), isFalse);
    });
  });

  // ─── extractSymptom ───────────────────────────────────────────────────────

  group('DeepseekService.extractSymptom', () {
    test('no JWT → throws No JWT available', () async {
      await expectLater(
        http.runWithClient(
          () => DeepseekService.extractSymptom(
            patientId: 1,
            transcript: 'headache',
          ),
          () => MockClient((_) async => http.Response('{}', 200)),
        ),
        throwsA(isA<Exception>().having(
          (e) => e.toString(),
          'message',
          contains('No JWT available'),
        )),
      );
    });

    test('JWT + 200 with data map → returns data map', () async {
      _seedValidJwt();
      final responseBody = jsonEncode({
        'data': {
          'symptomKey': 'HEADACHE',
          'symptomValue': 'severe headache',
          'severity': 'HIGH',
          'notes': 'Started yesterday',
        },
      });
      final result = await http.runWithClient(
        () => DeepseekService.extractSymptom(
          patientId: 1,
          transcript: 'severe headache since yesterday',
        ),
        () => MockClient((_) async => http.Response(responseBody, 200)),
      );
      expect(result['symptomKey'], 'HEADACHE');
      expect(result['severity'], 'HIGH');
    });

    test('JWT + 200 without data key → returns fallback map with transcript', () async {
      _seedValidJwt();
      final responseBody = jsonEncode({'message': 'no data'});
      final result = await http.runWithClient(
        () => DeepseekService.extractSymptom(
          patientId: 1,
          transcript: 'dizziness',
        ),
        () => MockClient((_) async => http.Response(responseBody, 200)),
      );
      expect(result['symptomKey'], '');
      expect(result['notes'], 'dizziness');
    });

    test('JWT + non-2xx → throws AI symptom analyze failed', () async {
      _seedValidJwt();
      await expectLater(
        http.runWithClient(
          () => DeepseekService.extractSymptom(
            patientId: 1,
            transcript: 'nausea',
          ),
          () => MockClient((_) async => http.Response('server error', 500)),
        ),
        throwsA(isA<Exception>().having(
          (e) => e.toString(),
          'message',
          contains('AI symptom analyze failed'),
        )),
      );
    });

    test('optional hint fields sent when provided', () async {
      _seedValidJwt();
      Map<String, dynamic>? capturedBody;
      final responseBody = jsonEncode({
        'data': {'symptomKey': 'PAIN', 'symptomValue': 'back pain', 'severity': 'LOW', 'notes': ''},
      });
      await http.runWithClient(
        () => DeepseekService.extractSymptom(
          patientId: 1,
          transcript: 'back pain',
          symptomKeyHint: 'PAIN',
          severityHint: 'LOW',
          notesHint: 'chronic',
          context: {'source': 'patient-report'},
        ),
        () => MockClient((req) async {
          capturedBody = jsonDecode(req.body) as Map<String, dynamic>;
          return http.Response(responseBody, 200);
        }),
      );
      final context = capturedBody?['context'] as Map<String, dynamic>?;
      expect(context?['symptomKey'], 'PAIN');
      expect(context?['severity'], 'LOW');
      expect(context?['notes'], 'chronic');
      expect(context?['source'], 'patient-report');
    });
  });
}
