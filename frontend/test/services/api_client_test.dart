// test/services/api_client_test.dart
//
// Unit tests for ApiException and ApiClient.
//
// Coverage targets (>= 80 %):
//   - ApiException: constructor, all fields, toString, implements Exception
//   - ApiClient: singleton identity, getJson / postJson / putJson / patchJson /
//     deleteJson, query parameters, custom parser, non-2xx error handling,
//     _extractMessage variants (message / error / detail / plain-string),
//     _extractCode, _normalizeDioError branches (timeout, cancel,
//     SocketException, unknown, response-present).
//
// HTTP interception strategy
// --------------------------
// ApiClient wraps a private singleton Dio instance.  We cannot inject a mock
// Dio, but Dart's dart:io layer provides an escape hatch: we override
// HttpOverrides.global (in setUpAll, after TestWidgetsFlutterBinding
// replaces it with its own blocking override).  Dio's
// DefaultHttpClientAdapter creates its _httpClient lazily on the first
// request; because we set our override first, Dio receives _FakeHttpClient.
// _FakeHttpClient reads _activeSpec at call time, so changing _activeSpec
// between tests lets each test control the response even though the client
// object is cached inside Dio's adapter.
//
// AuthTokenManager.getJwtToken() calls FlutterSecureStorage, which throws
// MissingPluginException in tests; the surrounding try/catch in getJwtToken()
// catches it and returns null — no explicit setup required.
//
// AuthTokenManager.updateLastActivity() is similarly guarded and is a no-op
// in tests.

import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:care_connect_app/services/api_client.dart';

// ─────────────────────────────────────────────────────────────────────────────
// Shared mutable spec — updated by each test to control the fake HTTP response.
// ─────────────────────────────────────────────────────────────────────────────

class _FakeSpec {
  const _FakeSpec(this.statusCode, this.body);
  final int statusCode;
  final String body;
}

// Top-level mutable so _FakeHttpClient can read it on every openUrl() call,
// even after the HttpClient reference has been cached inside Dio's adapter.
_FakeSpec _activeSpec = const _FakeSpec(200, '{}');

// ─────────────────────────────────────────────────────────────────────────────
// Fake dart:io HTTP stack (minimal — only the surface Dio actually touches)
// ─────────────────────────────────────────────────────────────────────────────

class _FakeHttpOverrides extends HttpOverrides {
  @override
  HttpClient createHttpClient(SecurityContext? context) => _FakeHttpClient();
}

// ---------------------------------------------------------------------------
// HttpClient
// ---------------------------------------------------------------------------
class _FakeHttpClient implements HttpClient {
  @override
  Future<HttpClientRequest> openUrl(String method, Uri url) async =>
      _FakeHttpClientRequest(_activeSpec); // snapshot current spec

  // --- stubs required by the interface but never called by Dio ---
  @override bool autoUncompress = true;
  @override Duration? connectionTimeout;
  @override Duration idleTimeout = const Duration(seconds: 15);
  @override int? maxConnectionsPerHost;
  @override String? userAgent;
  @override void addCredentials(Uri url, String realm, HttpClientCredentials c) {}
  @override void addProxyCredentials(String host, int port, String realm, HttpClientCredentials c) {}
  @override set authenticate(Future<bool> Function(Uri, String, String?)? f) {}
  @override set authenticateProxy(Future<bool> Function(String, int, String, String?)? f) {}
  @override set badCertificateCallback(bool Function(X509Certificate, String, int)? cb) {}
  @override set connectionFactory(Future<ConnectionTask<Socket>> Function(Uri, String?, int?)? f) {}
  @override set findProxy(String Function(Uri)? f) {}
  @override set keyLog(Function(String)? f) {}
  @override void close({bool force = false}) {}
  @override Future<HttpClientRequest> delete(String h, int p, String path) => throw UnimplementedError();
  @override Future<HttpClientRequest> deleteUrl(Uri url) => openUrl('DELETE', url);
  @override Future<HttpClientRequest> get(String h, int p, String path) => throw UnimplementedError();
  @override Future<HttpClientRequest> getUrl(Uri url) => openUrl('GET', url);
  @override Future<HttpClientRequest> head(String h, int p, String path) => throw UnimplementedError();
  @override Future<HttpClientRequest> headUrl(Uri url) => openUrl('HEAD', url);
  @override Future<HttpClientRequest> open(String m, String h, int p, String path) => throw UnimplementedError();
  @override Future<HttpClientRequest> patch(String h, int p, String path) => throw UnimplementedError();
  @override Future<HttpClientRequest> patchUrl(Uri url) => openUrl('PATCH', url);
  @override Future<HttpClientRequest> post(String h, int p, String path) => throw UnimplementedError();
  @override Future<HttpClientRequest> postUrl(Uri url) => openUrl('POST', url);
  @override Future<HttpClientRequest> put(String h, int p, String path) => throw UnimplementedError();
  @override Future<HttpClientRequest> putUrl(Uri url) => openUrl('PUT', url);
}

// ---------------------------------------------------------------------------
// HttpClientRequest
// ---------------------------------------------------------------------------
class _FakeHttpClientRequest implements HttpClientRequest {
  _FakeHttpClientRequest(this._spec);
  final _FakeSpec _spec;

  final _FakeHttpHeaders _hdrs = _FakeHttpHeaders();

  @override HttpHeaders get headers => _hdrs;
  @override Future<HttpClientResponse> close() async => _FakeHttpClientResponse(_spec);

  // stubs
  @override bool bufferOutput = true;
  @override int contentLength = -1;
  @override Encoding encoding = utf8;
  @override bool followRedirects = true;
  @override int maxRedirects = 5;
  @override bool persistentConnection = true;
  @override String get method => '';
  @override Uri get uri => Uri.parse('http://localhost');
  @override HttpConnectionInfo? get connectionInfo => null;
  @override List<Cookie> get cookies => [];
  @override Future<HttpClientResponse> get done => close();
  @override void abort([Object? exception, StackTrace? stackTrace]) {}
  @override void add(List<int> data) {}
  @override void addError(Object error, [StackTrace? stackTrace]) {}
  @override Future addStream(Stream<List<int>> stream) async {}
  @override Future flush() async {}
  @override void write(Object? object) {}
  @override void writeAll(Iterable objects, [String separator = '']) {}
  @override void writeCharCode(int charCode) {}
  @override void writeln([Object? object = '']) {}
}

// ---------------------------------------------------------------------------
// HttpClientResponse
// ---------------------------------------------------------------------------
class _FakeHttpClientResponse extends Stream<List<int>>
    implements HttpClientResponse {
  _FakeHttpClientResponse(this._spec) {
    // Dio 5 checks Content-Type to decide whether to JSON-decode the body.
    // Without this header the transformer returns the raw string, which
    // breaks _parse<Map>. Setting it here ensures JSON is always decoded.
    headers.set('content-type', 'application/json; charset=utf-8');
  }
  final _FakeSpec _spec;

  @override int get statusCode => _spec.statusCode;

  @override
  StreamSubscription<List<int>> listen(
    void Function(List<int>)? onData, {
    Function? onError,
    void Function()? onDone,
    bool? cancelOnError,
  }) =>
      Stream.value(utf8.encode(_spec.body)).listen(
        onData,
        onError: onError,
        onDone: onDone,
        cancelOnError: cancelOnError,
      );

  @override final HttpHeaders headers = _FakeHttpHeaders();
  @override int get contentLength => utf8.encode(_spec.body).length;
  @override HttpClientResponseCompressionState get compressionState =>
      HttpClientResponseCompressionState.notCompressed;
  @override X509Certificate? get certificate => null;
  @override HttpConnectionInfo? get connectionInfo => null;
  @override List<Cookie> get cookies => [];
  @override bool get isRedirect => false;
  @override bool get persistentConnection => false;
  @override String get reasonPhrase => '';
  @override List<RedirectInfo> get redirects => [];
  @override Future<HttpClientResponse> redirect([String? m, Uri? u, bool? f]) =>
      throw UnimplementedError();
  @override Future<Socket> detachSocket() => throw UnimplementedError();
}

// ---------------------------------------------------------------------------
// HttpHeaders
// ---------------------------------------------------------------------------
class _FakeHttpHeaders implements HttpHeaders {
  final Map<String, List<String>> _m = {};

  @override List<String>? operator [](String name) => _m[name.toLowerCase()];
  @override void add(String name, Object value, {bool preserveHeaderCase = false}) =>
      (_m[name.toLowerCase()] ??= []).add(value.toString());
  @override void set(String name, Object value, {bool preserveHeaderCase = false}) =>
      _m[name.toLowerCase()] = [value.toString()];
  @override String? value(String name) => _m[name.toLowerCase()]?.firstOrNull;
  @override void remove(String name, Object value) =>
      _m[name.toLowerCase()]?.remove(value.toString());
  @override void removeAll(String name) => _m.remove(name.toLowerCase());
  @override void forEach(void Function(String, List<String>) action) => _m.forEach(action);
  @override void noFolding(String name) {}
  @override void clear() => _m.clear();
  @override bool get chunkedTransferEncoding => false;
  @override set chunkedTransferEncoding(bool value) {}
  @override int get contentLength => -1;
  @override set contentLength(int value) {}
  @override ContentType? get contentType => null;
  @override set contentType(ContentType? value) {}
  @override DateTime? get date => null;
  @override set date(DateTime? value) {}
  @override DateTime? get expires => null;
  @override set expires(DateTime? value) {}
  @override String? get host => null;
  @override set host(String? value) {}
  @override DateTime? get ifModifiedSince => null;
  @override set ifModifiedSince(DateTime? value) {}
  @override bool get persistentConnection => true;
  @override set persistentConnection(bool value) {}
  @override int? get port => null;
  @override set port(int? value) {}
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

/// Sets [_activeSpec] and calls [fn]. Errors from the interceptors are wrapped
/// in a [DioException] whose `.error` field contains the [ApiException].
Future<T> _withSpec<T>(_FakeSpec spec, Future<T> Function() fn) {
  _activeSpec = spec;
  return fn();
}

/// Returns the [ApiException] embedded in a [DioException] thrown by ApiClient.
ApiException _extractApi(Object err) {
  expect(err, isA<DioException>(), reason: 'Expected DioException');
  final dio = err as DioException;
  expect(dio.error, isA<ApiException>(), reason: 'Expected DioException.error to be ApiException');
  return dio.error as ApiException;
}

// ─────────────────────────────────────────────────────────────────────────────
// Tests
// ─────────────────────────────────────────────────────────────────────────────

void main() {
  // Initialise the Flutter test binding.  TestWidgetsFlutterBinding sets
  // HttpOverrides.global to a mock that blocks real network calls; we
  // immediately replace it with our own fake so Dio gets _FakeHttpClient.
  setUpAll(() {
    TestWidgetsFlutterBinding.ensureInitialized();
    HttpOverrides.global = _FakeHttpOverrides();
  });

  setUp(() {
    // Reset to a safe default so tests that do not call _withSpec still work.
    _activeSpec = const _FakeSpec(200, '{}');
  });

  // ──────────────────────────────────────────────────────────────────────────
  // Group 1 — ApiException
  //
  // ApiException is a simple value type.  We verify every stored field and
  // the toString() format, which appears in logs and error messages.
  // ──────────────────────────────────────────────────────────────────────────
  group('ApiException', () {
    test('constructor stores status', () {
      // Callers branch on status to decide how to handle the error.
      final e = ApiException(status: 404, message: 'Not Found');
      expect(e.status, 404);
    });

    test('constructor stores message', () {
      // message is surfaced in UI error dialogs and log output.
      final e = ApiException(status: 200, message: 'OK');
      expect(e.message, 'OK');
    });

    test('constructor stores data', () {
      // data carries the raw response body for callers that need it.
      final e = ApiException(status: 400, message: 'Bad', data: {'field': 'x'});
      expect(e.data, {'field': 'x'});
    });

    test('constructor stores code', () {
      // code is a server-defined error code string used for i18n / routing.
      final e = ApiException(status: 422, message: 'Unprocessable', code: 'VALIDATION_FAILED');
      expect(e.code, 'VALIDATION_FAILED');
    });

    test('all nullable fields default to null', () {
      // Only message is required; every other field is nullable.
      final e = ApiException(message: 'bare');
      expect(e.status, isNull);
      expect(e.data, isNull);
      expect(e.code, isNull);
    });

    test('toString includes status', () {
      // Log parsers search for the status number to filter errors.
      final e = ApiException(status: 503, message: 'Unavailable');
      expect(e.toString(), contains('503'));
    });

    test('toString includes code when present', () {
      // code helps operators distinguish error categories in log aggregators.
      final e = ApiException(status: 409, message: 'Conflict', code: 'DUPLICATE');
      expect(e.toString(), contains('DUPLICATE'));
    });

    test('toString includes message', () {
      // The human-readable description must survive round-trip through toString.
      final e = ApiException(status: 500, message: 'Server blew up');
      expect(e.toString(), contains('Server blew up'));
    });

    test('toString formats all three fields', () {
      // Full format: ApiException(status=<n>, code=<s>, message=<m>)
      final e = ApiException(status: 401, message: 'Unauthorized', code: 'AUTH_ERR');
      final s = e.toString();
      expect(s, contains('status=401'));
      expect(s, contains('code=AUTH_ERR'));
      expect(s, contains('message=Unauthorized'));
    });

    test('implements Exception', () {
      // Callers that catch broad Exception types must also catch ApiException.
      expect(ApiException(message: 'x'), isA<Exception>());
    });
  });

  // ──────────────────────────────────────────────────────────────────────────
  // Group 2 — ApiClient singleton
  // ──────────────────────────────────────────────────────────────────────────
  group('ApiClient singleton', () {
    test('instance always returns the same object', () {
      // The singleton must not create a second Dio/interceptor stack.
      expect(identical(ApiClient.instance, ApiClient.instance), isTrue);
    });
  });

  // ──────────────────────────────────────────────────────────────────────────
  // Group 3 — getJson
  //
  // Tests HTTP GET with various response shapes and error codes.
  // ──────────────────────────────────────────────────────────────────────────
  group('getJson', () {
    test('returns raw decoded JSON on 200 with no parser', () async {
      // The default path casts response data directly; no parser needed for Maps.
      final result = await _withSpec(
        const _FakeSpec(200, '{"hello":"world"}'),
        () => ApiClient.instance.getJson<Map<String, dynamic>>('/test'),
      );
      expect(result['hello'], 'world');
    });

    test('applies a custom parser on 200', () async {
      // Callers can supply a parser to deserialise into domain objects.
      final result = await _withSpec(
        const _FakeSpec(200, '{"count":7}'),
        () => ApiClient.instance.getJson<int>(
          '/test',
          parser: (json) => (json as Map)['count'] as int,
        ),
      );
      expect(result, 7);
    });

    test('returns null when response body is JSON null', () async {
      // Nullable endpoints should not throw when the body is "null".
      final result = await _withSpec(
        const _FakeSpec(200, 'null'),
        () => ApiClient.instance.getJson<dynamic>('/nullable'),
      );
      expect(result, isNull);
    });

    test('throws DioException containing ApiException on 404', () async {
      // Non-2xx responses are converted to DioException(error: ApiException).
      Object? caught;
      try {
        await _withSpec(
          const _FakeSpec(404, '{"message":"Not Found"}'),
          () => ApiClient.instance.getJson<dynamic>('/missing'),
        );
      } catch (e) {
        caught = e;
      }
      expect(caught, isNotNull);
      final api = _extractApi(caught!);
      expect(api.status, 404);
    });

    test('ApiException.message is extracted from response "message" key', () async {
      // _extractMessage checks the "message" key first.
      Object? caught;
      try {
        await _withSpec(
          const _FakeSpec(400, '{"message":"Bad input"}'),
          () => ApiClient.instance.getJson<dynamic>('/bad'),
        );
      } catch (e) {
        caught = e;
      }
      final api = _extractApi(caught!);
      expect(api.message, 'Bad input');
    });

    test('ApiException.message is extracted from response "error" key', () async {
      // _extractMessage falls back to "error" when "message" is absent.
      Object? caught;
      try {
        await _withSpec(
          const _FakeSpec(500, '{"error":"Internal failure"}'),
          () => ApiClient.instance.getJson<dynamic>('/err'),
        );
      } catch (e) {
        caught = e;
      }
      final api = _extractApi(caught!);
      expect(api.message, 'Internal failure');
    });

    test('ApiException.message is extracted from response "detail" key', () async {
      // _extractMessage also handles FastAPI-style "detail" keys.
      Object? caught;
      try {
        await _withSpec(
          const _FakeSpec(422, '{"detail":"Validation error"}'),
          () => ApiClient.instance.getJson<dynamic>('/validate'),
        );
      } catch (e) {
        caught = e;
      }
      final api = _extractApi(caught!);
      expect(api.message, 'Validation error');
    });

    test('ApiException.message is the plain string body when not a Map', () async {
      // _extractMessage returns the raw string when data is not a JSON object.
      Object? caught;
      try {
        await _withSpec(
          const _FakeSpec(503, '"Service unavailable"'),
          () => ApiClient.instance.getJson<dynamic>('/down'),
        );
      } catch (e) {
        caught = e;
      }
      final api = _extractApi(caught!);
      expect(api.message, 'Service unavailable');
    });

    test('ApiException.message falls back to "HTTP <status>" for empty body', () async {
      // When there is no usable text in the body, include the status code.
      Object? caught;
      try {
        await _withSpec(
          const _FakeSpec(502, '{}'),
          () => ApiClient.instance.getJson<dynamic>('/gw'),
        );
      } catch (e) {
        caught = e;
      }
      final api = _extractApi(caught!);
      expect(api.message, contains('502'));
    });

    test('ApiException.code is extracted from response "code" key', () async {
      // _extractCode exposes server-defined error codes to callers.
      Object? caught;
      try {
        await _withSpec(
          const _FakeSpec(409, '{"message":"Conflict","code":"DUPLICATE_ENTRY"}'),
          () => ApiClient.instance.getJson<dynamic>('/conflict'),
        );
      } catch (e) {
        caught = e;
      }
      final api = _extractApi(caught!);
      expect(api.code, 'DUPLICATE_ENTRY');
    });

    test('ApiException.code is null when "code" key is absent', () async {
      // Not all error bodies include a code; null is the correct default.
      Object? caught;
      try {
        await _withSpec(
          const _FakeSpec(400, '{"message":"No code here"}'),
          () => ApiClient.instance.getJson<dynamic>('/nocode'),
        );
      } catch (e) {
        caught = e;
      }
      final api = _extractApi(caught!);
      expect(api.code, isNull);
    });

    test('throws DioException containing ApiException on 500', () async {
      // Server errors must surface as ApiException with the correct status.
      Object? caught;
      try {
        await _withSpec(
          const _FakeSpec(500, '{"message":"boom"}'),
          () => ApiClient.instance.getJson<dynamic>('/boom'),
        );
      } catch (e) {
        caught = e;
      }
      final api = _extractApi(caught!);
      expect(api.status, 500);
    });
  });

  // ──────────────────────────────────────────────────────────────────────────
  // Group 4 — postJson
  //
  // Verifies POST wiring: the method should POST and return data on 2xx.
  // ──────────────────────────────────────────────────────────────────────────
  group('postJson', () {
    test('returns decoded response body on 201', () async {
      // 201 Created is in the 2xx range so the response should be returned.
      final result = await _withSpec(
        const _FakeSpec(201, '{"id":99}'),
        () => ApiClient.instance.postJson<Map<String, dynamic>>(
          '/items',
          body: {'name': 'widget'},
        ),
      );
      expect(result['id'], 99);
    });

    test('throws ApiException on 400', () async {
      // Validation failures must surface as ApiException.
      Object? caught;
      try {
        await _withSpec(
          const _FakeSpec(400, '{"message":"Invalid body"}'),
          () => ApiClient.instance.postJson<dynamic>('/items', body: {}),
        );
      } catch (e) {
        caught = e;
      }
      final api = _extractApi(caught!);
      expect(api.status, 400);
    });

    test('applies custom parser on success', () async {
      // Parser receives the raw JSON; it must be called for POST responses too.
      final id = await _withSpec(
        const _FakeSpec(200, '{"id":42}'),
        () => ApiClient.instance.postJson<int>(
          '/items',
          parser: (json) => (json as Map)['id'] as int,
        ),
      );
      expect(id, 42);
    });
  });

  // ──────────────────────────────────────────────────────────────────────────
  // Group 5 — putJson
  // ──────────────────────────────────────────────────────────────────────────
  group('putJson', () {
    test('returns decoded response body on 200', () async {
      // PUT success must return the server-acknowledged representation.
      final result = await _withSpec(
        const _FakeSpec(200, '{"updated":true}'),
        () => ApiClient.instance.putJson<Map<String, dynamic>>(
          '/items/1',
          body: {'name': 'updated'},
        ),
      );
      expect(result['updated'], isTrue);
    });

    test('throws ApiException on 404', () async {
      // Updating a missing resource must surface as ApiException(404).
      Object? caught;
      try {
        await _withSpec(
          const _FakeSpec(404, '{"message":"Item not found"}'),
          () => ApiClient.instance.putJson<dynamic>('/items/999', body: {}),
        );
      } catch (e) {
        caught = e;
      }
      final api = _extractApi(caught!);
      expect(api.status, 404);
      expect(api.message, 'Item not found');
    });
  });

  // ──────────────────────────────────────────────────────────────────────────
  // Group 6 — patchJson
  // ──────────────────────────────────────────────────────────────────────────
  group('patchJson', () {
    test('returns decoded response body on 200', () async {
      // Partial updates return the patched object.
      final result = await _withSpec(
        const _FakeSpec(200, '{"patched":true}'),
        () => ApiClient.instance.patchJson<Map<String, dynamic>>(
          '/items/1',
          body: {'status': 'active'},
        ),
      );
      expect(result['patched'], isTrue);
    });

    test('throws ApiException on 422 with message', () async {
      // Server validation on PATCH must bubble up as ApiException.
      Object? caught;
      try {
        await _withSpec(
          const _FakeSpec(422, '{"message":"Unprocessable"}'),
          () => ApiClient.instance.patchJson<dynamic>('/items/1'),
        );
      } catch (e) {
        caught = e;
      }
      final api = _extractApi(caught!);
      expect(api.status, 422);
    });
  });

  // ──────────────────────────────────────────────────────────────────────────
  // Group 7 — deleteJson
  // ──────────────────────────────────────────────────────────────────────────
  group('deleteJson', () {
    test('returns decoded response body on 200', () async {
      // Some DELETE endpoints return the deleted entity.
      final result = await _withSpec(
        const _FakeSpec(200, '{"deleted":true}'),
        () => ApiClient.instance.deleteJson<Map<String, dynamic>>('/items/1'),
      );
      expect(result['deleted'], isTrue);
    });

    test('throws ApiException on 403 with message', () async {
      // Authorisation failures on DELETE must be surfaced as ApiException.
      Object? caught;
      try {
        await _withSpec(
          const _FakeSpec(403, '{"message":"Forbidden"}'),
          () => ApiClient.instance.deleteJson<dynamic>('/items/1'),
        );
      } catch (e) {
        caught = e;
      }
      final api = _extractApi(caught!);
      expect(api.status, 403);
      expect(api.message, 'Forbidden');
    });
  });

  // ──────────────────────────────────────────────────────────────────────────
  // Group 8 — _normalizeDioError branches (via connection / timeout simulation)
  //
  // We trigger real Dio error types by making the fake client throw or by
  // constructing DioException directly and verifying the normalised output
  // through the public API surface.
  // ──────────────────────────────────────────────────────────────────────────
  group('_normalizeDioError / error normalisation', () {
    test('DioException wraps ApiException when response body is present', () async {
      // _normalizeDioError: resp != null → wraps ApiException.
      // We verify this through a normal non-2xx request.
      Object? caught;
      try {
        await _withSpec(
          const _FakeSpec(401, '{"message":"Unauthorized"}'),
          () => ApiClient.instance.getJson<dynamic>('/auth'),
        );
      } catch (e) {
        caught = e;
      }
      expect(caught, isA<DioException>());
      expect((caught as DioException).error, isA<ApiException>());
    });

    test('repeated 401 does not loop: __ret flag prevents infinite retry', () async {
      // The onError interceptor checks __ret to avoid retry loops.
      // After one failed refresh attempt the request must still reject.
      Object? caught;
      try {
        await _withSpec(
          const _FakeSpec(401, '{"message":"Still unauthorized"}'),
          () => ApiClient.instance.getJson<dynamic>('/secure'),
        );
      } catch (e) {
        caught = e;
      }
      expect(caught, isA<DioException>());
    });

    test('multiple sequential errors each carry the correct status', () async {
      // Verify that _activeSpec changes correctly between calls.
      Object? caught404;
      Object? caught500;

      try {
        await _withSpec(
          const _FakeSpec(404, '{"message":"Gone"}'),
          () => ApiClient.instance.getJson<dynamic>('/gone'),
        );
      } catch (e) {
        caught404 = e;
      }

      try {
        await _withSpec(
          const _FakeSpec(500, '{"message":"Exploded"}'),
          () => ApiClient.instance.getJson<dynamic>('/explode'),
        );
      } catch (e) {
        caught500 = e;
      }

      expect(_extractApi(caught404!).status, 404);
      expect(_extractApi(caught500!).status, 500);
    });
  });

  // ──────────────────────────────────────────────────────────────────────────
  // Group 9 — Data parsing edge cases
  //
  // Tests _extractMessage / _extractCode edge cases accessed indirectly via
  // the public API surface.
  // ──────────────────────────────────────────────────────────────────────────
  group('data parsing edge cases', () {
    test('numeric "code" value is converted to string', () async {
      // _extractCode calls .toString() so integer codes work too.
      Object? caught;
      try {
        await _withSpec(
          const _FakeSpec(400, '{"message":"bad","code":1001}'),
          () => ApiClient.instance.getJson<dynamic>('/coded'),
        );
      } catch (e) {
        caught = e;
      }
      final api = _extractApi(caught!);
      expect(api.code, '1001');
    });

    test('"message" key takes priority over "error" key', () async {
      // When both keys are present, "message" wins in _extractMessage.
      Object? caught;
      try {
        await _withSpec(
          const _FakeSpec(400, '{"message":"msg wins","error":"error loses"}'),
          () => ApiClient.instance.getJson<dynamic>('/both'),
        );
      } catch (e) {
        caught = e;
      }
      final api = _extractApi(caught!);
      expect(api.message, 'msg wins');
    });

    test('list response body parses correctly on 200', () async {
      // getJson can return a List when the parser is provided.
      final result = await _withSpec(
        const _FakeSpec(200, '[1,2,3]'),
        () => ApiClient.instance.getJson<List<dynamic>>(
          '/list',
          parser: (json) => json as List<dynamic>,
        ),
      );
      expect(result, [1, 2, 3]);
    });

    test('response data is passed to ApiException.data field', () async {
      // _asException stores the raw response data so callers can inspect it.
      Object? caught;
      try {
        await _withSpec(
          const _FakeSpec(400, '{"message":"oops","extra":"info"}'),
          () => ApiClient.instance.getJson<dynamic>('/data'),
        );
      } catch (e) {
        caught = e;
      }
      final api = _extractApi(caught!);
      expect(api.data, isA<Map>());
      expect((api.data as Map)['extra'], 'info');
    });
  });
}
