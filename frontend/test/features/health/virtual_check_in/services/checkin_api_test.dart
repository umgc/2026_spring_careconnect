// test/features/health/virtual_check_in/services/checkin_api_test.dart
//
// NOTE: This is a Dart/Flutter project. The equivalent of JUnit here is the
// `flutter_test` package (same test toolchain, different class names).
//
// CheckInApi accepts an injected http.Client, so all tests pass a MockClient
// directly via the constructor — no http.runWithClient() wrapping needed.
// No real network traffic occurs during these tests.
//
// Coverage targets:
//   • Base-URL normalisation (_normalizeBase)
//   • Outgoing request headers (Content-Type, Authorization)
//   • getQuestions()    – URL, method, JSON parsing, error handling
//   • submitAnswers()   – URL, method, body shape, 200/201 acceptance
//   • createQuestion()  – URL, method, body shape, DTO parsing
//   • updateQuestion()  – URL, method, id embedding, DTO parsing
//   • deactivateQuestion() – URL, method, query parameter, void return
//   • close()           – resource cleanup

import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

import 'package:care_connect_app/features/health/virtual_check_in/services/checkin_api.dart';
import 'package:care_connect_app/features/health/virtual_check_in/models/question_type.dart';
import 'package:care_connect_app/features/health/virtual_check_in/models/virtual_check_in_backend_question_model.dart';
import 'package:care_connect_app/features/health/virtual_check_in/models/virtual_check_in_backend_model.dart'
    show SubmitAnswersRequest, AnswerItem;

// ── Test constants ────────────────────────────────────────────────────────────

const _base = 'https://api.example.com';
const _jwt = 'test.jwt.token';
const _checkInId = 'checkin-abc-123';
const _questionId = 42;

// ── Fixture helpers ───────────────────────────────────────────────────────────

/// Minimal valid question payload as returned by the backend.
Map<String, dynamic> _questionJson({int id = _questionId}) => {
      'id': id,
      'prompt': 'How are you feeling today?',
      'type': 'TEXT',
      'required': true,
      'active': true,
      'ordinal': 1,
    };

/// A matching BackendQuestionDto for use as an outbound request body.
BackendQuestionDto _questionDto({int? id = _questionId}) => BackendQuestionDto(
      id: id,
      prompt: 'How are you feeling today?',
      type: BackendQuestionType.text,
      required: true,
      active: true,
      ordinal: 1,
    );

// ── Factory helpers ───────────────────────────────────────────────────────────

/// Creates a [CheckInApi] wired with the provided [client] and optional [jwt].
/// [jwt] defaults to the test token constant; pass `null` for anonymous mode.
CheckInApi _api({http.Client? client, String? jwt = _jwt}) => CheckInApi(
      _base,
      client: client ?? http.Client(),
      jwt: jwt,
    );

/// A [MockClient] that replies to every request with [statusCode] and a
/// JSON-encoded [body].
MockClient _mockJson(int statusCode, Object body) =>
    MockClient((_) async => http.Response(jsonEncode(body), statusCode));

/// A [MockClient] that replies with [statusCode] and a plain-text [rawBody].
/// Use for empty bodies or error payloads that are not JSON.
MockClient _mockRaw(int statusCode, String rawBody) =>
    MockClient((_) async => http.Response(rawBody, statusCode));

/// Returns a [MockClient] that captures every outgoing [http.Request] into the
/// returned list and replies with [statusCode] + JSON-encoded [body].
///
/// ```dart
/// final (client, requests) = _capturingClient(200, someBody);
/// await _api(client: client).someMethod();
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

// ── Tests ─────────────────────────────────────────────────────────────────────

void main() {
  // ────────────────────────────────────────────────────────────────────────────
  // Group 1 — Base-URL normalisation (_normalizeBase)
  //
  // _normalizeBase trims whitespace and removes a trailing slash so that every
  // path segment appended later never produces a double-slash.  Tested
  // indirectly by inspecting the URL of a real request.
  // ────────────────────────────────────────────────────────────────────────────
  group('CheckInApi — base URL normalisation', () {
    test('trailing slash is stripped so URLs never contain double slashes',
        () async {
      // '$_base/' + '/api/...' would otherwise produce '.com//api/...'.
      final (client, requests) = _capturingClient(200, <dynamic>[]);
      final api = CheckInApi('$_base/', client: client);
      await api.getQuestions(_checkInId);
      // Path must start with exactly one slash.
      expect(requests.single.url.path, startsWith('/api/'));
      expect(requests.single.url.path, isNot(startsWith('//api/')));
    });

    test('base URL without trailing slash is used unchanged', () async {
      final (client, requests) = _capturingClient(200, <dynamic>[]);
      final api = CheckInApi(_base, client: client);
      await api.getQuestions(_checkInId);
      expect(requests.single.url.host, 'api.example.com');
    });

    test('surrounding whitespace in base URL is trimmed', () async {
      // _normalizeBase calls trim() before the slash check.
      final (client, requests) = _capturingClient(200, <dynamic>[]);
      final api = CheckInApi('  $_base  ', client: client);
      await api.getQuestions(_checkInId);
      expect(requests.single.url.host, 'api.example.com');
    });
  });

  // ────────────────────────────────────────────────────────────────────────────
  // Group 2 — Request headers
  //
  // _headers() always sets Content-Type: application/json.
  // It adds Authorization: Bearer <jwt> only when a non-empty JWT is provided.
  // ────────────────────────────────────────────────────────────────────────────
  group('Request headers', () {
    test('Content-Type is application/json on GET request', () async {
      // Every outgoing request must declare the content type regardless of body.
      final (client, requests) = _capturingClient(200, <dynamic>[]);
      await _api(client: client).getQuestions(_checkInId);
      expect(requests.single.headers['Content-Type'], 'application/json');
    });

    test('Content-Type is application/json on POST request', () async {
      final (client, requests) = _capturingClient(200, _questionJson());
      await _api(client: client).createQuestion(_questionDto());
      expect(requests.single.headers['Content-Type'], 'application/json');
    });

    test('Content-Type is application/json on PUT request', () async {
      final (client, requests) = _capturingClient(200, _questionJson());
      await _api(client: client).updateQuestion(_questionId, _questionDto());
      expect(requests.single.headers['Content-Type'], 'application/json');
    });

    test('Authorization header is "Bearer <jwt>" when jwt is provided', () async {
      // The JWT must be forwarded verbatim inside the Bearer scheme.
      final (client, requests) = _capturingClient(200, <dynamic>[]);
      await _api(client: client, jwt: _jwt).getQuestions(_checkInId);
      expect(requests.single.headers['Authorization'], 'Bearer $_jwt');
    });

    test('Authorization header is absent when jwt is null', () async {
      // Anonymous/server-to-server calls must not send a token.
      final (client, requests) = _capturingClient(200, <dynamic>[]);
      await _api(client: client, jwt: null).getQuestions(_checkInId);
      expect(requests.single.headers.containsKey('Authorization'), isFalse);
    });

    test('Authorization header is absent when jwt is an empty string', () async {
      // An empty jwt is equivalent to no jwt — the header must be omitted.
      final (client, requests) = _capturingClient(200, <dynamic>[]);
      await _api(client: client, jwt: '').getQuestions(_checkInId);
      expect(requests.single.headers.containsKey('Authorization'), isFalse);
    });
  });

  // ────────────────────────────────────────────────────────────────────────────
  // Group 3 — getQuestions(checkInId)
  //
  // GET /api/checkins/{checkInId}/questions
  //
  // Success (200): parses the JSON array into a typed List<BackendQuestionDto>.
  // Non-200: throws an Exception that includes the status code in its message.
  // ────────────────────────────────────────────────────────────────────────────
  group('getQuestions()', () {
    test('uses GET method', () async {
      final (client, requests) = _capturingClient(200, <dynamic>[]);
      await _api(client: client).getQuestions(_checkInId);
      expect(requests.single.method, 'GET');
    });

    test('hits /api/checkins/{checkInId}/questions endpoint', () async {
      // The path must embed the checkInId verbatim.
      final (client, requests) = _capturingClient(200, <dynamic>[]);
      await _api(client: client).getQuestions(_checkInId);
      expect(
        requests.single.url.path,
        '/api/checkins/$_checkInId/questions',
      );
    });

    test('embeds a different checkInId in the URL path', () async {
      // Each call must use the supplied ID, not a cached one.
      const otherId = 'other-checkin-999';
      final (client, requests) = _capturingClient(200, <dynamic>[]);
      await _api(client: client).getQuestions(otherId);
      expect(requests.single.url.path, contains(otherId));
    });

    test('returns an empty list when the server responds with []', () async {
      final result =
          await _api(client: _mockJson(200, <dynamic>[])).getQuestions(_checkInId);
      expect(result, isEmpty);
    });

    test('returns a list of BackendQuestionDto on 200', () async {
      // The service must deserialise every element into a typed DTO.
      final body = [_questionJson(id: 1), _questionJson(id: 2)];
      final result =
          await _api(client: _mockJson(200, body)).getQuestions(_checkInId);
      expect(result, hasLength(2));
      expect(result.first, isA<BackendQuestionDto>());
    });

    test('returned DTO has correct prompt', () async {
      final result = await _api(client: _mockJson(200, [_questionJson()]))
          .getQuestions(_checkInId);
      expect(result.single.prompt, 'How are you feeling today?');
    });

    test('returned DTO has correct id', () async {
      final result = await _api(client: _mockJson(200, [_questionJson(id: 77)]))
          .getQuestions(_checkInId);
      expect(result.single.id, 77);
    });

    test('returned DTO has correct type', () async {
      final result = await _api(client: _mockJson(200, [_questionJson()]))
          .getQuestions(_checkInId);
      expect(result.single.type, BackendQuestionType.text);
    });

    test('returned DTO has correct required flag', () async {
      final result = await _api(client: _mockJson(200, [_questionJson()]))
          .getQuestions(_checkInId);
      expect(result.single.required, isTrue);
    });

    test('returned DTO has correct active flag', () async {
      final result = await _api(client: _mockJson(200, [_questionJson()]))
          .getQuestions(_checkInId);
      expect(result.single.active, isTrue);
    });

    test('returned DTO has correct ordinal', () async {
      final result = await _api(client: _mockJson(200, [_questionJson()]))
          .getQuestions(_checkInId);
      expect(result.single.ordinal, 1);
    });

    test('throws Exception on 404 status', () async {
      await expectLater(
        _api(client: _mockRaw(404, 'Not Found')).getQuestions(_checkInId),
        throwsA(isA<Exception>()),
      );
    });

    test('throws Exception on 500 status', () async {
      await expectLater(
        _api(client: _mockRaw(500, 'Internal Server Error'))
            .getQuestions(_checkInId),
        throwsA(isA<Exception>()),
      );
    });

    test('throws Exception on 401 status', () async {
      await expectLater(
        _api(client: _mockRaw(401, 'Unauthorized')).getQuestions(_checkInId),
        throwsA(isA<Exception>()),
      );
    });

    test('exception message includes the HTTP status code', () async {
      // Status codes must reach the caller so they can be logged or acted upon.
      await expectLater(
        _api(client: _mockRaw(403, 'Forbidden')).getQuestions(_checkInId),
        throwsA(
          isA<Exception>().having(
            (e) => e.toString(),
            'message',
            contains('403'),
          ),
        ),
      );
    });
  });

  // ────────────────────────────────────────────────────────────────────────────
  // Group 4 — submitAnswers(checkInId, request)
  //
  // POST /api/checkins/{checkInId}/answers
  //
  // Both 200 and 201 are valid success codes.  Non-2xx must throw.
  // The body must carry the serialised answers list.
  // ────────────────────────────────────────────────────────────────────────────
  group('submitAnswers()', () {
    // Convenience builder for a minimal SubmitAnswersRequest.
    SubmitAnswersRequest req() =>
        SubmitAnswersRequest([AnswerItem.text(questionId: 1, value: 'Good')]);

    test('uses POST method', () async {
      final (client, requests) = _capturingClient(200, <String, dynamic>{});
      await _api(client: client).submitAnswers(_checkInId, req());
      expect(requests.single.method, 'POST');
    });

    test('hits /api/checkins/{checkInId}/answers endpoint', () async {
      final (client, requests) = _capturingClient(200, <String, dynamic>{});
      await _api(client: client).submitAnswers(_checkInId, req());
      expect(
        requests.single.url.path,
        '/api/checkins/$_checkInId/answers',
      );
    });

    test('completes normally on 200 response', () async {
      await expectLater(
        _api(client: _mockRaw(200, '')).submitAnswers(_checkInId, req()),
        completes,
      );
    });

    test('completes normally on 201 created response', () async {
      // Some backends return 201 for successful answer submission.
      await expectLater(
        _api(client: _mockRaw(201, '')).submitAnswers(_checkInId, req()),
        completes,
      );
    });

    test('throws Exception on 400 status', () async {
      await expectLater(
        _api(client: _mockRaw(400, 'Bad Request'))
            .submitAnswers(_checkInId, req()),
        throwsA(isA<Exception>()),
      );
    });

    test('throws Exception on 500 status', () async {
      await expectLater(
        _api(client: _mockRaw(500, 'Server Error'))
            .submitAnswers(_checkInId, req()),
        throwsA(isA<Exception>()),
      );
    });

    test('request body contains an "answers" list', () async {
      // Backend validation will fail if the key is missing.
      final (client, requests) = _capturingClient(200, <String, dynamic>{});
      await _api(client: client).submitAnswers(_checkInId, req());
      final body = jsonDecode(requests.single.body) as Map;
      expect(body.containsKey('answers'), isTrue);
      expect(body['answers'], isA<List>());
    });

    test('answers list has one entry for a single-answer request', () async {
      final (client, requests) = _capturingClient(200, <String, dynamic>{});
      await _api(client: client).submitAnswers(_checkInId, req());
      final body = jsonDecode(requests.single.body) as Map;
      expect((body['answers'] as List), hasLength(1));
    });

    test('text answer item carries correct questionId', () async {
      final (client, requests) = _capturingClient(200, <String, dynamic>{});
      final req = SubmitAnswersRequest(
          [AnswerItem.text(questionId: 7, value: 'Fine')]);
      await _api(client: client).submitAnswers(_checkInId, req);
      final answers =
          (jsonDecode(requests.single.body) as Map)['answers'] as List;
      expect(answers.first['questionId'], 7);
    });

    test('text answer item carries valueText field', () async {
      final (client, requests) = _capturingClient(200, <String, dynamic>{});
      final req = SubmitAnswersRequest(
          [AnswerItem.text(questionId: 1, value: 'Great')]);
      await _api(client: client).submitAnswers(_checkInId, req);
      final answers =
          (jsonDecode(requests.single.body) as Map)['answers'] as List;
      expect(answers.first['valueText'], 'Great');
    });

    test('boolean answer item carries valueBoolean field', () async {
      final (client, requests) = _capturingClient(200, <String, dynamic>{});
      final req = SubmitAnswersRequest(
          [AnswerItem.boolean(questionId: 2, value: true)]);
      await _api(client: client).submitAnswers(_checkInId, req);
      final answers =
          (jsonDecode(requests.single.body) as Map)['answers'] as List;
      expect(answers.first['valueBoolean'], true);
    });

    test('number answer item carries valueNumber field', () async {
      final (client, requests) = _capturingClient(200, <String, dynamic>{});
      final req =
          SubmitAnswersRequest([AnswerItem.number(questionId: 3, value: 7)]);
      await _api(client: client).submitAnswers(_checkInId, req);
      final answers =
          (jsonDecode(requests.single.body) as Map)['answers'] as List;
      expect(answers.first['valueNumber'], 7);
    });

    test('exception message includes the HTTP status code', () async {
      await expectLater(
        _api(client: _mockRaw(422, 'Unprocessable'))
            .submitAnswers(_checkInId, req()),
        throwsA(
          isA<Exception>().having(
            (e) => e.toString(),
            'message',
            contains('422'),
          ),
        ),
      );
    });
  });

  // ────────────────────────────────────────────────────────────────────────────
  // Group 5 — createQuestion(question)
  //
  // POST /api/questions
  //
  // Returns a parsed BackendQuestionDto from the 200 response body.
  // Non-200 must throw.  The request body must be the DTO's JSON representation.
  // ────────────────────────────────────────────────────────────────────────────
  group('createQuestion()', () {
    test('uses POST method', () async {
      final (client, requests) = _capturingClient(200, _questionJson());
      await _api(client: client).createQuestion(_questionDto(id: null));
      expect(requests.single.method, 'POST');
    });

    test('hits /api/questions endpoint', () async {
      final (client, requests) = _capturingClient(200, _questionJson());
      await _api(client: client).createQuestion(_questionDto(id: null));
      expect(requests.single.url.path, '/api/questions');
    });

    test('returns a BackendQuestionDto on 200 response', () async {
      final dto = await _api(client: _mockJson(200, _questionJson()))
          .createQuestion(_questionDto(id: null));
      expect(dto, isA<BackendQuestionDto>());
    });

    test('returned DTO has the server-assigned id', () async {
      // The backend assigns the id; the response value must be reflected.
      final dto = await _api(client: _mockJson(200, _questionJson(id: 55)))
          .createQuestion(_questionDto(id: null));
      expect(dto.id, 55);
    });

    test('returned DTO has the correct prompt', () async {
      final dto = await _api(client: _mockJson(200, _questionJson()))
          .createQuestion(_questionDto());
      expect(dto.prompt, 'How are you feeling today?');
    });

    test('returned DTO has the correct type', () async {
      final dto = await _api(client: _mockJson(200, _questionJson()))
          .createQuestion(_questionDto());
      expect(dto.type, BackendQuestionType.text);
    });

    test('throws Exception on 400 status', () async {
      await expectLater(
        _api(client: _mockRaw(400, 'Bad Request'))
            .createQuestion(_questionDto()),
        throwsA(isA<Exception>()),
      );
    });

    test('throws Exception on 500 status', () async {
      await expectLater(
        _api(client: _mockRaw(500, 'Server Error')).createQuestion(_questionDto()),
        throwsA(isA<Exception>()),
      );
    });

    test('request body includes the prompt field', () async {
      final (client, requests) = _capturingClient(200, _questionJson());
      await _api(client: client).createQuestion(_questionDto());
      final body = jsonDecode(requests.single.body) as Map;
      expect(body['prompt'], 'How are you feeling today?');
    });

    test('request body includes the type as wire value (e.g. "TEXT")', () async {
      // Backend expects the wire string, not the Dart enum name.
      final (client, requests) = _capturingClient(200, _questionJson());
      await _api(client: client).createQuestion(_questionDto());
      final body = jsonDecode(requests.single.body) as Map;
      expect(body['type'], 'TEXT');
    });

    test('request body includes the required flag', () async {
      final (client, requests) = _capturingClient(200, _questionJson());
      await _api(client: client).createQuestion(_questionDto());
      final body = jsonDecode(requests.single.body) as Map;
      expect(body['required'], isTrue);
    });

    test('request body includes the active flag', () async {
      final (client, requests) = _capturingClient(200, _questionJson());
      await _api(client: client).createQuestion(_questionDto());
      final body = jsonDecode(requests.single.body) as Map;
      expect(body['active'], isTrue);
    });

    test('request body includes the ordinal', () async {
      final (client, requests) = _capturingClient(200, _questionJson());
      await _api(client: client).createQuestion(_questionDto());
      final body = jsonDecode(requests.single.body) as Map;
      expect(body['ordinal'], 1);
    });

    test('request body omits "id" when dto.id is null', () async {
      // A new question has no id yet; sending null would confuse the backend.
      final (client, requests) = _capturingClient(200, _questionJson());
      await _api(client: client).createQuestion(_questionDto(id: null));
      final body = jsonDecode(requests.single.body) as Map;
      expect(body.containsKey('id'), isFalse);
    });

    test('exception message includes the HTTP status code', () async {
      await expectLater(
        _api(client: _mockRaw(409, 'Conflict')).createQuestion(_questionDto()),
        throwsA(
          isA<Exception>().having(
            (e) => e.toString(),
            'message',
            contains('409'),
          ),
        ),
      );
    });
  });

  // ────────────────────────────────────────────────────────────────────────────
  // Group 6 — updateQuestion(id, question)
  //
  // PUT /api/questions/{id}
  //
  // The path must embed the numeric id.  Returns a parsed BackendQuestionDto.
  // Non-200 must throw.
  // ────────────────────────────────────────────────────────────────────────────
  group('updateQuestion()', () {
    test('uses PUT method', () async {
      final (client, requests) = _capturingClient(200, _questionJson());
      await _api(client: client).updateQuestion(_questionId, _questionDto());
      expect(requests.single.method, 'PUT');
    });

    test('hits /api/questions/{id} endpoint', () async {
      final (client, requests) = _capturingClient(200, _questionJson());
      await _api(client: client).updateQuestion(_questionId, _questionDto());
      expect(requests.single.url.path, '/api/questions/$_questionId');
    });

    test('embeds the id in the URL path', () async {
      // Using a distinct id must produce the matching URL segment.
      final (client, requests) = _capturingClient(200, _questionJson(id: 99));
      await _api(client: client).updateQuestion(99, _questionDto());
      expect(requests.single.url.path, '/api/questions/99');
    });

    test('returns a BackendQuestionDto on 200 response', () async {
      final dto = await _api(client: _mockJson(200, _questionJson()))
          .updateQuestion(_questionId, _questionDto());
      expect(dto, isA<BackendQuestionDto>());
    });

    test('returned DTO reflects the updated prompt from the response body',
        () async {
      // The DTO must be built from the response, not the request payload.
      final updatedJson = {..._questionJson(), 'prompt': 'Updated prompt text'};
      final dto = await _api(client: _mockJson(200, updatedJson))
          .updateQuestion(_questionId, _questionDto());
      expect(dto.prompt, 'Updated prompt text');
    });

    test('returned DTO reflects the updated type', () async {
      final updatedJson = {..._questionJson(), 'type': 'YES_NO'};
      final dto = await _api(client: _mockJson(200, updatedJson))
          .updateQuestion(_questionId, _questionDto());
      expect(dto.type, BackendQuestionType.yesNo);
    });

    test('throws Exception on 404 status', () async {
      await expectLater(
        _api(client: _mockRaw(404, 'Not Found'))
            .updateQuestion(_questionId, _questionDto()),
        throwsA(isA<Exception>()),
      );
    });

    test('throws Exception on 500 status', () async {
      await expectLater(
        _api(client: _mockRaw(500, 'Server Error'))
            .updateQuestion(_questionId, _questionDto()),
        throwsA(isA<Exception>()),
      );
    });

    test('request body contains the updated prompt', () async {
      final updated = _questionDto().copyWith(prompt: 'New prompt');
      final (client, requests) = _capturingClient(200, _questionJson());
      await _api(client: client).updateQuestion(_questionId, updated);
      final body = jsonDecode(requests.single.body) as Map;
      expect(body['prompt'], 'New prompt');
    });

    test('request body contains the updated type as wire value', () async {
      // BackendQuestionType.number must produce the wire string "NUMBER".
      final updated = _questionDto().copyWith(type: BackendQuestionType.number);
      final (client, requests) = _capturingClient(200, _questionJson());
      await _api(client: client).updateQuestion(_questionId, updated);
      final body = jsonDecode(requests.single.body) as Map;
      expect(body['type'], 'NUMBER');
    });

    test('request body contains the YES_NO type as wire value', () async {
      final updated = _questionDto().copyWith(type: BackendQuestionType.yesNo);
      final (client, requests) = _capturingClient(200, _questionJson());
      await _api(client: client).updateQuestion(_questionId, updated);
      final body = jsonDecode(requests.single.body) as Map;
      expect(body['type'], 'YES_NO');
    });

    test('request body contains the TRUE_FALSE type as wire value', () async {
      final updated =
          _questionDto().copyWith(type: BackendQuestionType.trueFalse);
      final (client, requests) = _capturingClient(200, _questionJson());
      await _api(client: client).updateQuestion(_questionId, updated);
      final body = jsonDecode(requests.single.body) as Map;
      expect(body['type'], 'TRUE_FALSE');
    });

    test('exception message includes the HTTP status code', () async {
      await expectLater(
        _api(client: _mockRaw(403, 'Forbidden'))
            .updateQuestion(_questionId, _questionDto()),
        throwsA(
          isA<Exception>().having(
            (e) => e.toString(),
            'message',
            contains('403'),
          ),
        ),
      );
    });
  });

  // ────────────────────────────────────────────────────────────────────────────
  // Group 7 — deactivateQuestion(id)
  //
  // PATCH /api/questions/{id}/active?active=false
  //
  // Returns void.  Non-200 must throw.  The `active=false` query parameter is
  // the signal to the backend to deactivate — it must always be present.
  // ────────────────────────────────────────────────────────────────────────────
  group('deactivateQuestion()', () {
    test('uses PATCH method', () async {
      final (client, requests) = _capturingClient(200, <String, dynamic>{});
      await _api(client: client).deactivateQuestion(_questionId);
      expect(requests.single.method, 'PATCH');
    });

    test('hits /api/questions/{id}/active path', () async {
      final (client, requests) = _capturingClient(200, <String, dynamic>{});
      await _api(client: client).deactivateQuestion(_questionId);
      expect(
        requests.single.url.path,
        '/api/questions/$_questionId/active',
      );
    });

    test('includes "active=false" as a query parameter', () async {
      // The query string communicates the deactivation intent to the backend.
      final (client, requests) = _capturingClient(200, <String, dynamic>{});
      await _api(client: client).deactivateQuestion(_questionId);
      expect(requests.single.url.queryParameters['active'], 'false');
    });

    test('embeds the id in the URL path', () async {
      const otherId = 77;
      final (client, requests) = _capturingClient(200, <String, dynamic>{});
      await _api(client: client).deactivateQuestion(otherId);
      expect(requests.single.url.path, '/api/questions/$otherId/active');
    });

    test('completes normally on 200 response', () async {
      await expectLater(
        _api(client: _mockRaw(200, '')).deactivateQuestion(_questionId),
        completes,
      );
    });

    test('throws Exception on 404 status', () async {
      await expectLater(
        _api(client: _mockRaw(404, 'Not Found'))
            .deactivateQuestion(_questionId),
        throwsA(isA<Exception>()),
      );
    });

    test('throws Exception on 500 status', () async {
      await expectLater(
        _api(client: _mockRaw(500, 'Internal Server Error'))
            .deactivateQuestion(_questionId),
        throwsA(isA<Exception>()),
      );
    });

    test('throws Exception on 403 status', () async {
      await expectLater(
        _api(client: _mockRaw(403, 'Forbidden'))
            .deactivateQuestion(_questionId),
        throwsA(isA<Exception>()),
      );
    });

    test('exception message includes the HTTP status code', () async {
      await expectLater(
        _api(client: _mockRaw(409, 'Conflict')).deactivateQuestion(_questionId),
        throwsA(
          isA<Exception>().having(
            (e) => e.toString(),
            'message',
            contains('409'),
          ),
        ),
      );
    });
  });

  // ────────────────────────────────────────────────────────────────────────────
  // Group 8 — close()
  //
  // close() forwards to the underlying http.Client.  It must not throw, even
  // when called more than once (some callers call it in a finally block that may
  // execute after a previous explicit close).
  // ────────────────────────────────────────────────────────────────────────────
  group('close()', () {
    test('does not throw when called once', () {
      // Releasing resources must always be safe to call.
      final api = CheckInApi(_base, client: _mockRaw(200, ''));
      expect(() => api.close(), returnsNormally);
    });

    test('does not throw when called a second time', () {
      // Idempotent close() prevents resource-leak bugs in finally blocks.
      final api = CheckInApi(_base, client: _mockRaw(200, ''));
      api.close();
      expect(() => api.close(), returnsNormally);
    });
  });
}
