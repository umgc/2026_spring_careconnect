// Tests for AIConfigService and PatientAIConfigDTO.
//
// Coverage strategy:
//   1. PatientAIConfigDTO — fromJson(), toJson(), copyWith() are pure data
//      transformations with no external dependencies; tested directly.
//   2. AIConfigService.saveUserAIConfig() — uses ApiService.getAuthHeaders()
//      (which reads from flutter_secure_storage) and issues HTTP requests via
//      the top-level http.post.  The secure storage channel is stubbed, and
//      http.runWithClient() zones a MockClient over every call.
//   3. AIConfigService.getUserAIConfig() — additionally requires a BuildContext
//      with a UserProvider.  Each case is run as a widget test so that a real
//      context backed by MockUserProvider can be obtained.
//   4. Pure static list methods (getAvailableProviders, getPersonalityStyles,
//      getAvailableFeatures, getAvailableLanguages) are tested directly.

import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:provider/provider.dart';

import 'package:care_connect_app/providers/user_provider.dart';
import 'package:care_connect_app/services/ai_config_service.dart';

import '../mock_user_provider.dart';

// ─── Provider that always returns a null user (logged-out state) ─────────────

/// Minimal UserProvider that reports no logged-in user.  Used to exercise the
/// branch in getUserAIConfig() that returns null when userId is absent.
class _NoUserProvider extends MockUserProvider {
  @override
  UserSession? get user => null;

  @override
  bool get isLoggedIn => false;
}

// ─── MethodChannel used by flutter_secure_storage ────────────────────────────

// The flutter_secure_storage plugin registers under this channel name.
// All method calls are intercepted and return null so that getJwtToken()
// returns null → getAuthHeaders() omits the Authorization header.
const MethodChannel _secureStorageChannel =
    MethodChannel('plugins.it_nomads.com/flutter_secure_storage');

// ─── Factory / helper functions ──────────────────────────────────────────────

/// Returns a [MockClient] that replies with [statusCode] and a JSON-encoded
/// [body].
MockClient _mockJson(int statusCode, Object body) =>
    MockClient((_) async => http.Response(jsonEncode(body), statusCode));

/// Returns a [MockClient] that replies with [statusCode] and a plain [rawBody].
MockClient _mockRaw(int statusCode, String rawBody) =>
    MockClient((_) async => http.Response(rawBody, statusCode));

/// Returns a [MockClient] that throws [error] on every request.
MockClient _mockThrows(Object error) =>
    MockClient((_) async => throw error);

/// Returns a (client, capturedRequests) pair for asserting on outgoing requests.
(MockClient, List<http.Request>) _capturingClient(int statusCode, Object body) {
  final captured = <http.Request>[];
  final client = MockClient((req) async {
    captured.add(req);
    return http.Response(jsonEncode(body), statusCode);
  });
  return (client, captured);
}

// ─── Shared test data ────────────────────────────────────────────────────────

/// Minimal valid JSON map that satisfies PatientAIConfigDTO.fromJson().
Map<String, dynamic> get _minimalJson => {
      'patientId': 1,
    };

/// Complete JSON map with every field set.
Map<String, dynamic> get _fullJson => {
      'id': 42,
      'patientId': 7,
      'aiProvider': 'OPENAI',
      'preferences': {'responseLength': 'long'},
      'enabledFeatures': ['general_chat', 'medical_questions'],
      'maxTokensPerSession': 2000,
      'temperature': 0.9,
      'personalityStyle': 'FRIENDLY',
      'contextMemoryEnabled': false,
      'medicalContextEnabled': false,
      'language': 'es',
      'emergencyAlertsEnabled': false,
      'createdAt': '2024-01-15T10:30:00.000Z',
      'updatedAt': '2024-02-20T08:00:00.000Z',
      'isActive': false,
    };

// ─── Test entry point ─────────────────────────────────────────────────────────

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    // Intercept all flutter_secure_storage channel calls and return null.
    // This prevents MissingPluginException and simulates empty storage.
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(_secureStorageChannel, (_) async => null);
  });

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(_secureStorageChannel, null);
  });

  // ──────────────────────────────────────────────────────────────────────────
  // Group 1 — PatientAIConfigDTO.fromJson(): full JSON
  //
  // When every field is present, fromJson() must map each value exactly
  // to its corresponding DTO field.
  // ──────────────────────────────────────────────────────────────────────────
  group('PatientAIConfigDTO.fromJson() — full JSON', () {
    late PatientAIConfigDTO dto;

    setUp(() => dto = PatientAIConfigDTO.fromJson(_fullJson));

    test('parses id', () => expect(dto.id, 42));
    test('parses patientId', () => expect(dto.patientId, 7));
    test('parses aiProvider', () => expect(dto.aiProvider, 'OPENAI'));

    test('parses preferences map', () {
      // Preferences is a freeform map; the entire map must be preserved.
      expect(dto.preferences, {'responseLength': 'long'});
    });

    test('parses enabledFeatures list', () {
      expect(dto.enabledFeatures, ['general_chat', 'medical_questions']);
    });

    test('parses maxTokensPerSession', () => expect(dto.maxTokensPerSession, 2000));
    test('parses temperature', () => expect(dto.temperature, 0.9));
    test('parses personalityStyle', () => expect(dto.personalityStyle, 'FRIENDLY'));
    test('parses contextMemoryEnabled', () => expect(dto.contextMemoryEnabled, false));
    test('parses medicalContextEnabled', () => expect(dto.medicalContextEnabled, false));
    test('parses language', () => expect(dto.language, 'es'));
    test('parses emergencyAlertsEnabled', () => expect(dto.emergencyAlertsEnabled, false));
    test('parses isActive', () => expect(dto.isActive, false));

    test('parses createdAt as DateTime', () {
      // ISO-8601 string must become a non-null DateTime.
      expect(dto.createdAt, isA<DateTime>());
      expect(dto.createdAt!.year, 2024);
      expect(dto.createdAt!.month, 1);
    });

    test('parses updatedAt as DateTime', () {
      expect(dto.updatedAt, isA<DateTime>());
      expect(dto.updatedAt!.year, 2024);
    });
  });

  // ──────────────────────────────────────────────────────────────────────────
  // Group 2 — PatientAIConfigDTO.fromJson(): default values
  //
  // When optional fields are absent, fromJson() must fall back to sensible
  // defaults rather than throwing a null-dereference error.
  // ──────────────────────────────────────────────────────────────────────────
  group('PatientAIConfigDTO.fromJson() — default values', () {
    late PatientAIConfigDTO dto;

    setUp(() => dto = PatientAIConfigDTO.fromJson(_minimalJson));

    test('id defaults to null when absent', () => expect(dto.id, isNull));

    test('aiProvider defaults to DEFAULT', () {
      // The backend may not set a provider for newly-created configs.
      expect(dto.aiProvider, 'DEFAULT');
    });

    test('preferences defaults to empty map', () {
      expect(dto.preferences, isEmpty);
    });

    test('enabledFeatures defaults to empty list', () {
      expect(dto.enabledFeatures, isEmpty);
    });

    test('maxTokensPerSession defaults to 1000', () {
      expect(dto.maxTokensPerSession, 1000);
    });

    test('temperature defaults to 0.7', () {
      expect(dto.temperature, 0.7);
    });

    test('personalityStyle defaults to PROFESSIONAL', () {
      expect(dto.personalityStyle, 'PROFESSIONAL');
    });

    test('contextMemoryEnabled defaults to true', () {
      expect(dto.contextMemoryEnabled, true);
    });

    test('medicalContextEnabled defaults to true', () {
      expect(dto.medicalContextEnabled, true);
    });

    test('language defaults to en', () {
      expect(dto.language, 'en');
    });

    test('emergencyAlertsEnabled defaults to true', () {
      expect(dto.emergencyAlertsEnabled, true);
    });

    test('isActive defaults to true', () {
      expect(dto.isActive, true);
    });

    test('createdAt is null when absent', () {
      expect(dto.createdAt, isNull);
    });

    test('updatedAt is null when absent', () {
      expect(dto.updatedAt, isNull);
    });

    test('temperature is always a double even when JSON provides an int', () {
      // Dart JSON decoding may return int for values like 1 or 0; the DTO
      // must still expose a double.
      final dtoCasted =
          PatientAIConfigDTO.fromJson({'patientId': 1, 'temperature': 1});
      expect(dtoCasted.temperature, isA<double>());
    });
  });

  // ──────────────────────────────────────────────────────────────────────────
  // Group 3 — PatientAIConfigDTO.toJson()
  //
  // toJson() produces the map that gets POSTed to the backend.
  // The id field must be included only when non-null.
  // ──────────────────────────────────────────────────────────────────────────
  group('PatientAIConfigDTO.toJson()', () {
    test('includes id when it is set', () {
      final dto = PatientAIConfigDTO.fromJson(_fullJson);
      final json = dto.toJson();
      expect(json.containsKey('id'), isTrue);
      expect(json['id'], 42);
    });

    test('omits id when it is null', () {
      // Sending a null id can confuse some backends; the key must be absent.
      final dto = PatientAIConfigDTO.fromJson(_minimalJson);
      final json = dto.toJson();
      expect(json.containsKey('id'), isFalse);
    });

    test('includes patientId', () {
      final json = PatientAIConfigDTO.fromJson(_minimalJson).toJson();
      expect(json['patientId'], 1);
    });

    test('includes aiProvider', () {
      final json = PatientAIConfigDTO.fromJson(_fullJson).toJson();
      expect(json['aiProvider'], 'OPENAI');
    });

    test('includes preferences map', () {
      final json = PatientAIConfigDTO.fromJson(_fullJson).toJson();
      expect(json['preferences'], {'responseLength': 'long'});
    });

    test('includes enabledFeatures list', () {
      final json = PatientAIConfigDTO.fromJson(_fullJson).toJson();
      expect(json['enabledFeatures'], ['general_chat', 'medical_questions']);
    });

    test('includes maxTokensPerSession', () {
      final json = PatientAIConfigDTO.fromJson(_fullJson).toJson();
      expect(json['maxTokensPerSession'], 2000);
    });

    test('includes temperature', () {
      final json = PatientAIConfigDTO.fromJson(_fullJson).toJson();
      expect(json['temperature'], 0.9);
    });

    test('includes personalityStyle', () {
      final json = PatientAIConfigDTO.fromJson(_fullJson).toJson();
      expect(json['personalityStyle'], 'FRIENDLY');
    });

    test('includes contextMemoryEnabled', () {
      final json = PatientAIConfigDTO.fromJson(_fullJson).toJson();
      expect(json['contextMemoryEnabled'], false);
    });

    test('includes medicalContextEnabled', () {
      final json = PatientAIConfigDTO.fromJson(_fullJson).toJson();
      expect(json['medicalContextEnabled'], false);
    });

    test('includes language', () {
      final json = PatientAIConfigDTO.fromJson(_fullJson).toJson();
      expect(json['language'], 'es');
    });

    test('includes emergencyAlertsEnabled', () {
      final json = PatientAIConfigDTO.fromJson(_fullJson).toJson();
      expect(json['emergencyAlertsEnabled'], false);
    });

    test('includes isActive', () {
      final json = PatientAIConfigDTO.fromJson(_fullJson).toJson();
      expect(json['isActive'], false);
    });

    test('round-trips through fromJson → toJson for key fields', () {
      // Serialising and re-parsing must produce the same values.
      final original = PatientAIConfigDTO.fromJson(_fullJson);
      final roundTripped = PatientAIConfigDTO.fromJson(original.toJson());
      expect(roundTripped.patientId, original.patientId);
      expect(roundTripped.aiProvider, original.aiProvider);
      expect(roundTripped.temperature, original.temperature);
      expect(roundTripped.language, original.language);
    });
  });

  // ──────────────────────────────────────────────────────────────────────────
  // Group 4 — PatientAIConfigDTO.copyWith()
  //
  // copyWith() must return a new instance with only the specified fields
  // changed and all other fields inherited from the original.
  // ──────────────────────────────────────────────────────────────────────────
  group('PatientAIConfigDTO.copyWith()', () {
    late PatientAIConfigDTO original;

    setUp(() => original = PatientAIConfigDTO.fromJson(_fullJson));

    test('returns a new object (not the same reference)', () {
      final copy = original.copyWith();
      expect(identical(copy, original), isFalse);
    });

    test('keeps original values when no fields are overridden', () {
      final copy = original.copyWith();
      expect(copy.aiProvider, original.aiProvider);
      expect(copy.language, original.language);
      expect(copy.temperature, original.temperature);
    });

    test('overrides aiProvider', () {
      final copy = original.copyWith(aiProvider: 'DEEPSEEK');
      expect(copy.aiProvider, 'DEEPSEEK');
    });

    test('keeps other fields unchanged when overriding aiProvider', () {
      final copy = original.copyWith(aiProvider: 'DEEPSEEK');
      expect(copy.patientId, original.patientId);
      expect(copy.language, original.language);
    });

    test('overrides temperature', () {
      final copy = original.copyWith(temperature: 0.3);
      expect(copy.temperature, 0.3);
    });

    test('overrides language', () {
      final copy = original.copyWith(language: 'fr');
      expect(copy.language, 'fr');
    });

    test('overrides maxTokensPerSession', () {
      final copy = original.copyWith(maxTokensPerSession: 5000);
      expect(copy.maxTokensPerSession, 5000);
    });

    test('overrides contextMemoryEnabled to true', () {
      // original has contextMemoryEnabled == false; flip it to true.
      final copy = original.copyWith(contextMemoryEnabled: true);
      expect(copy.contextMemoryEnabled, true);
    });

    test('overrides isActive', () {
      final copy = original.copyWith(isActive: true);
      expect(copy.isActive, true);
    });

    test('overrides preferences map', () {
      final newPrefs = {'technicalLevel': 'expert'};
      final copy = original.copyWith(preferences: newPrefs);
      expect(copy.preferences, newPrefs);
    });

    test('overrides enabledFeatures list', () {
      final features = ['symptom_analysis'];
      final copy = original.copyWith(enabledFeatures: features);
      expect(copy.enabledFeatures, features);
    });
  });

  // ──────────────────────────────────────────────────────────────────────────
  // Group 5 — AIConfigService.getAvailableProviders()
  //
  // This is a pure static helper; no HTTP or storage interactions occur.
  // ──────────────────────────────────────────────────────────────────────────
  group('AIConfigService.getAvailableProviders()', () {
    late List<Map<String, String>> providers;

    setUp(() => providers = AIConfigService.getAvailableProviders());

    test('returns a non-empty list', () {
      expect(providers, isNotEmpty);
    });

    test('returns exactly 4 providers', () {
      // The UI renders one entry per provider, so the count must be stable.
      expect(providers, hasLength(4));
    });

    test('each entry has a value key', () {
      for (final p in providers) {
        expect(p.containsKey('value'), isTrue,
            reason: 'Entry $p is missing the "value" key');
      }
    });

    test('each entry has a label key', () {
      for (final p in providers) {
        expect(p.containsKey('label'), isTrue,
            reason: 'Entry $p is missing the "label" key');
      }
    });

    test('contains DEFAULT provider', () {
      expect(providers.any((p) => p['value'] == 'DEFAULT'), isTrue);
    });

    test('contains DEEPSEEK provider', () {
      expect(providers.any((p) => p['value'] == 'DEEPSEEK'), isTrue);
    });

    test('contains OPENAI provider', () {
      expect(providers.any((p) => p['value'] == 'OPENAI'), isTrue);
    });

    test('contains MEDICAL_SPECIALIST provider', () {
      expect(providers.any((p) => p['value'] == 'MEDICAL_SPECIALIST'), isTrue);
    });
  });

  // ──────────────────────────────────────────────────────────────────────────
  // Group 6 — AIConfigService.getPersonalityStyles()
  // ──────────────────────────────────────────────────────────────────────────
  group('AIConfigService.getPersonalityStyles()', () {
    late List<Map<String, String>> styles;

    setUp(() => styles = AIConfigService.getPersonalityStyles());

    test('returns exactly 5 personality styles', () {
      expect(styles, hasLength(5));
    });

    test('each entry has value and label keys', () {
      for (final s in styles) {
        expect(s.containsKey('value'), isTrue);
        expect(s.containsKey('label'), isTrue);
      }
    });

    test('contains PROFESSIONAL style', () {
      expect(styles.any((s) => s['value'] == 'PROFESSIONAL'), isTrue);
    });

    test('contains FRIENDLY style', () {
      expect(styles.any((s) => s['value'] == 'FRIENDLY'), isTrue);
    });

    test('contains EMPATHETIC style', () {
      expect(styles.any((s) => s['value'] == 'EMPATHETIC'), isTrue);
    });

    test('contains DIRECT style', () {
      expect(styles.any((s) => s['value'] == 'DIRECT'), isTrue);
    });

    test('contains EDUCATIONAL style', () {
      expect(styles.any((s) => s['value'] == 'EDUCATIONAL'), isTrue);
    });
  });

  // ──────────────────────────────────────────────────────────────────────────
  // Group 7 — AIConfigService.getAvailableFeatures()
  // ──────────────────────────────────────────────────────────────────────────
  group('AIConfigService.getAvailableFeatures()', () {
    late List<Map<String, dynamic>> features;

    setUp(() => features = AIConfigService.getAvailableFeatures());

    test('returns exactly 9 features', () {
      expect(features, hasLength(9));
    });

    test('each entry has value, label, description, and icon keys', () {
      for (final f in features) {
        expect(f.containsKey('value'), isTrue);
        expect(f.containsKey('label'), isTrue);
        expect(f.containsKey('description'), isTrue);
        expect(f.containsKey('icon'), isTrue);
      }
    });

    test('contains general_chat feature', () {
      expect(features.any((f) => f['value'] == 'general_chat'), isTrue);
    });

    test('contains medical_questions feature', () {
      expect(features.any((f) => f['value'] == 'medical_questions'), isTrue);
    });

    test('contains symptom_analysis feature', () {
      expect(features.any((f) => f['value'] == 'symptom_analysis'), isTrue);
    });

    test('contains emergency_assistance feature', () {
      expect(features.any((f) => f['value'] == 'emergency_assistance'), isTrue);
    });
  });

  // ──────────────────────────────────────────────────────────────────────────
  // Group 8 — AIConfigService.getAvailableLanguages()
  // ──────────────────────────────────────────────────────────────────────────
  group('AIConfigService.getAvailableLanguages()', () {
    late List<Map<String, String>> languages;

    setUp(() => languages = AIConfigService.getAvailableLanguages());

    test('returns exactly 10 languages', () {
      expect(languages, hasLength(10));
    });

    test('each entry has value and label keys', () {
      for (final l in languages) {
        expect(l.containsKey('value'), isTrue);
        expect(l.containsKey('label'), isTrue);
      }
    });

    test('contains English (en)', () {
      expect(languages.any((l) => l['value'] == 'en'), isTrue);
    });

    test('contains Spanish (es)', () {
      expect(languages.any((l) => l['value'] == 'es'), isTrue);
    });

    test('contains Chinese (zh)', () {
      expect(languages.any((l) => l['value'] == 'zh'), isTrue);
    });
  });

  // ──────────────────────────────────────────────────────────────────────────
  // Group 9 — AIConfigService.saveUserAIConfig(): HTTP behaviour
  //
  // saveUserAIConfig() issues a POST to /v1/api/ai-chat/config.
  // It must return a populated DTO on 200/201 and null on failure.
  // ──────────────────────────────────────────────────────────────────────────
  group('AIConfigService.saveUserAIConfig() — HTTP behaviour', () {
    // A minimal valid backend response that PatientAIConfigDTO.fromJson() can
    // parse without error.
    Map<String, dynamic> savedJson() => {
          'id': 1,
          'patientId': 5,
          'aiProvider': 'OPENAI',
          'preferences': {},
          'enabledFeatures': [],
          'maxTokensPerSession': 1000,
          'temperature': 0.7,
          'personalityStyle': 'PROFESSIONAL',
          'contextMemoryEnabled': true,
          'medicalContextEnabled': true,
          'language': 'en',
          'emergencyAlertsEnabled': true,
          'isActive': true,
        };

    // A minimal DTO to submit.
    PatientAIConfigDTO makeConfig() => PatientAIConfigDTO(
          patientId: 5,
          aiProvider: 'OPENAI',
          preferences: {},
          enabledFeatures: [],
          maxTokensPerSession: 1000,
          temperature: 0.7,
          personalityStyle: 'PROFESSIONAL',
          contextMemoryEnabled: true,
          medicalContextEnabled: true,
          language: 'en',
          emergencyAlertsEnabled: true,
        );

    test('returns a PatientAIConfigDTO on HTTP 200', () async {
      final result = await http.runWithClient(
        () => AIConfigService.saveUserAIConfig(makeConfig(), userId: 10),
        () => _mockJson(200, savedJson()),
      );
      expect(result, isA<PatientAIConfigDTO>());
    });

    test('returns a PatientAIConfigDTO on HTTP 201 (created)', () async {
      // Some REST endpoints return 201 for a new resource.
      final result = await http.runWithClient(
        () => AIConfigService.saveUserAIConfig(makeConfig(), userId: 10),
        () => _mockJson(201, savedJson()),
      );
      expect(result, isA<PatientAIConfigDTO>());
    });

    test('returned DTO has the correct patientId', () async {
      final result = await http.runWithClient(
        () => AIConfigService.saveUserAIConfig(makeConfig(), userId: 10),
        () => _mockJson(200, savedJson()),
      );
      expect(result!.patientId, 5);
    });

    test('returns null on HTTP 400', () async {
      final result = await http.runWithClient(
        () => AIConfigService.saveUserAIConfig(makeConfig(), userId: 10),
        () => _mockRaw(400, 'Bad Request'),
      );
      expect(result, isNull);
    });

    test('returns null on HTTP 500', () async {
      final result = await http.runWithClient(
        () => AIConfigService.saveUserAIConfig(makeConfig(), userId: 10),
        () => _mockRaw(500, 'Internal Server Error'),
      );
      expect(result, isNull);
    });

    test('returns null on transport exception', () async {
      final result = await http.runWithClient(
        () => AIConfigService.saveUserAIConfig(makeConfig(), userId: 10),
        () => _mockThrows(http.ClientException('Connection refused')),
      );
      expect(result, isNull);
    });
  });

  // ──────────────────────────────────────────────────────────────────────────
  // Group 10 — AIConfigService.saveUserAIConfig(): request construction
  //
  // The request body must include all fields required by the backend's
  // UserAIConfigDTO, with fallback defaults for absent preference fields.
  // ──────────────────────────────────────────────────────────────────────────
  group('AIConfigService.saveUserAIConfig() — request body', () {
    Future<Map<String, dynamic>> _captureBody({
      int userId = 10,
      Map<String, dynamic> preferences = const {},
    }) async {
      final config = PatientAIConfigDTO(
        patientId: 5,
        aiProvider: 'OPENAI',
        preferences: preferences,
        enabledFeatures: [],
        maxTokensPerSession: 2048,
        temperature: 0.5,
        personalityStyle: 'FRIENDLY',
        contextMemoryEnabled: false,
        medicalContextEnabled: false,
        language: 'fr',
        emergencyAlertsEnabled: false,
      );

      final (client, requests) = _capturingClient(200, {
        'patientId': 5,
        'aiProvider': 'OPENAI',
        'preferences': {},
        'enabledFeatures': [],
        'maxTokensPerSession': 2048,
        'temperature': 0.5,
        'personalityStyle': 'FRIENDLY',
        'contextMemoryEnabled': false,
        'medicalContextEnabled': false,
        'language': 'fr',
        'emergencyAlertsEnabled': false,
        'isActive': true,
      });
      await http.runWithClient(
        () => AIConfigService.saveUserAIConfig(config, userId: userId),
        () => client,
      );
      return jsonDecode(requests.single.body) as Map<String, dynamic>;
    }

    test('uses POST method', () async {
      final config = PatientAIConfigDTO(
        patientId: 5,
        aiProvider: 'OPENAI',
        preferences: const {},
        enabledFeatures: const [],
        maxTokensPerSession: 1000,
        temperature: 0.7,
        personalityStyle: 'PROFESSIONAL',
        contextMemoryEnabled: true,
        medicalContextEnabled: true,
        language: 'en',
        emergencyAlertsEnabled: true,
      );
      final (client, requests) = _capturingClient(200, {
        'patientId': 5,
        'aiProvider': 'OPENAI',
        'preferences': {},
        'enabledFeatures': [],
        'maxTokensPerSession': 1000,
        'temperature': 0.7,
        'personalityStyle': 'PROFESSIONAL',
        'contextMemoryEnabled': true,
        'medicalContextEnabled': true,
        'language': 'en',
        'emergencyAlertsEnabled': true,
        'isActive': true,
      });
      await http.runWithClient(
        () => AIConfigService.saveUserAIConfig(config, userId: 10),
        () => client,
      );
      expect(requests.single.method, 'POST');
    });

    test('hits the /v1/api/ai-chat/config endpoint', () async {
      final config = PatientAIConfigDTO(
        patientId: 5,
        aiProvider: 'OPENAI',
        preferences: const {},
        enabledFeatures: const [],
        maxTokensPerSession: 1000,
        temperature: 0.7,
        personalityStyle: 'PROFESSIONAL',
        contextMemoryEnabled: true,
        medicalContextEnabled: true,
        language: 'en',
        emergencyAlertsEnabled: true,
      );
      final (client, requests) = _capturingClient(200, {
        'patientId': 5,
        'aiProvider': 'OPENAI',
        'preferences': {},
        'enabledFeatures': [],
        'maxTokensPerSession': 1000,
        'temperature': 0.7,
        'personalityStyle': 'PROFESSIONAL',
        'contextMemoryEnabled': true,
        'medicalContextEnabled': true,
        'language': 'en',
        'emergencyAlertsEnabled': true,
        'isActive': true,
      });
      await http.runWithClient(
        () => AIConfigService.saveUserAIConfig(config, userId: 10),
        () => client,
      );
      expect(requests.single.url.path, '/v1/api/ai-chat/config');
    });

    test('request body includes userId', () async {
      final body = await _captureBody(userId: 99);
      expect(body['userId'], 99);
    });

    test('request body includes patientId', () async {
      final body = await _captureBody();
      expect(body['patientId'], 5);
    });

    test('request body includes preferredAiProvider', () async {
      final body = await _captureBody();
      expect(body['preferredAiProvider'], 'OPENAI');
    });

    test('request body includes maxTokens', () async {
      final body = await _captureBody();
      expect(body['maxTokens'], 2048);
    });

    test('request body includes temperature', () async {
      final body = await _captureBody();
      expect(body['temperature'], 0.5);
    });

    test('request body includes isActive', () async {
      final body = await _captureBody();
      expect(body.containsKey('isActive'), isTrue);
    });

    test('openaiModel defaults to gpt-4 when absent from preferences',
        () async {
      // Backend requires this field; service must supply a default.
      final body = await _captureBody(preferences: {});
      expect(body['openaiModel'], 'gpt-4');
    });

    test('openaiModel uses value from preferences when present', () async {
      final body = await _captureBody(
          preferences: {'openaiModel': 'gpt-3.5-turbo'});
      expect(body['openaiModel'], 'gpt-3.5-turbo');
    });

    test('deepseekModel defaults to deepseek-chat when absent', () async {
      final body = await _captureBody(preferences: {});
      expect(body['deepseekModel'], 'deepseek-chat');
    });

    test('conversationHistoryLimit defaults to 20 when absent', () async {
      final body = await _captureBody(preferences: {});
      expect(body['conversationHistoryLimit'], 20);
    });

    test('includeVitalsByDefault defaults to true when absent', () async {
      final body = await _captureBody(preferences: {});
      expect(body['includeVitalsByDefault'], true);
    });

    test('includeMedicationsByDefault defaults to true when absent', () async {
      final body = await _captureBody(preferences: {});
      expect(body['includeMedicationsByDefault'], true);
    });

    test('includeNotesByDefault defaults to true when absent', () async {
      final body = await _captureBody(preferences: {});
      expect(body['includeNotesByDefault'], true);
    });

    test('includeAllergiesByDefault defaults to true when absent', () async {
      final body = await _captureBody(preferences: {});
      expect(body['includeAllergiesByDefault'], true);
    });

    test('systemPrompt defaults to non-empty string when absent', () async {
      final body = await _captureBody(preferences: {});
      expect(body['systemPrompt'], isA<String>());
      expect((body['systemPrompt'] as String), isNotEmpty);
    });

    test('systemPrompt uses value from preferences when present', () async {
      const custom = 'You are a custom assistant.';
      final body =
          await _captureBody(preferences: {'systemPrompt': custom});
      expect(body['systemPrompt'], custom);
    });
  });

  // ──────────────────────────────────────────────────────────────────────────
  // Group 11 — AIConfigService.getUserAIConfig(): HTTP behaviour
  //
  // getUserAIConfig() requires a BuildContext with a UserProvider.
  // Each test is a widget test that pumps a ChangeNotifierProvider-backed tree
  // to obtain a real context.
  // ──────────────────────────────────────────────────────────────────────────
  group('AIConfigService.getUserAIConfig() — HTTP behaviour', () {
    // Minimal valid backend response for a found config.
    final Map<String, dynamic> _configJson = {
      'id': 10,
      'patientId': 3,
      'aiProvider': 'DEFAULT',
      'preferences': {},
      'enabledFeatures': [],
      'maxTokensPerSession': 1000,
      'temperature': 0.7,
      'personalityStyle': 'PROFESSIONAL',
      'contextMemoryEnabled': true,
      'medicalContextEnabled': true,
      'language': 'en',
      'emergencyAlertsEnabled': true,
      'isActive': true,
    };

    /// Pumps a minimal widget tree backed by [provider] and returns the
    /// BuildContext of the inner [SizedBox].
    Future<BuildContext> _pumpContext(
        WidgetTester tester, MockUserProvider provider) async {
      await tester.pumpWidget(
        ChangeNotifierProvider<UserProvider>.value(
          value: provider,
          child: const MaterialApp(home: SizedBox()),
        ),
      );
      return tester.element(find.byType(SizedBox));
    }

    testWidgets('returns a PatientAIConfigDTO on HTTP 200', (tester) async {
      final ctx = await _pumpContext(tester, MockUserProvider());
      final result = await http.runWithClient(
        () => AIConfigService.getUserAIConfig(ctx),
        () => _mockJson(200, _configJson),
      );
      expect(result, isA<PatientAIConfigDTO>());
    });

    testWidgets('returned DTO has correct patientId from server response',
        (tester) async {
      final ctx = await _pumpContext(tester, MockUserProvider());
      final result = await http.runWithClient(
        () => AIConfigService.getUserAIConfig(ctx),
        () => _mockJson(200, _configJson),
      );
      expect(result!.patientId, 3);
    });

    testWidgets('returns a default PatientAIConfigDTO on HTTP 404',
        (tester) async {
      // A 404 means no config exists yet; the service must return defaults
      // rather than null, so the UI can display sensible starting values.
      final ctx = await _pumpContext(tester, MockUserProvider());
      final result = await http.runWithClient(
        () => AIConfigService.getUserAIConfig(ctx),
        () => _mockRaw(404, 'Not Found'),
      );
      expect(result, isA<PatientAIConfigDTO>());
    });

    testWidgets('default config on 404 uses DEFAULT as aiProvider',
        (tester) async {
      final ctx = await _pumpContext(tester, MockUserProvider());
      final result = await http.runWithClient(
        () => AIConfigService.getUserAIConfig(ctx),
        () => _mockRaw(404, 'Not Found'),
      );
      expect(result!.aiProvider, 'DEFAULT');
    });

    testWidgets('default config on 404 has expected default enabledFeatures',
        (tester) async {
      // The default config should include the standard set of features.
      final ctx = await _pumpContext(tester, MockUserProvider());
      final result = await http.runWithClient(
        () => AIConfigService.getUserAIConfig(ctx),
        () => _mockRaw(404, 'Not Found'),
      );
      expect(result!.enabledFeatures, contains('general_chat'));
    });

    testWidgets('returns null on HTTP 500', (tester) async {
      final ctx = await _pumpContext(tester, MockUserProvider());
      final result = await http.runWithClient(
        () => AIConfigService.getUserAIConfig(ctx),
        () => _mockRaw(500, 'Server Error'),
      );
      expect(result, isNull);
    });

    testWidgets('returns null on transport exception', (tester) async {
      final ctx = await _pumpContext(tester, MockUserProvider());
      final result = await http.runWithClient(
        () => AIConfigService.getUserAIConfig(ctx),
        () => _mockThrows(http.ClientException('Network error')),
      );
      expect(result, isNull);
    });

    testWidgets('returns null when no user is logged in', (tester) async {
      // _NoUserProvider.user returns null, simulating a logged-out state.
      final noUserProvider = _NoUserProvider();
      final ctx = await _pumpContext(tester, noUserProvider);
      final result = await http.runWithClient(
        () => AIConfigService.getUserAIConfig(ctx),
        () => _mockJson(200, _configJson),
      );
      expect(result, isNull);
    });

    testWidgets('sends userId as query parameter', (tester) async {
      // The query must include the logged-in userId so the backend filters
      // the correct config record.
      final provider = MockUserProvider(mockUser: MockUser(id: 77));
      final ctx = await _pumpContext(tester, provider);

      final (client, requests) = _capturingClient(200, _configJson);
      await http.runWithClient(
        () => AIConfigService.getUserAIConfig(ctx),
        () => client,
      );
      expect(requests.single.url.queryParameters['userId'], '77');
    });

    testWidgets('uses GET method', (tester) async {
      final ctx = await _pumpContext(tester, MockUserProvider());
      final (client, requests) = _capturingClient(200, _configJson);
      await http.runWithClient(
        () => AIConfigService.getUserAIConfig(ctx),
        () => client,
      );
      expect(requests.single.method, 'GET');
    });

    testWidgets('hits the /v1/api/ai-chat/config endpoint', (tester) async {
      final ctx = await _pumpContext(tester, MockUserProvider());
      final (client, requests) = _capturingClient(200, _configJson);
      await http.runWithClient(
        () => AIConfigService.getUserAIConfig(ctx),
        () => client,
      );
      expect(requests.single.url.path, '/v1/api/ai-chat/config');
    });
  });
}
