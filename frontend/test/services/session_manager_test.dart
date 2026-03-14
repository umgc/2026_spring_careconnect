import 'dart:async';

import 'package:care_connect_app/services/session_manager.dart';
import 'package:fake_async/fake_async.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    await SessionManager().clear();
  });

  // --------------- singleton ---------------

  test('SessionManager() returns the same instance on repeated calls', () {
    final a = SessionManager();
    final b = SessionManager();

    expect(identical(a, b), true);
  });

  // --------------- headers ---------------

  test('headers includes Content-Type by default', () {
    final manager = SessionManager();

    expect(manager.headers['Content-Type'], 'application/json');
  });

  test('headers does not include cookie when session is null', () {
    final manager = SessionManager();

    expect(manager.headers.containsKey('cookie'), false);
  });

  test('headers does not include cookie when restored value is empty string', () async {
    final manager = SessionManager();
    SharedPreferences.setMockInitialValues({'session_cookie': ''});
    await manager.restoreSession();

    expect(manager.headers.containsKey('cookie'), false);
  });

  // --------------- updateCookies ---------------

  test('updateCookies extracts SESSION cookie from set-cookie header', () async {
    final manager = SessionManager();
    final response = http.Response(
      '',
      200,
      headers: {'set-cookie': 'SESSION=xyz789'},
    );

    await manager.updateCookies(response);

    expect(manager.headers['cookie'], 'SESSION=xyz789');
  });

  test('updateCookies persists cookie to SharedPreferences', () async {
    final manager = SessionManager();
    final response = http.Response(
      '',
      200,
      headers: {'set-cookie': 'SESSION=persisted123; Path=/'},
    );

    await manager.updateCookies(response);

    final prefs = await SharedPreferences.getInstance();
    expect(prefs.getString('session_cookie'), 'SESSION=persisted123');
  });

  test('updateCookies ignores response with no set-cookie header', () async {
    final manager = SessionManager();
    // Set an initial cookie so we can verify it is NOT overwritten.
    final initial = http.Response(
      '',
      200,
      headers: {'set-cookie': 'SESSION=initial'},
    );
    await manager.updateCookies(initial);

    // Response without set-cookie header should leave the cookie unchanged.
    final noHeader = http.Response('', 200);
    await manager.updateCookies(noHeader);

    expect(manager.headers['cookie'], 'SESSION=initial');
  });

  test('updateCookies ignores set-cookie header without SESSION=', () async {
    final manager = SessionManager();
    final response = http.Response(
      '',
      200,
      headers: {'set-cookie': 'OTHER=value; Path=/'},
    );

    await manager.updateCookies(response);

    expect(manager.headers.containsKey('cookie'), false);
  });

  test('updateCookies extracts SESSION from multi-part set-cookie string', () async {
    final manager = SessionManager();
    final response = http.Response(
      '',
      200,
      headers: {'set-cookie': 'SESSION=multi456; Path=/; HttpOnly; SameSite=Strict'},
    );

    await manager.updateCookies(response);

    expect(manager.headers['cookie'], 'SESSION=multi456');
  });

  test('updateCookies overwrites previously stored cookie', () async {
    final manager = SessionManager();

    final first = http.Response(
      '',
      200,
      headers: {'set-cookie': 'SESSION=first'},
    );
    await manager.updateCookies(first);
    expect(manager.headers['cookie'], 'SESSION=first');

    final second = http.Response(
      '',
      200,
      headers: {'set-cookie': 'SESSION=second'},
    );
    await manager.updateCookies(second);
    expect(manager.headers['cookie'], 'SESSION=second');

    final prefs = await SharedPreferences.getInstance();
    expect(prefs.getString('session_cookie'), 'SESSION=second');
  });

  // --------------- restoreSession ---------------

  test('restoreSession loads cookie from SharedPreferences', () async {
    final manager = SessionManager();
    SharedPreferences.setMockInitialValues({
      'session_cookie': 'SESSION=restored999',
    });

    await manager.restoreSession();

    expect(manager.headers['cookie'], 'SESSION=restored999');
  });

  test('restoreSession sets cookie to null when nothing is stored', () async {
    final manager = SessionManager();

    await manager.restoreSession();

    expect(manager.headers.containsKey('cookie'), false);
  });

  test('restoreSession populates headers with restored cookie', () async {
    final manager = SessionManager();
    SharedPreferences.setMockInitialValues({
      'session_cookie': 'SESSION=headerCheck',
    });

    await manager.restoreSession();

    final h = manager.headers;
    expect(h['Content-Type'], 'application/json');
    expect(h['cookie'], 'SESSION=headerCheck');
  });

  // --------------- clear ---------------

  test('clear removes session cookie from memory and SharedPreferences', () async {
    final manager = SessionManager();
    final response = http.Response(
      '',
      200,
      headers: {'set-cookie': 'SESSION=toBeCleared'},
    );
    await manager.updateCookies(response);

    await manager.clear();

    expect(manager.headers.containsKey('cookie'), false);

    final prefs = await SharedPreferences.getInstance();
    expect(prefs.getString('session_cookie'), isNull);
  });

  test('clear is safe when no session exists', () async {
    final manager = SessionManager();

    // Should not throw.
    await manager.clear();

    expect(manager.headers.containsKey('cookie'), false);
  });

  test('clear causes headers to exclude cookie', () async {
    final manager = SessionManager();
    final response = http.Response(
      '',
      200,
      headers: {'set-cookie': 'SESSION=willClear'},
    );
    await manager.updateCookies(response);
    expect(manager.headers.containsKey('cookie'), true);

    await manager.clear();

    expect(manager.headers.containsKey('cookie'), false);
    expect(manager.headers.length, 1);
    expect(manager.headers['Content-Type'], 'application/json');
  });

  // --------------- lifecycle ---------------

  test('full lifecycle: updateCookies -> clear -> restoreSession returns null cookie', () async {
    final manager = SessionManager();
    final response = http.Response(
      '',
      200,
      headers: {'set-cookie': 'SESSION=lifecycle'},
    );

    await manager.updateCookies(response);
    expect(manager.headers['cookie'], 'SESSION=lifecycle');

    await manager.clear();

    await manager.restoreSession();
    expect(manager.headers.containsKey('cookie'), false);
  });

  test('full lifecycle: updateCookies -> restoreSession preserves cookie across restore', () async {
    final manager = SessionManager();
    final response = http.Response(
      '',
      200,
      headers: {'set-cookie': 'SESSION=persist'},
    );

    await manager.updateCookies(response);
    // Simulate app restart: clear in-memory state, then restore from prefs.
    // We cannot reset the singleton's private field directly, so we verify
    // that SharedPreferences still holds the value after a restore call.
    await manager.restoreSession();

    expect(manager.headers['cookie'], 'SESSION=persist');
  });

  // --------------- get ---------------

  test('get sends GET request to the correct URL', () async {
    final manager = SessionManager();
    http.Request? captured;

    final result = await http.runWithClient(
      () => manager.get('https://example.com/api/data'),
      () => MockClient((req) async {
        captured = req;
        return http.Response('{"ok":true}', 200);
      }),
    );

    expect(captured, isNotNull);
    expect(captured!.method, 'GET');
    expect(captured!.url.toString(), 'https://example.com/api/data');
    expect(result.statusCode, 200);
    expect(result.body, '{"ok":true}');
  });

  test('get includes session headers in request', () async {
    final manager = SessionManager();
    await manager.updateCookies(http.Response(
      '',
      200,
      headers: {'set-cookie': 'SESSION=forGet'},
    ));
    http.Request? captured;

    await http.runWithClient(
      () => manager.get('https://example.com/api'),
      () => MockClient((req) async {
        captured = req;
        return http.Response('', 200);
      }),
    );

    expect(captured!.headers['Content-Type'], 'application/json');
    expect(captured!.headers['cookie'], 'SESSION=forGet');
  });

  // --------------- post ---------------

  test('post sends POST request with body', () async {
    final manager = SessionManager();
    http.Request? captured;

    final result = await http.runWithClient(
      () => manager.post('https://example.com/api/create', body: '{"name":"test"}'),
      () => MockClient((req) async {
        captured = req;
        return http.Response('{"id":1}', 201);
      }),
    );

    expect(captured, isNotNull);
    expect(captured!.method, 'POST');
    expect(captured!.url.toString(), 'https://example.com/api/create');
    expect(captured!.body, '{"name":"test"}');
    expect(result.statusCode, 201);
  });

  test('post includes session headers in request', () async {
    final manager = SessionManager();
    await manager.updateCookies(http.Response(
      '',
      200,
      headers: {'set-cookie': 'SESSION=forPost'},
    ));
    http.Request? captured;

    await http.runWithClient(
      () => manager.post('https://example.com/api'),
      () => MockClient((req) async {
        captured = req;
        return http.Response('', 200);
      }),
    );

    expect(captured!.headers['Content-Type'], 'application/json');
    expect(captured!.headers['cookie'], 'SESSION=forPost');
  });

  // --------------- put ---------------

  test('put sends PUT request with body', () async {
    final manager = SessionManager();
    http.Request? captured;

    final result = await http.runWithClient(
      () => manager.put('https://example.com/api/update', body: '{"name":"updated"}'),
      () => MockClient((req) async {
        captured = req;
        return http.Response('{"ok":true}', 200);
      }),
    );

    expect(captured, isNotNull);
    expect(captured!.method, 'PUT');
    expect(captured!.url.toString(), 'https://example.com/api/update');
    expect(captured!.body, '{"name":"updated"}');
    expect(result.statusCode, 200);
  });

  test('put includes session headers in request', () async {
    final manager = SessionManager();
    await manager.updateCookies(http.Response(
      '',
      200,
      headers: {'set-cookie': 'SESSION=forPut'},
    ));
    http.Request? captured;

    await http.runWithClient(
      () => manager.put('https://example.com/api'),
      () => MockClient((req) async {
        captured = req;
        return http.Response('', 200);
      }),
    );

    expect(captured!.headers['Content-Type'], 'application/json');
    expect(captured!.headers['cookie'], 'SESSION=forPut');
  });

  // --------------- timeouts ---------------

  test('get returns 408 when server does not respond within timeout', () {
    fakeAsync((async) {
      final manager = SessionManager();
      http.Response? result;

      http.runWithClient(
        () {
          manager.get('https://example.com/slow').then((r) => result = r);
        },
        () => MockClient((req) => Completer<http.Response>().future),
      );

      async.elapse(const Duration(seconds: 181));

      expect(result, isNotNull);
      expect(result!.statusCode, 408);
      expect(result!.body, '{"error": "Request timeout"}');
    });
  });

  test('post returns 408 when server does not respond within timeout', () {
    fakeAsync((async) {
      final manager = SessionManager();
      http.Response? result;

      http.runWithClient(
        () {
          manager.post('https://example.com/slow').then((r) => result = r);
        },
        () => MockClient((req) => Completer<http.Response>().future),
      );

      async.elapse(const Duration(seconds: 181));

      expect(result, isNotNull);
      expect(result!.statusCode, 408);
      expect(result!.body, '{"error": "Request timeout"}');
    });
  });

  test('put returns 408 when server does not respond within timeout', () {
    fakeAsync((async) {
      final manager = SessionManager();
      http.Response? result;

      http.runWithClient(
        () {
          manager.put('https://example.com/slow').then((r) => result = r);
        },
        () => MockClient((req) => Completer<http.Response>().future),
      );

      async.elapse(const Duration(seconds: 181));

      expect(result, isNotNull);
      expect(result!.statusCode, 408);
      expect(result!.body, '{"error": "Request timeout"}');
    });
  });
}
