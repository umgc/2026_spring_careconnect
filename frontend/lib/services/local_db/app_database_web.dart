import 'offline_sync_row.dart';

class AppDatabase {
  Future<void> ensureOfflineSyncTable() async {}

  Future<String> upsertOfflineSyncOperation({
    required String id,
    required String method,
    required String url,
    required String headersJson,
    required String? bodyJson,
    required String createdAtIso,
    required String fingerprint,
  }) async {
    return id;
  }

  Future<int> getPendingOfflineSyncCount() async {
    return 0;
  }

  Future<List<OfflineSyncDbRow>> getPendingOfflineSyncQueue({
    int limit = 200,
  }) async {
    return <OfflineSyncDbRow>[];
  }

  Future<OfflineSyncDbRow?> getOfflineSyncById(String id) async {
    return null;
  }

  Future<void> deleteOfflineSyncById(String id) async {}

  Future<void> markOfflineSyncAsSyncing(String id) async {}

  Future<void> markOfflineSyncAsFailed({
    required String id,
    required String errorMessage,
  }) async {}

  Future<void> closeDb() async {}
}
