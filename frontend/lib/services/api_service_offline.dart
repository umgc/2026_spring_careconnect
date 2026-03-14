import 'package:http/http.dart' as http;

import 'local_db/offline_sync_service.dart';

export 'local_db/offline_sync_service.dart'
    show OfflineSyncQueueItem, OfflineSyncRunSummary;

/// Centralizes offline queue wiring used by [ApiService].
class ApiServiceOffline {
  ApiServiceOffline._();

  static final OfflineSyncService _offlineSyncService =
      OfflineSyncService.instance();
  static bool Function()? _canQueueOfflineWrites;

  static final http.Client httpClient = OfflineQueueHttpClient(
    inner: http.Client(),
    offlineSyncService: _offlineSyncService,
    canQueueWrites: () => _canQueueOfflineWrites?.call() ?? true,
  );

  static void configure({
    required bool Function() canQueueOfflineWrites,
  }) {
    _canQueueOfflineWrites = canQueueOfflineWrites;
  }

  static Future<void> initialize() async {
    await _offlineSyncService.initialize();
  }

  static Future<List<OfflineSyncQueueItem>> getPendingQueue({
    int limit = 200,
  }) async {
    return _offlineSyncService.getPendingQueue(limit: limit);
  }

  static Future<int> getPendingCount() async {
    return _offlineSyncService.getPendingCount();
  }

  static Future<bool> syncQueuedRequestById(String id) async {
    return _offlineSyncService.syncQueuedRequestById(id);
  }

  static Future<bool> deleteQueuedRequestById(String id) async {
    return _offlineSyncService.deleteQueuedRequestById(id);
  }

  static Future<OfflineSyncRunSummary> syncPendingQueue({
    int limit = 200,
  }) async {
    return _offlineSyncService.syncPendingQueue(limit: limit);
  }

  static bool isQueuedOfflineResponse(http.Response response) {
    return response.headers['x-offline-queued'] == 'true';
  }
}
