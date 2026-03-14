// Tests for HealthApi and ApiException.
//
// NOTE: This is a Dart/Flutter project. The equivalent of JUnit here is the
// `flutter_test` package. These tests verify HTTP behaviour, response parsing,
// error handling, and request construction — the same concerns a JUnit suite
// would cover for a Java HTTP client.
//
// HealthApi uses the top-level http.get/post/put/patch/delete functions.
// We intercept those calls with http.runWithClient(), which zones a MockClient
// over the global HTTP client for the duration of each call. No real network
// traffic occurs.

import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

import 'package:care_connect_app/features/health/Shared/Data/health_api.dart';

// ─── test constants ───────────────────────────────────────────────────────────

const _jwt = 'test.jwt.token';
const _patientId = 42;
const _allergyId = 7;

// ─── factory helpers ──────────────────────────────────────────────────────────

/// Returns a [HealthApi] instance wired with the test JWT.
HealthApi _api() => HealthApi(_jwt);

/// Returns a [MockClient] that replies to every request with [statusCode] and
/// a JSON-encoded [body].
MockClient _mockJson(int statusCode, Object body) =>
    MockClient((_) async => http.Response(jsonEncode(body), statusCode));

/// Returns a [MockClient] that replies with a raw (non-encoded) [rawBody].
/// Use this for empty bodies or plain-text error payloads.
MockClient _mockRaw(int statusCode, String rawBody) =>
    MockClient((_) async => http.Response(rawBody, statusCode));

/// Returns a [MockClient] that captures every [http.Request] it receives into
/// the second element of the returned record, then responds with [statusCode]
/// and JSON-encoded [body].
///
/// Dart 3 record syntax:
/// ```dart
/// final (client, requests) = _capturingClient(200, {'id': 1});
/// await http.runWithClient(() => api.someMethod(), () => client);
/// expect(requests.single.method, 'GET');
/// ```
(MockClient, List<http.Request>) _capturingClient(int statusCode, Object body) {
  final captured = <http.Request>[];
  final client = MockClient((req) async {
    captured.add(req);
    return http.Response(jsonEncode(body), statusCode);
  });
  return (client, captured);
}

// ─── test entry point ─────────────────────────────────────────────────────────

void main() {
  // ──────────────────────────────────────────────────────────────────────────
  // Group 1 — ApiException
  //
  // ApiException is a thin wrapper around statusCode + message. These tests
  // confirm the constructor stores both fields and that toString() formats them
  // correctly, since callers inspect both the code (for branching) and the
  // message (for display / logging).
  // ──────────────────────────────────────────────────────────────────────────
  group('ApiException', () {
    test('constructor stores statusCode', () {
      // statusCode must be accessible so callers can switch on specific codes.
      expect(ApiException(404, 'Not Found').statusCode, 404);
    });

    test('constructor stores message', () {
      // message surfaces in logs and user-facing error dialogs.
      expect(ApiException(404, 'Not Found').message, 'Not Found');
    });

    test('toString() returns expected format "ApiException(<code>): <msg>"', () {
      // Exact format matters for log aggregation tools that parse the prefix.
      expect(ApiException(403, 'Forbidden').toString(), 'ApiException(403): Forbidden');
    });

    test('toString() includes statusCode', () {
      expect(ApiException(500, 'err').toString(), contains('500'));
    });

    test('toString() includes message', () {
      expect(ApiException(500, 'Server Error').toString(), contains('Server Error'));
    });

    test('is an Exception', () {
      // Callers that catch the broad Exception type (not ApiException) must
      // also catch this.
      expect(ApiException(200, 'ok'), isA<Exception>());
    });
  });

  // ──────────────────────────────────────────────────────────────────────────
  // Group 2 — HealthApi construction
  // ──────────────────────────────────────────────────────────────────────────
  group('HealthApi construction', () {
    test('stores the provided JWT', () {
      // The JWT is embedded in every Authorization header; it must be preserved
      // exactly as supplied.
      expect(HealthApi('my.jwt.token').jwt, 'my.jwt.token');
    });

    test('different tokens produce different instances', () {
      // Two instances created with different JWTs must not share state.
      final a = HealthApi('token-a');
      final b = HealthApi('token-b');
      expect(a.jwt, isNot(b.jwt));
    });
  });

  // ──────────────────────────────────────────────────────────────────────────
  // Group 3 — Request headers
  //
  // _headers is private, so we verify its contents by capturing the outgoing
  // http.Request inside a MockClient. Every outgoing request must carry the
  // correct Authorization, Content-Type, and Accept headers.
  // ──────────────────────────────────────────────────────────────────────────
  group('Request headers', () {
    // Use getMyPatientId() as a representative GET call.
    Future<List<http.Request>> capturedHeaders() async {
      final (client, requests) = _capturingClient(200, {'id': _patientId});
      await http.runWithClient(() => _api().getMyPatientId(), () => client);
      return requests;
    }

    test('Authorization header is "Bearer <jwt>"', () async {
      final reqs = await capturedHeaders();
      expect(reqs.single.headers['Authorization'], 'Bearer $_jwt');
    });

    test('Content-Type header is "application/json"', () async {
      final reqs = await capturedHeaders();
      expect(reqs.single.headers['Content-Type'], 'application/json');
    });

    test('Accept header is "application/json"', () async {
      final reqs = await capturedHeaders();
      expect(reqs.single.headers['Accept'], 'application/json');
    });
  });

  // ──────────────────────────────────────────────────────────────────────────
  // Group 4 — URL construction (_uri — tested indirectly)
  //
  // _uri strips trailing slashes from the base URL and ensures the path starts
  // with "/". We verify the resulting URL paths through captured requests.
  // ──────────────────────────────────────────────────────────────────────────
  group('URL construction', () {
    test('getMyPatientId() hits /v1/api/patients/me', () async {
      final (client, requests) = _capturingClient(200, {'id': _patientId});
      await http.runWithClient(() => _api().getMyPatientId(), () => client);
      expect(requests.single.url.path, '/v1/api/patients/me');
    });

    test('getAllergiesForPatient() embeds patientId in the path', () async {
      final (client, requests) = _capturingClient(200, {'data': []});
      await http.runWithClient(
        () => _api().getAllergiesForPatient(_patientId),
        () => client,
      );
      expect(requests.single.url.path, '/v1/api/allergies/patient/$_patientId');
    });

    test('getActiveAllergiesForPatient() appends /active to the path', () async {
      final (client, requests) = _capturingClient(200, {'data': []});
      await http.runWithClient(
        () => _api().getActiveAllergiesForPatient(_patientId),
        () => client,
      );
      expect(
        requests.single.url.path,
        '/v1/api/allergies/patient/$_patientId/active',
      );
    });
  });

  // ──────────────────────────────────────────────────────────────────────────
  // Group 5 — getMyPatientId()
  //
  // The backend may return the patient id at the top level {"id":…} or inside
  // a data envelope {"data":{"id":…}}. Both shapes must be handled, and
  // non-200 or missing-id responses must throw ApiException.
  // ──────────────────────────────────────────────────────────────────────────
  group('getMyPatientId()', () {
    test('returns id from top-level "id" field', () async {
      // Primary response shape: { "id": 42 }
      final id = await http.runWithClient(
        () => _api().getMyPatientId(),
        () => _mockJson(200, {'id': _patientId}),
      );
      expect(id, _patientId);
    });

    test('returns id from nested data.id field', () async {
      // Some endpoints wrap the entity: { "data": { "id": 42 } }
      final id = await http.runWithClient(
        () => _api().getMyPatientId(),
        () => _mockJson(200, {'data': {'id': _patientId}}),
      );
      expect(id, _patientId);
    });

    test('coerces a numeric double id to int', () async {
      // JSON integers can be decoded as num; toInt() must handle this.
      final id = await http.runWithClient(
        () => _api().getMyPatientId(),
        () => _mockRaw(200, '{"id": 42.0}'),
      );
      expect(id, 42);
    });

    test('throws ApiException on non-200 status', () async {
      await expectLater(
        http.runWithClient(
          () => _api().getMyPatientId(),
          () => _mockRaw(401, 'Unauthorized'),
        ),
        throwsA(
          isA<ApiException>().having((e) => e.statusCode, 'statusCode', 401),
        ),
      );
    });

    test('throws ApiException(500) when id is absent from response', () async {
      // A 200 body that contains no recognisable id field is a contract
      // violation — the method must surface this as a server error.
      await expectLater(
        http.runWithClient(
          () => _api().getMyPatientId(),
          () => _mockJson(200, {'someOtherField': 'value'}),
        ),
        throwsA(
          isA<ApiException>().having((e) => e.statusCode, 'statusCode', 500),
        ),
      );
    });

    test('uses GET method', () async {
      final (client, requests) = _capturingClient(200, {'id': _patientId});
      await http.runWithClient(() => _api().getMyPatientId(), () => client);
      expect(requests.single.method, 'GET');
    });
  });

  // ──────────────────────────────────────────────────────────────────────────
  // Group 6 — getAllergiesForPatient()
  //
  // The response is expected to carry a `data` array. Missing / malformed
  // data fields must return an empty list rather than crashing.
  // ──────────────────────────────────────────────────────────────────────────
  group('getAllergiesForPatient()', () {
    final twoAllergies = [
      {'id': 1, 'allergen': 'Penicillin'},
      {'id': 2, 'allergen': 'Peanuts'},
    ];

    test('returns list from data envelope', () async {
      final res = await http.runWithClient(
        () => _api().getAllergiesForPatient(_patientId),
        () => _mockJson(200, {'data': twoAllergies}),
      );
      expect(res, hasLength(2));
      expect(res.first['allergen'], 'Penicillin');
    });

    test('returns empty list when data field is absent', () async {
      // Backend might return an empty response body on edge cases.
      final res = await http.runWithClient(
        () => _api().getAllergiesForPatient(_patientId),
        () => _mockJson(200, {'message': 'ok'}),
      );
      expect(res, isEmpty);
    });

    test('returns empty list when data is not a List', () async {
      // Defensive: malformed data field must not cause a cast exception.
      final res = await http.runWithClient(
        () => _api().getAllergiesForPatient(_patientId),
        () => _mockJson(200, {'data': 'not-a-list'}),
      );
      expect(res, isEmpty);
    });

    test('returns empty list for an empty data array', () async {
      final res = await http.runWithClient(
        () => _api().getAllergiesForPatient(_patientId),
        () => _mockJson(200, {'data': []}),
      );
      expect(res, isEmpty);
    });

    test('throws ApiException on non-200 status', () async {
      await expectLater(
        http.runWithClient(
          () => _api().getAllergiesForPatient(_patientId),
          () => _mockRaw(403, 'Forbidden'),
        ),
        throwsA(
          isA<ApiException>().having((e) => e.statusCode, 'statusCode', 403),
        ),
      );
    });

    test('uses GET method', () async {
      final (client, requests) = _capturingClient(200, {'data': []});
      await http.runWithClient(
        () => _api().getAllergiesForPatient(_patientId),
        () => client,
      );
      expect(requests.single.method, 'GET');
    });
  });

  // ──────────────────────────────────────────────────────────────────────────
  // Group 7 — getActiveAllergiesForPatient()
  //
  // Mirrors getAllergiesForPatient() but hits the /active sub-path.
  // ──────────────────────────────────────────────────────────────────────────
  group('getActiveAllergiesForPatient()', () {
    test('returns only active allergies from data envelope', () async {
      final active = [
        {'id': 3, 'allergen': 'Dust', 'isActive': true}
      ];
      final res = await http.runWithClient(
        () => _api().getActiveAllergiesForPatient(_patientId),
        () => _mockJson(200, {'data': active}),
      );
      expect(res, hasLength(1));
      expect(res.first['allergen'], 'Dust');
    });

    test('returns empty list when no active allergies', () async {
      final res = await http.runWithClient(
        () => _api().getActiveAllergiesForPatient(_patientId),
        () => _mockJson(200, {'data': []}),
      );
      expect(res, isEmpty);
    });

    test('returns empty list when data field is absent', () async {
      final res = await http.runWithClient(
        () => _api().getActiveAllergiesForPatient(_patientId),
        () => _mockJson(200, {}),
      );
      expect(res, isEmpty);
    });

    test('throws ApiException on non-200 status', () async {
      await expectLater(
        http.runWithClient(
          () => _api().getActiveAllergiesForPatient(_patientId),
          () => _mockRaw(404, 'Not Found'),
        ),
        throwsA(isA<ApiException>()),
      );
    });

    test('uses GET method', () async {
      final (client, requests) = _capturingClient(200, {'data': []});
      await http.runWithClient(
        () => _api().getActiveAllergiesForPatient(_patientId),
        () => client,
      );
      expect(requests.single.method, 'GET');
    });
  });

  // ──────────────────────────────────────────────────────────────────────────
  // Group 8 — createAllergy()
  //
  // Covers:
  //  • 201 and 200 success responses
  //  • data envelope present / absent in response
  //  • required fields always included in request body
  //  • optional fields (notes, diagnosedDate) included iff non-null
  //  • non-2xx throws ApiException
  // ──────────────────────────────────────────────────────────────────────────
  group('createAllergy()', () {
    // Convenience wrapper that runs createAllergy() with a fixed client.
    Future<Map<String, dynamic>> create({
      MockClient? client,
      String allergen = 'Penicillin',
      String? notes,
      String? diagnosedDate,
      bool isActive = true,
    }) =>
        http.runWithClient(
          () => _api().createAllergy(
            patientId: _patientId,
            allergen: allergen,
            allergyType: 'DRUG',
            severity: 'MILD',
            reaction: 'Rash',
            notes: notes,
            diagnosedDate: diagnosedDate,
            isActive: isActive,
          ),
          () =>
              client ??
              _mockJson(201, {'data': {'id': 1, 'allergen': allergen}}),
        );

    test('returns data map from 201 response', () async {
      final res = await create();
      expect(res['allergen'], 'Penicillin');
    });

    test('returns data map from 200 response', () async {
      // Some backends respond 200 even on creation.
      final res = await create(
        client: _mockJson(200, {'data': {'id': 1, 'allergen': 'Penicillin'}}),
      );
      expect(res['allergen'], 'Penicillin');
    });

    test('returns empty map when data field is absent in response', () async {
      // Defensive: missing data envelope must not cause a cast exception.
      final res = await create(client: _mockJson(201, {'message': 'created'}));
      expect(res, isEmpty);
    });

    test('throws ApiException on 400 status', () async {
      await expectLater(
        create(client: _mockRaw(400, 'Bad Request')),
        throwsA(
          isA<ApiException>().having((e) => e.statusCode, 'statusCode', 400),
        ),
      );
    });

    test('request body includes all required fields', () async {
      // patientId, allergen, allergyType, severity, reaction, isActive are
      // always required — any omission would fail backend validation.
      final (client, requests) = _capturingClient(201, {'data': {'id': 1}});
      await http.runWithClient(
        () => _api().createAllergy(
          patientId: _patientId,
          allergen: 'Aspirin',
          allergyType: 'DRUG',
          severity: 'MODERATE',
          reaction: 'Nausea',
        ),
        () => client,
      );
      final body = jsonDecode(requests.single.body) as Map;
      expect(body['patientId'], _patientId);
      expect(body['allergen'], 'Aspirin');
      expect(body['allergyType'], 'DRUG');
      expect(body['severity'], 'MODERATE');
      expect(body['reaction'], 'Nausea');
      expect(body['isActive'], true); // default value
    });

    test('optional notes is included in body when provided', () async {
      final (client, requests) = _capturingClient(201, {'data': {'id': 1}});
      await http.runWithClient(
        () => _api().createAllergy(
          patientId: _patientId,
          allergen: 'X',
          allergyType: 'FOOD',
          severity: 'MILD',
          reaction: 'Itch',
          notes: 'Monitor closely',
        ),
        () => client,
      );
      final body = jsonDecode(requests.single.body) as Map;
      expect(body['notes'], 'Monitor closely');
    });

    test('optional notes is absent from body when not provided', () async {
      // Sending a null key may confuse the backend; the field must be omitted.
      final (client, requests) = _capturingClient(201, {'data': {'id': 1}});
      await http.runWithClient(
        () => _api().createAllergy(
          patientId: _patientId,
          allergen: 'X',
          allergyType: 'FOOD',
          severity: 'MILD',
          reaction: 'Itch',
        ),
        () => client,
      );
      final body = jsonDecode(requests.single.body) as Map;
      expect(body.containsKey('notes'), isFalse);
    });

    test('optional diagnosedDate is included in body when provided', () async {
      final (client, requests) = _capturingClient(201, {'data': {'id': 1}});
      await http.runWithClient(
        () => _api().createAllergy(
          patientId: _patientId,
          allergen: 'X',
          allergyType: 'DRUG',
          severity: 'SEVERE',
          reaction: 'Anaphylaxis',
          diagnosedDate: '2024-01-15',
        ),
        () => client,
      );
      final body = jsonDecode(requests.single.body) as Map;
      expect(body['diagnosedDate'], '2024-01-15');
    });

    test('optional diagnosedDate is absent from body when not provided',
        () async {
      final (client, requests) = _capturingClient(201, {'data': {'id': 1}});
      await http.runWithClient(
        () => _api().createAllergy(
          patientId: _patientId,
          allergen: 'X',
          allergyType: 'DRUG',
          severity: 'MILD',
          reaction: 'Rash',
        ),
        () => client,
      );
      final body = jsonDecode(requests.single.body) as Map;
      expect(body.containsKey('diagnosedDate'), isFalse);
    });

    test('isActive defaults to true in request body', () async {
      final (client, requests) = _capturingClient(201, {'data': {'id': 1}});
      await create(client: client);
      final body = jsonDecode(requests.single.body) as Map;
      expect(body['isActive'], true);
    });

    test('isActive can be set to false', () async {
      final (client, requests) = _capturingClient(201, {'data': {'id': 1}});
      await http.runWithClient(
        () => _api().createAllergy(
          patientId: _patientId,
          allergen: 'X',
          allergyType: 'DRUG',
          severity: 'MILD',
          reaction: 'Rash',
          isActive: false,
        ),
        () => client,
      );
      final body = jsonDecode(requests.single.body) as Map;
      expect(body['isActive'], false);
    });

    test('uses POST method', () async {
      final (client, requests) = _capturingClient(201, {'data': {'id': 1}});
      await create(client: client);
      expect(requests.single.method, 'POST');
    });

    test('hits /v1/api/allergies endpoint', () async {
      final (client, requests) = _capturingClient(201, {'data': {'id': 1}});
      await create(client: client);
      expect(requests.single.url.path, '/v1/api/allergies');
    });
  });

  // ──────────────────────────────────────────────────────────────────────────
  // Group 9 — updateAllergy()
  //
  // Only non-null fields should appear in the PUT body; missing fields must
  // be omitted entirely so existing values are preserved server-side.
  // ──────────────────────────────────────────────────────────────────────────
  group('updateAllergy()', () {
    test('returns data map on 200 response', () async {
      final res = await http.runWithClient(
        () => _api().updateAllergy(id: _allergyId, allergen: 'Updated'),
        () => _mockJson(200, {'data': {'id': _allergyId, 'allergen': 'Updated'}}),
      );
      expect(res['allergen'], 'Updated');
    });

    test('returns empty map when data field is absent', () async {
      final res = await http.runWithClient(
        () => _api().updateAllergy(id: _allergyId),
        () => _mockJson(200, {'message': 'updated'}),
      );
      expect(res, isEmpty);
    });

    test('throws ApiException on non-200 status', () async {
      await expectLater(
        http.runWithClient(
          () => _api().updateAllergy(id: _allergyId, allergen: 'X'),
          () => _mockRaw(404, 'Not Found'),
        ),
        throwsA(
          isA<ApiException>().having((e) => e.statusCode, 'statusCode', 404),
        ),
      );
    });

    test('request body contains only the provided (non-null) field', () async {
      // Sending only "severity" must not accidentally null-out other fields.
      final (client, requests) =
          _capturingClient(200, {'data': {'id': _allergyId}});
      await http.runWithClient(
        () => _api().updateAllergy(id: _allergyId, severity: 'SEVERE'),
        () => client,
      );
      final body = jsonDecode(requests.single.body) as Map;
      expect(body['severity'], 'SEVERE');
      expect(body.containsKey('allergen'), isFalse);
      expect(body.containsKey('allergyType'), isFalse);
      expect(body.containsKey('reaction'), isFalse);
      expect(body.containsKey('notes'), isFalse);
      expect(body.containsKey('diagnosedDate'), isFalse);
      expect(body.containsKey('isActive'), isFalse);
    });

    test('request body contains all provided fields', () async {
      // When every optional field is supplied, all must appear in the body.
      final (client, requests) =
          _capturingClient(200, {'data': {'id': _allergyId}});
      await http.runWithClient(
        () => _api().updateAllergy(
          id: _allergyId,
          allergen: 'Latex',
          allergyType: 'ENVIRONMENTAL',
          severity: 'MODERATE',
          reaction: 'Hives',
          notes: 'Updated notes',
          diagnosedDate: '2024-06-01',
          isActive: false,
        ),
        () => client,
      );
      final body = jsonDecode(requests.single.body) as Map;
      expect(body['allergen'], 'Latex');
      expect(body['allergyType'], 'ENVIRONMENTAL');
      expect(body['severity'], 'MODERATE');
      expect(body['reaction'], 'Hives');
      expect(body['notes'], 'Updated notes');
      expect(body['diagnosedDate'], '2024-06-01');
      expect(body['isActive'], false);
    });

    test('uses PUT method', () async {
      final (client, requests) =
          _capturingClient(200, {'data': {}});
      await http.runWithClient(
        () => _api().updateAllergy(id: _allergyId),
        () => client,
      );
      expect(requests.single.method, 'PUT');
    });

    test('hits /v1/api/allergies/<id> endpoint', () async {
      final (client, requests) =
          _capturingClient(200, {'data': {}});
      await http.runWithClient(
        () => _api().updateAllergy(id: _allergyId),
        () => client,
      );
      expect(requests.single.url.path, '/v1/api/allergies/$_allergyId');
    });
  });

  // ──────────────────────────────────────────────────────────────────────────
  // Group 10 — deactivateAllergy()
  //
  // Soft-delete via PATCH. Only status code matters; body is not parsed.
  // ──────────────────────────────────────────────────────────────────────────
  group('deactivateAllergy()', () {
    test('completes normally on 200 response', () async {
      await expectLater(
        http.runWithClient(
          () => _api().deactivateAllergy(_allergyId),
          () => _mockRaw(200, ''),
        ),
        completes,
      );
    });

    test('throws ApiException on non-200 status', () async {
      await expectLater(
        http.runWithClient(
          () => _api().deactivateAllergy(_allergyId),
          () => _mockRaw(404, 'Not Found'),
        ),
        throwsA(
          isA<ApiException>().having((e) => e.statusCode, 'statusCode', 404),
        ),
      );
    });

    test('uses PATCH method', () async {
      final (client, requests) = _capturingClient(200, {});
      await http.runWithClient(
        () => _api().deactivateAllergy(_allergyId),
        () => client,
      );
      expect(requests.single.method, 'PATCH');
    });

    test('hits /v1/api/allergies/<id>/deactivate endpoint', () async {
      final (client, requests) = _capturingClient(200, {});
      await http.runWithClient(
        () => _api().deactivateAllergy(_allergyId),
        () => client,
      );
      expect(
        requests.single.url.path,
        '/v1/api/allergies/$_allergyId/deactivate',
      );
    });
  });

  // ──────────────────────────────────────────────────────────────────────────
  // Group 11 — deleteAllergy()
  //
  // Hard-delete via DELETE. Accepts both 200 and 204 (no-content) as success.
  // ──────────────────────────────────────────────────────────────────────────
  group('deleteAllergy()', () {
    test('completes normally on 200 response', () async {
      await expectLater(
        http.runWithClient(
          () => _api().deleteAllergy(_allergyId),
          () => _mockRaw(200, ''),
        ),
        completes,
      );
    });

    test('completes normally on 204 no-content response', () async {
      // RFC 7231: 204 is the canonical success code for DELETE operations.
      await expectLater(
        http.runWithClient(
          () => _api().deleteAllergy(_allergyId),
          () => _mockRaw(204, ''),
        ),
        completes,
      );
    });

    test('throws ApiException on 404 status', () async {
      await expectLater(
        http.runWithClient(
          () => _api().deleteAllergy(_allergyId),
          () => _mockRaw(404, 'Not Found'),
        ),
        throwsA(
          isA<ApiException>().having((e) => e.statusCode, 'statusCode', 404),
        ),
      );
    });

    test('throws ApiException on 500 status', () async {
      await expectLater(
        http.runWithClient(
          () => _api().deleteAllergy(_allergyId),
          () => _mockRaw(500, 'Internal Server Error'),
        ),
        throwsA(
          isA<ApiException>().having((e) => e.statusCode, 'statusCode', 500),
        ),
      );
    });

    test('uses DELETE method', () async {
      final (client, requests) = _capturingClient(200, {});
      await http.runWithClient(
        () => _api().deleteAllergy(_allergyId),
        () => client,
      );
      expect(requests.single.method, 'DELETE');
    });

    test('hits /v1/api/allergies/<id> endpoint', () async {
      final (client, requests) = _capturingClient(200, {});
      await http.runWithClient(
        () => _api().deleteAllergy(_allergyId),
        () => client,
      );
      expect(requests.single.url.path, '/v1/api/allergies/$_allergyId');
    });
  });

  // ──────────────────────────────────────────────────────────────────────────
  // Group 12 — Error message content
  //
  // ApiException.message should carry the raw response body so downstream
  // callers and logs can see what the server actually said.
  // ──────────────────────────────────────────────────────────────────────────
  group('Error message content', () {
    test('ApiException message contains server response body', () async {
      // The exact error body (e.g. validation details) must reach the caller.
      await expectLater(
        http.runWithClient(
          () => _api().deactivateAllergy(_allergyId),
          () => _mockRaw(422, 'Validation failed: severity is required'),
        ),
        throwsA(
          isA<ApiException>().having(
            (e) => e.message,
            'message',
            contains('Validation failed'),
          ),
        ),
      );
    });

    test('getAllergiesForPatient error message contains method context',
        () async {
      // The error prefix identifies which call failed, simplifying debugging.
      await expectLater(
        http.runWithClient(
          () => _api().getAllergiesForPatient(_patientId),
          () => _mockRaw(500, 'db error'),
        ),
        throwsA(
          isA<ApiException>().having(
            (e) => e.message,
            'message',
            contains('getAllergiesForPatient'),
          ),
        ),
      );
    });
  });
}
