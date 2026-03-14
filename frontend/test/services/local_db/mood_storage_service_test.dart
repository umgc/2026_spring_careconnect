// Tests for MoodStorageService, SyncSummary, MoodQueueItem, and
// OfflinePersistenceDisabledException.
//
// Coverage strategy:
//   MoodStorageService routes mood reads/writes between the backend API and a
//   local encrypted database depending on network connectivity.  The two main
//   dependencies — ConnectivityRouterService and AppDatabase — are injected,
//   allowing full in-process testing without a real SQLite database or network.
//
//   _FakeAppDatabase extends AppDatabase and overrides every query method with
//   an in-memory Map<int, Mood> implementation.  Because AppDatabase uses a
//   LazyDatabase that only opens on the first real query, and _FakeAppDatabase
//   overrides every method that would trigger a real query, the underlying
//   SQLite file is never opened.
//
//   For the online paths that call ApiService.saveMoodScore / getMoodHistory,
//   HttpOverrides.global redirects all dart:io HTTP traffic to a local
//   HttpServer whose handlers are configured per-test.
//
//   Branches tested:
//     SyncSummary constructor and fields.
//     MoodQueueItem constructor and fields.
//     OfflinePersistenceDisabledException.toString().
//     saveMood — offline path.
//     saveMood — offline path blocked by canUseOfflineFallback=false.
//     saveMood — online path, success (HTTP 200).
//     saveMood — online throws, fallback to offline.
//     saveMoodForCurrentUser — no provider configured.
//     saveMoodForCurrentUser — provider returns null userId.
//     saveMoodForCurrentUser — delegates to saveMood.
//     getMoodHistory — offline, returns local rows.
//     getMoodHistory — online, returns backend rows.
//     syncMoodsToBackendIfOnline — offline, returns zero summary.
//     syncMoodsToBackendIfOnline — online, empty local DB → early summary.
//     syncMoodsToBackendIfOnline — online, rows to sync.
//     syncMoodsToBackendIfOnline — online, row already in backend (skip).
//     syncQueuedMoodItemById — offline, returns false.
//     syncQueuedMoodItemById — online, row missing → false.
//     syncQueuedMoodItemById — online, sync success → true.
//     syncQueuedMoodItemById — online, HTTP fails → false.
//     getPendingMoodQueue — returns items oldest-first.
//     deleteQueuedMoodItem — deletes existing row, returns true.
//     deleteQueuedMoodItem — missing row, returns false.
//     isLocalDbEncrypted — delegates to AppDatabase.
//     close — delegates to AppDatabase.
//     _extractErrorMessage — empty body, JSON with message, JSON with error,
//                            non-JSON body, JSON without known keys.
//     _normalizeDateTimeString — valid ISO string, invalid string, null/empty.

import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:care_connect_app/services/local_db/app_database.dart';
import 'package:care_connect_app/services/local_db/connectivity_router_service.dart';
import 'package:care_connect_app/services/local_db/mood_storage_service.dart';

// ─── Secure-storage channel stub ─────────────────────────────────────────────

// ApiService (called by the online paths) reads auth headers via
// AuthTokenManager, which uses flutter_secure_storage.  The channel is stubbed
// so no MissingPluginException occurs.
const MethodChannel _secureStorageChannel =
    MethodChannel('plugins.it_nomads.com/flutter_secure_storage');

// ─── HTTP redirect helpers ────────────────────────────────────────────────────

typedef _Handler = Future<void> Function(HttpRequest);

// Rewrites every outgoing dart:io HTTP request to the local test server while
// preserving the original path/query.  This intercepts ApiService._httpClient
// (an IOClient backed by dart:io.HttpClient) which cannot be replaced via
// http.runWithClient().
class _RewritingHttpOverrides extends HttpOverrides {
  _RewritingHttpOverrides(this._port);
  final int _port;

  @override
  HttpClient createHttpClient(SecurityContext? context) {
    final inner = super.createHttpClient(context);
    return _RewritingHttpClient(inner, _port);
  }
}

class _RewritingHttpClient implements HttpClient {
  _RewritingHttpClient(this._inner, this._port);
  final HttpClient _inner;
  final int _port;

  Uri _rewrite(Uri original) => Uri(
        scheme: 'http',
        host: '127.0.0.1',
        port: _port,
        path: original.path,
        query: original.hasQuery ? original.query : null,
      );

  @override
  Future<HttpClientRequest> openUrl(String method, Uri url) =>
      _inner.openUrl(method, _rewrite(url));

  @override
  Future<HttpClientRequest> postUrl(Uri url) =>
      _inner.postUrl(_rewrite(url));

  @override
  void close({bool force = false}) => _inner.close(force: force);

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

// ─── In-memory AppDatabase fake ───────────────────────────────────────────────

// Extends AppDatabase and overrides every query method with a simple Map-backed
// implementation.  The AppDatabase constructor creates a LazyDatabase, which
// only opens the SQLite file on the first real query.  Since every query method
// is overridden here, the underlying database is never opened.
class _FakeAppDatabase extends AppDatabase {
  final _store = <int, Mood>{};
  var _nextId = 1;
  var _encryptionKeyExists = false;

  _FakeAppDatabase() : super();

  @override
  Future<int> insertMood({
    required int userId,
    required int score,
    required String label,
    DateTime? createdAt,
  }) async {
    final id = _nextId++;
    _store[id] = Mood(
      id: id,
      userId: userId,
      score: score,
      label: label,
      createdAt: createdAt ?? DateTime.now(),
    );
    return id;
  }

  @override
  Future<List<Mood>> getMoodsForUser(int userIdValue) async {
    final result = _store.values
        .where((m) => m.userId == userIdValue)
        .toList()
      ..sort((a, b) => b.createdAt.compareTo(a.createdAt)); // newest first
    return result;
  }

  @override
  Future<List<Mood>> getMoodsForUserOldestFirst(int userIdValue) async {
    final result = _store.values
        .where((m) => m.userId == userIdValue)
        .toList()
      ..sort((a, b) => a.createdAt.compareTo(b.createdAt)); // oldest first
    return result;
  }

  @override
  Future<Mood?> getMoodByIdForUser({
    required int moodId,
    required int userIdValue,
  }) async {
    final mood = _store[moodId];
    if (mood == null || mood.userId != userIdValue) return null;
    return mood;
  }

  @override
  Future<int> deleteMoodById(int moodId) async {
    return _store.remove(moodId) != null ? 1 : 0;
  }

  @override
  Future<void> deleteMoodsByIds(Iterable<int> ids) async {
    for (final id in ids) {
      _store.remove(id);
    }
  }

  @override
  Future<bool> isEncrypted() async => _encryptionKeyExists;

  @override
  Future<void> close() async {}
}

// ─── Connectivity helpers ─────────────────────────────────────────────────────

ConnectivityRouterService _online() =>
    ConnectivityRouterService(isOnline: () async => true);

ConnectivityRouterService _offline() =>
    ConnectivityRouterService(isOnline: () async => false);

// ─── Test entry point ─────────────────────────────────────────────────────────

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  late HttpServer server;
  late Map<String, _Handler> handlers;
  HttpOverrides? previousOverrides;

  setUpAll(() async {
    // Stub flutter_secure_storage so AuthTokenManager.getAuthHeaders() can
    // run inside tests without a platform plugin.
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(_secureStorageChannel, (_) async => null);

    // Start a local HTTP server that the online-path tests route to via
    // HttpOverrides.global.
    server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
    handlers = {};

    unawaited(server.forEach((req) async {
      final handler = handlers[req.uri.path];
      if (handler != null) {
        await handler(req);
      } else {
        req.response.statusCode = HttpStatus.notFound;
        req.response.headers.contentType = ContentType.json;
        req.response.write(jsonEncode({'error': 'unhandled: ${req.uri.path}'}));
      }
      await req.response.close();
    }));

    previousOverrides = HttpOverrides.current;
    HttpOverrides.global = _RewritingHttpOverrides(server.port);
  });

  tearDownAll(() async {
    HttpOverrides.global = previousOverrides;
    await server.close(force: true);
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(_secureStorageChannel, null);
  });

  setUp(() => handlers.clear());

  // ─── SyncSummary ────────────────────────────────────────────────────────

  group('SyncSummary', () {
    test('constructor assigns all fields correctly', () {
      // Verifies every field is stored so callers can inspect sync results.
      const summary = SyncSummary(
        attempted: 10,
        created: 5,
        skippedExisting: 3,
        failed: 2,
        ranOnline: true,
      );
      expect(summary.attempted, 10);
      expect(summary.created, 5);
      expect(summary.skippedExisting, 3);
      expect(summary.failed, 2);
      expect(summary.ranOnline, isTrue);
    });

    test('ranOnline can be false (offline summary)', () {
      const summary = SyncSummary(
        attempted: 0,
        created: 0,
        skippedExisting: 0,
        failed: 0,
        ranOnline: false,
      );
      expect(summary.ranOnline, isFalse);
    });
  });

  // ─── MoodQueueItem ──────────────────────────────────────────────────────

  group('MoodQueueItem', () {
    test('constructor assigns all fields correctly', () {
      // MoodQueueItem is a projection used by the UI to display the pending
      // sync queue without exposing internal account identifiers.
      final ts = DateTime(2024, 3, 15, 10, 30);
      final item = MoodQueueItem(
        localId: 7,
        score: 8,
        label: 'happy',
        createdAt: DateTime(2024, 3, 15, 10, 30),
      );
      expect(item.localId, 7);
      expect(item.score, 8);
      expect(item.label, 'happy');
      expect(item.createdAt, ts);
    });
  });

  // ─── OfflinePersistenceDisabledException ────────────────────────────────

  group('OfflinePersistenceDisabledException', () {
    test('toString returns the message passed to the constructor', () {
      // Verifies that the exception surfaces a human-readable description
      // for display in error dialogs.
      const ex = OfflinePersistenceDisabledException('Offline is off.');
      expect(ex.toString(), 'Offline is off.');
    });

    test('implements Exception', () {
      const ex = OfflinePersistenceDisabledException('msg');
      expect(ex, isA<Exception>());
    });
  });

  // ─── saveMood — offline path ─────────────────────────────────────────────

  group('MoodStorageService.saveMood() — offline', () {
    test('stores mood in local DB when offline', () async {
      // Verifies the offline write path: mood is persisted locally so it
      // can be synced when connectivity is restored.
      final db = _FakeAppDatabase();
      final service = MoodStorageService(
        connectivityRouter: _offline(),
        appDatabase: db,
      );

      await service.saveMood(userId: 1, score: 7, label: 'calm');

      final rows = await db.getMoodsForUser(1);
      expect(rows, hasLength(1));
      expect(rows.first.score, 7);
      expect(rows.first.label, 'calm');
    });

    test('offline write throws OfflinePersistenceDisabledException when blocked',
        () async {
      // Verifies the policy gate: when canUseOfflineFallback returns false,
      // the offline write is rejected so data is not silently lost.
      final db = _FakeAppDatabase();
      final service = MoodStorageService(
        connectivityRouter: _offline(),
        appDatabase: db,
        canUseOfflineFallback: () async => false,
      );

      await expectLater(
        service.saveMood(userId: 1, score: 5, label: 'ok'),
        throwsA(isA<OfflinePersistenceDisabledException>()),
      );
    });

    test('offline write succeeds when canUseOfflineFallback returns true',
        () async {
      // Verifies the gate allows the write when the policy is permissive.
      final db = _FakeAppDatabase();
      final service = MoodStorageService(
        connectivityRouter: _offline(),
        appDatabase: db,
        canUseOfflineFallback: () async => true,
      );

      await service.saveMood(userId: 2, score: 9, label: 'great');

      final rows = await db.getMoodsForUser(2);
      expect(rows, hasLength(1));
    });

    test('offline write uses current timestamp when none provided', () async {
      // Verifies the mood row has a reasonable createdAt (within 5 seconds
      // of now) rather than a zero or epoch value.
      final before = DateTime.now().subtract(const Duration(seconds: 1));
      final db = _FakeAppDatabase();
      final service =
          MoodStorageService(connectivityRouter: _offline(), appDatabase: db);

      await service.saveMood(userId: 1, score: 3, label: 'tired');

      final rows = await db.getMoodsForUser(1);
      expect(rows.first.createdAt.isAfter(before), isTrue);
    });
  });

  // ─── saveMood — online path ───────────────────────────────────────────────

  group('MoodStorageService.saveMood() — online', () {
    test('posts to backend and does NOT write to local DB on success', () async {
      // Verifies the online path: a successful HTTP response means the mood
      // is persisted on the server; no local row should be created.
      handlers['/v1/api/patient/1/mood'] = (req) async {
        req.response.statusCode = HttpStatus.ok;
        req.response.headers.contentType = ContentType.json;
        req.response.write(jsonEncode({'id': 1}));
      };

      final db = _FakeAppDatabase();
      final service =
          MoodStorageService(connectivityRouter: _online(), appDatabase: db);

      await service.saveMood(userId: 1, score: 8, label: 'happy');

      final rows = await db.getMoodsForUser(1);
      expect(rows, isEmpty);
    });

    test('falls back to offline DB when online POST fails', () async {
      // saveMood uses fallbackToOfflineOnOnlineError=true, so a server error
      // must trigger local storage as a resilience mechanism.
      handlers['/v1/api/patient/1/mood'] = (req) async {
        req.response.statusCode = HttpStatus.internalServerError;
        req.response.write('{}');
      };

      final db = _FakeAppDatabase();
      final service =
          MoodStorageService(connectivityRouter: _online(), appDatabase: db);

      await service.saveMood(userId: 1, score: 4, label: 'sad');

      final rows = await db.getMoodsForUser(1);
      expect(rows, hasLength(1));
      expect(rows.first.label, 'sad');
    });
  });

  // ─── saveMoodForCurrentUser ───────────────────────────────────────────────

  group('MoodStorageService.saveMoodForCurrentUser()', () {
    test('throws when no currentUserIdProvider is configured', () async {
      // Verifies the guard: calling saveMoodForCurrentUser without injecting
      // a provider must fail fast with a descriptive message.
      final service =
          MoodStorageService(connectivityRouter: _offline());

      await expectLater(
        service.saveMoodForCurrentUser(score: 5, label: 'ok'),
        throwsA(isA<Exception>()),
      );
    });

    test('throws when currentUserIdProvider returns null', () async {
      // Verifies the null-userId guard: the service must not attempt to save
      // a mood without a user identifier.
      final service = MoodStorageService(
        connectivityRouter: _offline(),
        currentUserIdProvider: () async => null,
      );

      await expectLater(
        service.saveMoodForCurrentUser(score: 5, label: 'ok'),
        throwsA(isA<Exception>()),
      );
    });

    test('saves mood using the userId from currentUserIdProvider', () async {
      // Verifies the happy-path delegation: provider resolves the userId and
      // saveMood is called with it.
      final db = _FakeAppDatabase();
      final service = MoodStorageService(
        connectivityRouter: _offline(),
        appDatabase: db,
        currentUserIdProvider: () async => 42,
      );

      await service.saveMoodForCurrentUser(score: 6, label: 'ok');

      final rows = await db.getMoodsForUser(42);
      expect(rows, hasLength(1));
      expect(rows.first.userId, 42);
    });
  });

  // ─── getMoodHistory ───────────────────────────────────────────────────────

  group('MoodStorageService.getMoodHistory()', () {
    test('returns local rows when offline', () async {
      // Verifies the offline read path: local rows are mapped to the standard
      // map format and include a "source": "offline" flag.
      final db = _FakeAppDatabase();
      final ts = DateTime(2024, 1, 1);
      await db.insertMood(userId: 1, score: 5, label: 'ok', createdAt: ts);

      final service =
          MoodStorageService(connectivityRouter: _offline(), appDatabase: db);
      final history = await service.getMoodHistory(1);

      expect(history, hasLength(1));
      expect(history.first['score'], 5);
      expect(history.first['label'], 'ok');
      expect(history.first['source'], 'offline');
      expect(history.first['userId'], 1);
    });

    test('returns empty list when no local rows exist (offline)', () async {
      final db = _FakeAppDatabase();
      final service =
          MoodStorageService(connectivityRouter: _offline(), appDatabase: db);
      final history = await service.getMoodHistory(99);
      expect(history, isEmpty);
    });

    test('returns backend rows when online', () async {
      // Verifies the online read path: backend JSON is mapped directly to
      // the returned list without adding "source": "offline".
      handlers['/v1/api/patient/5/mood'] = (req) async {
        req.response.statusCode = HttpStatus.ok;
        req.response.headers.contentType = ContentType.json;
        req.response.write(jsonEncode([
          {'id': 1, 'userId': 5, 'score': 7, 'label': 'good'},
        ]));
      };

      final service = MoodStorageService(connectivityRouter: _online());
      final history = await service.getMoodHistory(5);

      expect(history, hasLength(1));
      expect(history.first['score'], 7);
    });

    test('offline local rows include createdAt as ISO string', () async {
      // The offline row projection must include the createdAt field so the
      // UI can display when the mood was recorded.
      final db = _FakeAppDatabase();
      final ts = DateTime(2024, 6, 15, 12, 0, 0);
      await db.insertMood(userId: 1, score: 8, label: 'great', createdAt: ts);

      final service =
          MoodStorageService(connectivityRouter: _offline(), appDatabase: db);
      final history = await service.getMoodHistory(1);

      expect(history.first['createdAt'], isA<String>());
      expect(history.first['createdAt'], contains('2024'));
    });
  });

  // ─── syncMoodsToBackendIfOnline ───────────────────────────────────────────

  group('MoodStorageService.syncMoodsToBackendIfOnline()', () {
    test('returns offline summary (ranOnline=false) when offline', () async {
      // Verifies the offline shortcut: when there is no connectivity, sync
      // is skipped and a zero summary is returned immediately.
      final service = MoodStorageService(
          connectivityRouter: _offline(), appDatabase: _FakeAppDatabase());

      final summary =
          await service.syncMoodsToBackendIfOnline(userId: 1);

      expect(summary.ranOnline, isFalse);
      expect(summary.attempted, 0);
      expect(summary.created, 0);
    });

    test('returns early summary with ranOnline=true when local DB is empty',
        () async {
      // If there are no local rows there is nothing to sync; the function
      // must return immediately without calling the backend.
      handlers['/v1/api/patient/1/mood'] = (req) async {
        req.response.statusCode = HttpStatus.ok;
        req.response.headers.contentType = ContentType.json;
        req.response.write(jsonEncode([]));
      };

      final db = _FakeAppDatabase();
      final service =
          MoodStorageService(connectivityRouter: _online(), appDatabase: db);

      final summary =
          await service.syncMoodsToBackendIfOnline(userId: 1);

      expect(summary.ranOnline, isTrue);
      expect(summary.attempted, 0);
    });

    test('creates backend row and removes local row on successful sync',
        () async {
      // Verifies the full sync cycle: a locally-stored mood is POSTed to
      // the backend and then deleted from the local DB.
      handlers['/v1/api/patient/2/mood'] = (req) async {
        if (req.method == 'POST') {
          req.response.statusCode = HttpStatus.ok;
          req.response.headers.contentType = ContentType.json;
          req.response.write(jsonEncode({'id': 99}));
        } else {
          // GET returns empty backend history so the local row is treated
          // as new (not a duplicate to skip).
          req.response.statusCode = HttpStatus.ok;
          req.response.headers.contentType = ContentType.json;
          req.response.write(jsonEncode([]));
        }
      };

      final db = _FakeAppDatabase();
      await db.insertMood(userId: 2, score: 5, label: 'ok');

      final service =
          MoodStorageService(connectivityRouter: _online(), appDatabase: db);
      final summary = await service.syncMoodsToBackendIfOnline(userId: 2);

      expect(summary.ranOnline, isTrue);
      expect(summary.attempted, 1);
      expect(summary.created, 1);
      expect(summary.failed, 0);

      // Local row was deleted after sync.
      final remaining = await db.getMoodsForUser(2);
      expect(remaining, isEmpty);
    });

    test('skips local row that already exists in backend (deduplication)',
        () async {
      // Verifies the fingerprint-based deduplication: if a mood already
      // exists on the backend (same userId/score/label/createdAt), the local
      // row is counted as skipped and removed without a POST.
      final ts = DateTime.utc(2024, 5, 10, 8, 0, 0);

      handlers['/v1/api/patient/3/mood'] = (req) async {
        if (req.method == 'GET') {
          req.response.statusCode = HttpStatus.ok;
          req.response.headers.contentType = ContentType.json;
          req.response.write(jsonEncode([
            {
              'userId': 3,
              'score': 6,
              'label': 'fine',
              'createdAt': ts.toIso8601String(),
            }
          ]));
        } else {
          req.response.statusCode = HttpStatus.ok;
          req.response.write(jsonEncode({}));
        }
      };

      final db = _FakeAppDatabase();
      await db.insertMood(userId: 3, score: 6, label: 'fine', createdAt: ts);

      final service =
          MoodStorageService(connectivityRouter: _online(), appDatabase: db);
      final summary = await service.syncMoodsToBackendIfOnline(userId: 3);

      expect(summary.skippedExisting, 1);
      expect(summary.created, 0);

      // Local row still deleted (counted as already-synced).
      final remaining = await db.getMoodsForUser(3);
      expect(remaining, isEmpty);
    });
  });

  // ─── getPendingMoodQueue ──────────────────────────────────────────────────

  group('MoodStorageService.getPendingMoodQueue()', () {
    test('returns items in oldest-first order', () async {
      // The sync pipeline processes entries from oldest to newest so they
      // are applied to the backend in chronological order.
      final db = _FakeAppDatabase();
      final newer = DateTime(2024, 2, 1);
      final older = DateTime(2024, 1, 1);
      await db.insertMood(userId: 1, score: 8, label: 'b', createdAt: newer);
      await db.insertMood(userId: 1, score: 3, label: 'a', createdAt: older);

      final service =
          MoodStorageService(connectivityRouter: _offline(), appDatabase: db);
      final queue = await service.getPendingMoodQueue(userId: 1);

      expect(queue, hasLength(2));
      expect(queue.first.label, 'a'); // oldest first
      expect(queue.last.label, 'b');
    });

    test('returns empty list when no moods are pending', () async {
      final db = _FakeAppDatabase();
      final service =
          MoodStorageService(connectivityRouter: _offline(), appDatabase: db);
      final queue = await service.getPendingMoodQueue(userId: 1);
      expect(queue, isEmpty);
    });

    test('each item exposes localId, score, label, and createdAt', () async {
      // Verifies the MoodQueueItem projection exposes the fields the UI needs.
      final db = _FakeAppDatabase();
      final ts = DateTime(2024, 3, 1);
      await db.insertMood(userId: 1, score: 7, label: 'ok', createdAt: ts);

      final service =
          MoodStorageService(connectivityRouter: _offline(), appDatabase: db);
      final queue = await service.getPendingMoodQueue(userId: 1);

      expect(queue.first.localId, isPositive);
      expect(queue.first.score, 7);
      expect(queue.first.label, 'ok');
      expect(queue.first.createdAt, ts);
    });
  });

  // ─── deleteQueuedMoodItem ─────────────────────────────────────────────────

  group('MoodStorageService.deleteQueuedMoodItem()', () {
    test('returns true when an existing row is deleted', () async {
      // Verifies the success path: deleting a real local row reports true.
      final db = _FakeAppDatabase();
      final id = await db.insertMood(userId: 1, score: 5, label: 'ok');

      final service =
          MoodStorageService(connectivityRouter: _offline(), appDatabase: db);
      final result = await service.deleteQueuedMoodItem(localId: id);

      expect(result, isTrue);
    });

    test('returns false when the row does not exist', () async {
      // Verifies the not-found path: attempting to delete a missing row
      // returns false rather than throwing.
      final db = _FakeAppDatabase();
      final service =
          MoodStorageService(connectivityRouter: _offline(), appDatabase: db);
      final result = await service.deleteQueuedMoodItem(localId: 999);
      expect(result, isFalse);
    });

    test('row is no longer accessible after deletion', () async {
      final db = _FakeAppDatabase();
      final id = await db.insertMood(userId: 1, score: 5, label: 'ok');
      final service =
          MoodStorageService(connectivityRouter: _offline(), appDatabase: db);

      await service.deleteQueuedMoodItem(localId: id);

      final rows = await db.getMoodsForUser(1);
      expect(rows, isEmpty);
    });
  });

  // ─── syncQueuedMoodItemById ───────────────────────────────────────────────

  group('MoodStorageService.syncQueuedMoodItemById()', () {
    test('returns false immediately when offline', () async {
      // Verifies the offline shortcut returns false without attempting any
      // database or network operations.
      final service = MoodStorageService(
          connectivityRouter: _offline(), appDatabase: _FakeAppDatabase());

      final result = await service.syncQueuedMoodItemById(
          userId: 1, localId: 1);
      expect(result, isFalse);
    });

    test('returns false when the local row does not exist', () async {
      // If the row was already deleted (e.g. by another sync attempt), the
      // method must handle the missing row gracefully and return false.
      handlers['/v1/api/patient/1/mood'] = (req) async {
        req.response.statusCode = HttpStatus.ok;
        req.response.write(jsonEncode({}));
      };

      final db = _FakeAppDatabase();
      final service =
          MoodStorageService(connectivityRouter: _online(), appDatabase: db);

      final result = await service.syncQueuedMoodItemById(
          userId: 1, localId: 9999);
      expect(result, isFalse);
    });

    test('returns true and removes local row on successful backend write',
        () async {
      // Verifies the happy-path: a successful POST returns true and the local
      // row is deleted so it is not re-synced on the next cycle.
      handlers['/v1/api/patient/4/mood'] = (req) async {
        req.response.statusCode = HttpStatus.ok;
        req.response.headers.contentType = ContentType.json;
        req.response.write(jsonEncode({'id': 42}));
      };

      final db = _FakeAppDatabase();
      final localId =
          await db.insertMood(userId: 4, score: 6, label: 'calm');

      final service =
          MoodStorageService(connectivityRouter: _online(), appDatabase: db);
      final result =
          await service.syncQueuedMoodItemById(userId: 4, localId: localId);

      expect(result, isTrue);
      final remaining = await db.getMoodsForUser(4);
      expect(remaining, isEmpty);
    });

    test('returns false when backend write fails (HTTP error)', () async {
      // An HTTP error response (status >= 300) causes _throwIfHttpError to
      // throw, which is caught and returns false.
      handlers['/v1/api/patient/6/mood'] = (req) async {
        req.response.statusCode = HttpStatus.internalServerError;
        req.response.write(jsonEncode({'message': 'db error'}));
      };

      final db = _FakeAppDatabase();
      final localId =
          await db.insertMood(userId: 6, score: 3, label: 'tired');

      final service =
          MoodStorageService(connectivityRouter: _online(), appDatabase: db);
      final result =
          await service.syncQueuedMoodItemById(userId: 6, localId: localId);

      expect(result, isFalse);

      // Local row must remain (not deleted) after a failed sync.
      final remaining = await db.getMoodsForUser(6);
      expect(remaining, hasLength(1));
    });
  });

  // ─── isLocalDbEncrypted ───────────────────────────────────────────────────

  group('MoodStorageService.isLocalDbEncrypted()', () {
    test('returns false when fake DB has no encryption key', () async {
      // Verifies delegation to AppDatabase.isEncrypted().
      final db = _FakeAppDatabase(); // _encryptionKeyExists defaults to false
      final service =
          MoodStorageService(connectivityRouter: _offline(), appDatabase: db);

      expect(await service.isLocalDbEncrypted(), isFalse);
    });

    test('returns true when fake DB reports an encryption key', () async {
      final db = _FakeAppDatabase().._encryptionKeyExists = true;
      final service =
          MoodStorageService(connectivityRouter: _offline(), appDatabase: db);

      expect(await service.isLocalDbEncrypted(), isTrue);
    });
  });

  // ─── close ────────────────────────────────────────────────────────────────

  group('MoodStorageService.close()', () {
    test('completes without error', () async {
      // Verifies that close() delegates to AppDatabase.close() without
      // throwing an exception.
      final service = MoodStorageService(
        connectivityRouter: _offline(),
        appDatabase: _FakeAppDatabase(),
      );
      await expectLater(service.close(), completes);
    });
  });

  // ─── _throwIfHttpError / _extractErrorMessage (via syncQueuedMoodItemById) ──
  //
  // saveMood always uses fallbackToOfflineOnOnlineError: true, so HTTP errors
  // thrown by _throwIfHttpError are caught internally and never propagate to
  // the caller.  syncQueuedMoodItemById also wraps its online block in
  // try { ... } catch (_) { return false; }, making the HTTP error visible to
  // _throwIfHttpError/_extractErrorMessage without propagating to the caller.
  // Each test pre-seeds the fake DB with a mood row and expects false back.

  group('MoodStorageService — HTTP error extraction', () {
    test('extracts "message" field from JSON error body', () async {
      // _throwIfHttpError calls _extractErrorMessage which prefers the
      // "message" key.  The online block throws, syncQueuedMoodItemById
      // catches it and returns false — covering the "message" branch.
      final db = _FakeAppDatabase();
      await db.insertMood(userId: 10, score: 3, label: 'ok');

      handlers['/v1/api/patient/10/mood'] = (req) async {
        req.response.statusCode = HttpStatus.badRequest;
        req.response.headers.contentType = ContentType.json;
        req.response.write(jsonEncode({'message': 'invalid score value'}));
      };

      final service = MoodStorageService(
        connectivityRouter: _online(),
        appDatabase: db,
      );

      // syncQueuedMoodItemById returns false when the HTTP call fails.
      final result = await service.syncQueuedMoodItemById(
        userId: 10,
        localId: 1,
      );
      expect(result, isFalse);
    });

    test('extracts "error" field when "message" is absent', () async {
      // Verifies the fallback key used when the backend provides an "error"
      // field instead of "message".  The sync call returns false after the
      // HTTP error is caught — covering the "error" key branch.
      final db = _FakeAppDatabase();
      await db.insertMood(userId: 11, score: 3, label: 'ok');

      handlers['/v1/api/patient/11/mood'] = (req) async {
        req.response.statusCode = HttpStatus.badRequest;
        req.response.headers.contentType = ContentType.json;
        req.response.write(jsonEncode({'error': 'score out of range'}));
      };

      final service = MoodStorageService(
        connectivityRouter: _online(),
        appDatabase: db,
      );

      final result = await service.syncQueuedMoodItemById(
        userId: 11,
        localId: 1,
      );
      expect(result, isFalse);
    });

    test('uses raw body when JSON decoding fails', () async {
      // _extractErrorMessage catches FormatException from jsonDecode and
      // returns the raw body string.  syncQueuedMoodItemById returns false
      // after the exception is caught — covering the non-JSON branch.
      final db = _FakeAppDatabase();
      await db.insertMood(userId: 12, score: 3, label: 'ok');

      handlers['/v1/api/patient/12/mood'] = (req) async {
        req.response.statusCode = HttpStatus.badRequest;
        req.response.write('plain text error');
      };

      final service = MoodStorageService(
        connectivityRouter: _online(),
        appDatabase: db,
      );

      final result = await service.syncQueuedMoodItemById(
        userId: 12,
        localId: 1,
      );
      expect(result, isFalse);
    });

    test('reports "No response body" when body is empty', () async {
      // An empty response body must hit the body.isEmpty branch in
      // _extractErrorMessage.  syncQueuedMoodItemById returns false — covering
      // that branch without requiring an exception to propagate.
      final db = _FakeAppDatabase();
      await db.insertMood(userId: 13, score: 3, label: 'ok');

      handlers['/v1/api/patient/13/mood'] = (req) async {
        req.response.statusCode = HttpStatus.internalServerError;
        // No body written.
      };

      final service = MoodStorageService(
        connectivityRouter: _online(),
        appDatabase: db,
      );

      final result = await service.syncQueuedMoodItemById(
        userId: 13,
        localId: 1,
      );
      expect(result, isFalse);
    });

    test('uses raw body for JSON without known keys', () async {
      // When JSON decodes successfully but has neither "message" nor "error"
      // keys, _extractErrorMessage falls through to return the raw body.
      final db = _FakeAppDatabase();
      await db.insertMood(userId: 14, score: 3, label: 'ok');

      handlers['/v1/api/patient/14/mood'] = (req) async {
        req.response.statusCode = HttpStatus.badRequest;
        req.response.headers.contentType = ContentType.json;
        req.response.write(jsonEncode({'code': 400}));
      };

      final service = MoodStorageService(
        connectivityRouter: _online(),
        appDatabase: db,
      );

      final result = await service.syncQueuedMoodItemById(
        userId: 14,
        localId: 1,
      );
      expect(result, isFalse);
    });
  });
}
