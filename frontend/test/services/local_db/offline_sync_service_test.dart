import 'dart:async';
import 'dart:convert';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

import 'package:care_connect_app/services/local_db/app_database_stub.dart'
    if (dart.library.io) 'package:care_connect_app/services/local_db/app_database.dart';
import 'package:care_connect_app/services/local_db/offline_sync_service.dart';

const MethodChannel pathProviderChannel =
    MethodChannel('plugins.flutter.io/path_provider');

const MethodChannel secureStorageChannel =
    MethodChannel('plugins.it_nomads.com/flutter_secure_storage');

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;

  Future<void> clearOfflineQueue(AppDatabase db) async {
    await db.ensureOfflineSyncTable();
    final existingRows = await db.getPendingOfflineSyncQueue(limit: 1000);
    for (final row in existingRows) {
      await db.deleteOfflineSyncById(row.id);
    }
  }

  setUpAll(() {
    messenger.setMockMethodCallHandler(
      pathProviderChannel,
      (MethodCall methodCall) async {
        if (methodCall.method == 'getApplicationDocumentsDirectory') {
          return '/tmp';
        }
        return null;
      },
    );

    messenger.setMockMethodCallHandler(
      secureStorageChannel,
      (MethodCall methodCall) async {
        switch (methodCall.method) {
          case 'read':
            return null;
          case 'write':
            return null;
          case 'delete':
            return null;
          case 'deleteAll':
            return null;
          case 'containsKey':
            return false;
          case 'readAll':
            return <String, String>{};
          default:
            return null;
        }
      },
    );
  });

  tearDownAll(() {
    messenger.setMockMethodCallHandler(pathProviderChannel, null);
    messenger.setMockMethodCallHandler(secureStorageChannel, null);
  });

  group('OfflineSyncService', () {
    late AppDatabase db;
    late OfflineSyncService service;

    setUp(() async {
      db = AppDatabase();
      await clearOfflineQueue(db);

      service = OfflineSyncService(
        appDatabase: db,
        replayClient: MockClient((request) async {
          return http.Response('', 200);
        }),
      );
    });

    tearDown(() async {
      await service.close();
    });

    test('isQueueableMethod returns true for write methods', () {
      expect(service.isQueueableMethod('POST'), isTrue);
      expect(service.isQueueableMethod('PUT'), isTrue);
      expect(service.isQueueableMethod('PATCH'), isTrue);
      expect(service.isQueueableMethod('DELETE'), isTrue);
    });

    test('isQueueableMethod returns false for non-write methods', () {
      expect(service.isQueueableMethod('GET'), isFalse);
      expect(service.isQueueableMethod('HEAD'), isFalse);
      expect(service.isQueueableMethod('OPTIONS'), isFalse);
    });

    test('initialize creates offline sync table without throwing', () async {
      await service.initialize();
      expect(await service.getPendingCount(), 0);
    });

    test('enqueueRequest adds a request to queue', () async {
      final queuedId = await service.enqueueRequest(
        method: 'POST',
        uri: Uri.parse('https://example.com/v1/api/mood'),
        headers: {'Content-Type': 'application/json'},
        body: '{"score":5}',
      );

      expect(queuedId, isNotEmpty);
      expect(await service.getPendingCount(), 1);

      final queue = await service.getPendingQueue();
      expect(queue.length, 1);
      expect(queue.first.method, 'POST');
      expect(queue.first.url, 'https://example.com/v1/api/mood');
    });

    test('enqueueRequest deduplicates identical requests by fingerprint',
        () async {
      final id1 = await service.enqueueRequest(
        method: 'POST',
        uri: Uri.parse('https://example.com/v1/api/mood'),
        headers: {'Content-Type': 'application/json'},
        body: '{"score":5}',
      );

      final id2 = await service.enqueueRequest(
        method: 'POST',
        uri: Uri.parse('https://example.com/v1/api/mood'),
        headers: {'Content-Type': 'application/json'},
        body: '{"score":5}',
      );

      expect(id2, id1);
      expect(await service.getPendingCount(), 1);
    });

    test('getPendingQueue respects limit', () async {
      for (var i = 0; i < 3; i++) {
        await service.enqueueRequest(
          method: 'POST',
          uri: Uri.parse('https://example.com/v1/api/tasks/$i'),
          headers: {'Content-Type': 'application/json'},
          body: '{"title":"Task $i"}',
        );
      }

      final queue = await service.getPendingQueue(limit: 2);
      expect(queue.length, 2);
    });

    test('deleteQueuedRequestById returns true when request exists', () async {
      final id = await service.enqueueRequest(
        method: 'POST',
        uri: Uri.parse('https://example.com/v1/api/tasks'),
        headers: {'Content-Type': 'application/json'},
        body: '{"title":"Task"}',
      );

      final deleted = await service.deleteQueuedRequestById(id);

      expect(deleted, isTrue);
      expect(await service.getPendingCount(), 0);
    });

    test('deleteQueuedRequestById returns false when request does not exist',
        () async {
      final deleted = await service.deleteQueuedRequestById('missing-id');
      expect(deleted, isFalse);
    });

    test('syncQueuedRequestById returns true for missing row', () async {
      final result = await service.syncQueuedRequestById('missing-id');
      expect(result, isTrue);
    });

    test('syncPendingQueue returns zero summary when queue is empty', () async {
      final summary = await service.syncPendingQueue();

      expect(summary.attempted, 0);
      expect(summary.succeeded, 0);
      expect(summary.failed, 0);
    });

    test('syncQueuedRequestById deletes row on HTTP 200 success', () async {
      await service.close();
      service = OfflineSyncService(
        appDatabase: db,
        replayClient: MockClient((request) async {
          return http.Response('', 200);
        }),
      );

      final id = await service.enqueueRequest(
        method: 'POST',
        uri: Uri.parse('https://example.com/v1/api/tasks'),
        headers: {'Content-Type': 'application/json'},
        body: '{"title":"Task"}',
      );

      final result = await service.syncQueuedRequestById(id);

      expect(result, isTrue);
      expect(await service.getPendingCount(), 0);
    });

    test('syncQueuedRequestById treats HTTP 409 as success', () async {
      await service.close();
      service = OfflineSyncService(
        appDatabase: db,
        replayClient: MockClient((request) async {
          return http.Response('conflict', 409);
        }),
      );

      final id = await service.enqueueRequest(
        method: 'POST',
        uri: Uri.parse('https://example.com/v1/api/tasks'),
        headers: {'Content-Type': 'application/json'},
        body: '{"title":"Task"}',
      );

      final result = await service.syncQueuedRequestById(id);

      expect(result, isTrue);
      expect(await service.getPendingCount(), 0);
    });

    test('syncQueuedRequestById marks row failed on HTTP error', () async {
      await service.close();
      service = OfflineSyncService(
        appDatabase: db,
        replayClient: MockClient((request) async {
          return http.Response('server error', 500);
        }),
      );

      final id = await service.enqueueRequest(
        method: 'POST',
        uri: Uri.parse('https://example.com/v1/api/tasks'),
        headers: {'Content-Type': 'application/json'},
        body: '{"title":"Task"}',
      );

      final result = await service.syncQueuedRequestById(id);

      expect(result, isFalse);

      final row = await db.getOfflineSyncById(id);
      expect(row, isNotNull);
      expect(row!.status, 'failed');
      expect(row.retryCount, 1);
      expect(row.lastError, contains('HTTP 500'));
    });

    test('syncQueuedRequestById marks row failed for invalid URL', () async {
      await db.upsertOfflineSyncOperation(
        id: 'bad-url-id',
        method: 'POST',
        url: '://bad-url',
        headersJson: jsonEncode({'Content-Type': 'application/json'}),
        bodyJson: '{"title":"Task"}',
        createdAtIso: DateTime.now().toUtc().toIso8601String(),
        fingerprint: 'bad-fingerprint',
      );

      final result = await service.syncQueuedRequestById('bad-url-id');

      expect(result, isFalse);

      final row = await db.getOfflineSyncById('bad-url-id');
      expect(row, isNotNull);
      expect(row!.status, 'failed');
      expect(row.lastError, 'Invalid queued URL');
    });

    test('syncPendingQueue returns correct success and failure counts',
        () async {
      var sendCount = 0;

      await service.close();
      service = OfflineSyncService(
        appDatabase: db,
        replayClient: MockClient((request) async {
          sendCount++;
          if (sendCount == 1) {
            return http.Response('', 200);
          }
          return http.Response('bad', 500);
        }),
      );

      await service.enqueueRequest(
        method: 'POST',
        uri: Uri.parse('https://example.com/v1/api/tasks/1'),
        headers: {'Content-Type': 'application/json'},
        body: '{"title":"Task 1"}',
      );

      await service.enqueueRequest(
        method: 'POST',
        uri: Uri.parse('https://example.com/v1/api/tasks/2'),
        headers: {'Content-Type': 'application/json'},
        body: '{"title":"Task 2"}',
      );

      final summary = await service.syncPendingQueue();

      expect(summary.attempted, 2);
      expect(summary.succeeded, 1);
      expect(summary.failed, 1);
    });

    test('shouldQueueForError returns true for TimeoutException', () {
      expect(service.shouldQueueForError(TimeoutException('timeout')), isTrue);
    });

    test('shouldQueueForError returns true for ClientException', () {
      expect(
        service.shouldQueueForError(
          http.ClientException('connection refused'),
        ),
        isTrue,
      );
    });

    test('shouldQueueForError returns true for known network text', () {
      expect(
        service.shouldQueueForError(
          Exception('SocketException: failed host lookup'),
        ),
        isTrue,
      );
      expect(
        service.shouldQueueForError(Exception('network is unreachable')),
        isTrue,
      );
    });

    test('shouldQueueForError returns false for non-network error', () {
      expect(service.shouldQueueForError(Exception('format error')), isFalse);
    });

    test('buildQueuedStreamedResponse returns queued JSON response', () async {
      final request =
          http.Request('POST', Uri.parse('https://example.com/test'));

      final streamed = service.buildQueuedStreamedResponse(
        request,
        queuedId: 'queued-123',
      );

      final response = await http.Response.fromStream(streamed);
      final body = jsonDecode(response.body) as Map<String, dynamic>;

      expect(response.statusCode, 200);
      expect(body['queued'], isTrue);
      expect(body['offline'], isTrue);
      expect(body['requestId'], 'queued-123');
      expect(response.headers['x-offline-queued'], 'true');
      expect(response.headers['x-offline-request-id'], 'queued-123');
    });
    test('enqueueRequest treats empty body as null and still queues', () async {
      final queuedId = await service.enqueueRequest(
        method: 'POST',
        uri: Uri.parse('https://example.com/v1/api/tasks'),
        headers: {'Content-Type': 'application/json'},
        body: '',
      );

      expect(queuedId, isNotEmpty);

      final row = await db.getOfflineSyncById(queuedId);
      expect(row, isNotNull);
      expect(row!.bodyJson, isNull);
      expect(await service.getPendingCount(), 1);
    });

    test('enqueueRequest ignores replay header when deduplicating fingerprint',
        () async {
      final id1 = await service.enqueueRequest(
        method: 'POST',
        uri: Uri.parse('https://example.com/v1/api/tasks'),
        headers: {
          'Content-Type': 'application/json',
        },
        body: '{"title":"Task"}',
      );

      final id2 = await service.enqueueRequest(
        method: 'POST',
        uri: Uri.parse('https://example.com/v1/api/tasks'),
        headers: {
          'Content-Type': 'application/json',
          OfflineSyncService.replayHeader: 'true',
        },
        body: '{"title":"Task"}',
      );

      expect(id2, id1);
      expect(await service.getPendingCount(), 1);
    });

    test('syncQueuedRequestById allows syncing status rows', () async {
      await db.upsertOfflineSyncOperation(
        id: 'done-row',
        method: 'POST',
        url: 'https://example.com/v1/api/tasks',
        headersJson: jsonEncode({'Content-Type': 'application/json'}),
        bodyJson: '{"title":"Task"}',
        createdAtIso: DateTime.now().toUtc().toIso8601String(),
        fingerprint: 'done-row-fingerprint',
      );

      await db.markOfflineSyncAsSyncing('done-row');
      await db.deleteOfflineSyncById('done-row');

      await db.upsertOfflineSyncOperation(
        id: 'custom-status-row',
        method: 'POST',
        url: 'https://example.com/v1/api/tasks',
        headersJson: jsonEncode({'Content-Type': 'application/json'}),
        bodyJson: '{"title":"Task"}',
        createdAtIso: DateTime.now().toUtc().toIso8601String(),
        fingerprint: 'custom-status-row-fingerprint',
      );

      await db.markOfflineSyncAsSyncing('custom-status-row');

      final row = await db.getOfflineSyncById('custom-status-row');
      expect(row, isNotNull);
      expect(row!.status, 'syncing');

      final result = await service.syncQueuedRequestById('custom-status-row');
      expect(result, isTrue);
    });

    test('syncQueuedRequestById marks row failed when replay client throws',
        () async {
      await service.close();
      service = OfflineSyncService(
        appDatabase: db,
        replayClient: MockClient((request) async {
          throw Exception('unexpected replay failure');
        }),
      );

      final id = await service.enqueueRequest(
        method: 'POST',
        uri: Uri.parse('https://example.com/v1/api/tasks'),
        headers: {'Content-Type': 'application/json'},
        body: '{"title":"Task"}',
      );

      final result = await service.syncQueuedRequestById(id);

      expect(result, isFalse);

      final row = await db.getOfflineSyncById(id);
      expect(row, isNotNull);
      expect(row!.status, 'failed');
      expect(row.retryCount, 1);
      expect(row.lastError, contains('unexpected replay failure'));
    });

    test('getPendingQueue builds mood check-in safe display', () async {
      await service.enqueueRequest(
        method: 'POST',
        uri: Uri.parse('https://example.com/v1/api/mood-pain-log'),
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode({
          'moodValue': 4,
          'timestamp': '2026-03-15T12:30:00Z',
        }),
      );

      final queue = await service.getPendingQueue();

      expect(queue.length, 1);
      expect(queue.first.displayTitle, 'Mood Check-In');
      expect(
        queue.first.displayDetails.any((d) => d.contains('Mood rating: 4')),
        isTrue,
      );
      expect(
        queue.first.displayDetails.any((d) => d.contains('Date:')),
        isTrue,
      );
    });

    test('getPendingQueue builds mood entry safe display', () async {
      await service.enqueueRequest(
        method: 'POST',
        uri: Uri.parse('https://example.com/v1/api/mood'),
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode({
          'score': 5,
          'label': 'Happy',
        }),
      );

      final queue = await service.getPendingQueue();

      expect(queue.length, 1);
      expect(queue.first.displayTitle, 'Mood Entry');
      expect(
        queue.first.displayDetails.any(
          (d) => d.contains('Mood rating: 5 (Happy)'),
        ),
        isTrue,
      );
    });

    test('getPendingQueue builds task safe display from flat payload',
        () async {
      await service.enqueueRequest(
        method: 'POST',
        uri: Uri.parse('https://example.com/v1/api/tasks'),
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode({
          'title': 'Take medication',
          'note': 'After breakfast',
          'taskDate': '2026-03-16',
          'time': '08:00',
        }),
      );

      final queue = await service.getPendingQueue();

      expect(queue.length, 1);
      expect(queue.first.displayTitle, 'Task');
      expect(queue.first.displayDetails, contains('Title: Take medication'));
      expect(queue.first.displayDetails, contains('Note: After breakfast'));
      expect(queue.first.displayDetails, contains('Task date: 2026-03-16'));
      expect(queue.first.displayDetails, contains('Task time: 08:00'));
    });

    test('getPendingQueue builds task safe display from nested task payload',
        () async {
      await service.enqueueRequest(
        method: 'POST',
        uri: Uri.parse('https://example.com/v1/api/tasks'),
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode({
          'task': {
            'title': 'Walk outside',
            'note': '15 minutes',
            'taskDate': '2026-03-17',
            'time': '09:30',
          }
        }),
      );

      final queue = await service.getPendingQueue();

      expect(queue.length, 1);
      expect(queue.first.displayTitle, 'Task');
      expect(queue.first.displayDetails, contains('Title: Walk outside'));
      expect(queue.first.displayDetails, contains('Note: 15 minutes'));
      expect(queue.first.displayDetails, contains('Task date: 2026-03-17'));
      expect(queue.first.displayDetails, contains('Task time: 09:30'));
    });

    test('getPendingQueue generic payload excludes sensitive fields', () async {
      await service.enqueueRequest(
        method: 'POST',
        uri: Uri.parse('https://example.com/v1/api/other'),
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode({
          'title': 'Safe Title',
          'description': 'Visible description',
          'password': 'secret123',
          'token': 'abcd',
          'authorization': 'Bearer xyz',
        }),
      );

      final queue = await service.getPendingQueue();

      expect(queue.length, 1);
      expect(queue.first.displayTitle, 'Queued Update');

      final joined = queue.first.displayDetails.join(' | ');
      expect(joined.contains('Safe Title'), isTrue);
      expect(joined.contains('Visible description'), isTrue);
      expect(joined.contains('secret123'), isFalse);
      expect(joined.contains('abcd'), isFalse);
      expect(joined.contains('Bearer xyz'), isFalse);
    });

    test('getPendingQueue falls back to waiting to sync for empty payload',
        () async {
      await service.enqueueRequest(
        method: 'POST',
        uri: Uri.parse('https://example.com/v1/api/other'),
        headers: {'Content-Type': 'application/json'},
        body: null,
      );

      final queue = await service.getPendingQueue();

      expect(queue.length, 1);
      expect(queue.first.displayTitle, 'Queued Update');
      expect(queue.first.displayDetails, contains('Waiting to sync'));
    });

    test('syncQueuedRequestById adds replay header to outbound request',
        () async {
      http.BaseRequest? capturedRequest;

      await service.close();
      service = OfflineSyncService(
        appDatabase: db,
        replayClient: MockClient((request) async {
          capturedRequest = request;
          return http.Response('', 200);
        }),
      );

      final id = await service.enqueueRequest(
        method: 'POST',
        uri: Uri.parse('https://example.com/v1/api/tasks'),
        headers: {'Content-Type': 'application/json'},
        body: '{"title":"Task"}',
      );

      final result = await service.syncQueuedRequestById(id);

      expect(result, isTrue);
      expect(capturedRequest, isNotNull);
      expect(
        capturedRequest!.headers[OfflineSyncService.replayHeader],
        'true',
      );
    });
  });

  group('OfflineQueueHttpClient', () {
    test('passes through non-queueable GET requests', () async {
      final inner = MockClient((request) async {
        return http.Response('ok', 200);
      });

      final db = AppDatabase();
      await clearOfflineQueue(db);

      final service = OfflineSyncService(
        appDatabase: db,
        replayClient: MockClient((request) async => http.Response('', 200)),
      );

      final client = OfflineQueueHttpClient(
        inner: inner,
        offlineSyncService: service,
      );

      final response =
          await client.get(Uri.parse('https://example.com/health'));

      expect(response.statusCode, 200);
      expect(response.body, 'ok');

      await service.close();
    });

    test('queues POST request on network failure when allowed', () async {
      final db = AppDatabase();
      await clearOfflineQueue(db);

      final service = OfflineSyncService(
        appDatabase: db,
        replayClient: MockClient((request) async => http.Response('', 200)),
      );

      final inner = MockClient((request) async {
        throw http.ClientException('connection refused');
      });

      final client = OfflineQueueHttpClient(
        inner: inner,
        offlineSyncService: service,
        canQueueWrites: () => true,
      );

      final response = await client.post(
        Uri.parse('https://example.com/v1/api/tasks'),
        headers: {'Content-Type': 'application/json'},
        body: '{"title":"Queued Task"}',
      );

      expect(response.statusCode, 200);

      final decoded = jsonDecode(response.body) as Map<String, dynamic>;
      expect(decoded['queued'], isTrue);
      expect(await service.getPendingCount(), 1);

      await service.close();
    });

    test('does not queue POST request when queueing disabled', () async {
      final db = AppDatabase();
      await clearOfflineQueue(db);

      final service = OfflineSyncService(
        appDatabase: db,
        replayClient: MockClient((request) async => http.Response('', 200)),
      );

      final inner = MockClient((request) async {
        throw http.ClientException('connection refused');
      });

      final client = OfflineQueueHttpClient(
        inner: inner,
        offlineSyncService: service,
        canQueueWrites: () => false,
      );

      expect(
        () => client.post(
          Uri.parse('https://example.com/v1/api/tasks'),
          headers: {'Content-Type': 'application/json'},
          body: '{"title":"Task"}',
        ),
        throwsA(isA<http.ClientException>()),
      );

      expect(await service.getPendingCount(), 0);

      await service.close();
    });

    test('does not queue auth endpoint requests', () async {
      final db = AppDatabase();
      await clearOfflineQueue(db);

      final service = OfflineSyncService(
        appDatabase: db,
        replayClient: MockClient((request) async => http.Response('', 200)),
      );

      final inner = MockClient((request) async {
        throw http.ClientException('connection refused');
      });

      final client = OfflineQueueHttpClient(
        inner: inner,
        offlineSyncService: service,
      );

      expect(
        () => client.post(
          Uri.parse('https://example.com/v1/api/auth/login'),
          headers: {'Content-Type': 'application/json'},
          body: '{"username":"a","password":"b"}',
        ),
        throwsA(isA<http.ClientException>()),
      );

      expect(await service.getPendingCount(), 0);

      await service.close();
    });

    test('does not queue multipart requests', () async {
      final db = AppDatabase();
      await clearOfflineQueue(db);

      final service = OfflineSyncService(
        appDatabase: db,
        replayClient: MockClient((request) async => http.Response('', 200)),
      );

      final inner = MockClient((request) async {
        throw http.ClientException('multipart failed');
      });

      final client = OfflineQueueHttpClient(
        inner: inner,
        offlineSyncService: service,
      );

      final request = http.MultipartRequest(
        'POST',
        Uri.parse('https://example.com/v1/api/upload'),
      );

      expect(
        () => client.send(request),
        throwsA(isA<http.ClientException>()),
      );

      expect(await service.getPendingCount(), 0);
      await service.close();
    });

    test('does not queue replay-flagged requests', () async {
      final db = AppDatabase();
      await clearOfflineQueue(db);

      final service = OfflineSyncService(
        appDatabase: db,
        replayClient: MockClient((request) async => http.Response('', 200)),
      );

      final inner = MockClient((request) async {
        throw http.ClientException('replay request failed');
      });

      final client = OfflineQueueHttpClient(
        inner: inner,
        offlineSyncService: service,
      );

      expect(
        () => client.post(
          Uri.parse('https://example.com/v1/api/tasks'),
          headers: {
            'Content-Type': 'application/json',
            OfflineSyncService.replayHeader: 'true',
          },
          body: '{"title":"Task"}',
        ),
        throwsA(isA<http.ClientException>()),
      );

      expect(await service.getPendingCount(), 0);
      await service.close();
    });
  });
}
